package eu.humanbrainproject.mip.algorithms.rapidminer;

import java.util.*;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;

import com.rapidminer.example.Attribute;
import com.rapidminer.example.ExampleSet;
import com.rapidminer.example.table.*;
import com.rapidminer.operator.OperatorException;
import com.rapidminer.tools.Ontology;

import eu.humanbrainproject.mip.algorithms.Configuration;
import eu.humanbrainproject.mip.algorithms.db.DBConnectionDescriptor;
import eu.humanbrainproject.mip.algorithms.db.DBException;
import eu.humanbrainproject.mip.algorithms.db.InputDataConnector;
import eu.humanbrainproject.mip.algorithms.rapidminer.exceptions.RapidMinerException;


/**
 * @author Arnaud Jutzeler
 */
public class InputData {

    private final InputDataConnector connector;
    private final String[] featuresNames;
    private final String variableName;

    //protected Map<String, Integer> types;

    protected final ExampleSet data;

    /**
     * @return the input data initialised from the environment variables
     */
    public static InputData fromEnv() throws DBException, RapidMinerException {
        final Configuration conf = Configuration.INSTANCE;

        // Read first system property then env variables
        final String labelName = conf.variables()[0];
        final String[] featuresNames = conf.covariables();
        final String query = conf.inputSqlQuery();
        final double seed = conf.randomSeed();

        final InputDataConnector connector = new InputDataConnector(query, seed, DBConnectionDescriptor.inputConnectorFromEnv());

        return new InputData(featuresNames, labelName, connector);
    }

    public InputData(String[] featuresNames, String variableName, InputDataConnector connector) throws DBException, RapidMinerException {
        this.featuresNames = featuresNames;
        this.variableName = variableName;
        //this.types = new HashMap<>();

        this.connector = connector;
        this.data = createExampleSet();
    }

    /**
     * Return the relevant data structure to pass as input to RapidMiner
     *
     * @return the input data as an ExampleSet to train RapidMiner algorithms
     */
    public ExampleSet getData() {
        return data;
    }

    public String[] getFeaturesNames() {
        return featuresNames;
    }

    /**
     * @return the name of the target variable
     */
    public String getVariableName() {
        return variableName;
    }

    /**
     * @return the SQL query
     */
    public String getQuery() {
        return connector.getQuery();
    }

    /**
     * Get the data from DB
     */
    private ExampleSet createExampleSet() throws RapidMinerException, DBException {
        MemoryExampleTable table;

        try (ResultSet rs = connector.fetchInputData()) {

            // Create attribute list
            ResultSetMetaData rsmd = rs.getMetaData();
            List<Attribute> attributes = new ArrayList<>();

            for (int i = 1; i <= rsmd.getColumnCount(); i++) {
                String name = rsmd.getColumnName(i);
                String typeName = rsmd.getColumnTypeName(i);

                int type = getOntology(typeName);
                //types.put(name, type);
                attributes.add(AttributeFactory.createAttribute(name, type));
            }

            // Create table
            table = new MemoryExampleTable(attributes);

            DataRowFactory dataRowFactory = new DataRowFactory(DataRowFactory.TYPE_DOUBLE_ARRAY, '.');
            ResultSetDataRowReader reader = new ResultSetDataRowReader(dataRowFactory, attributes, rs);
            while (reader.hasNext()) {
                table.addDataRow(reader.next());
            }
        } catch (SQLException e) {
            throw new DBException(e);
        }

        connector.disconnect();

        // Create example set
        try {
            return table.createExampleSet(table.findAttribute(variableName));
        } catch (OperatorException e) {
            throw new RapidMinerException(e);
        }
    }

    /**
     * Match an Ontology to a DB type name
     *
     * @param typeName JDBC type name
     * @return
     */
    private static int getOntology(String typeName) {
        String[] real = {
                "numeric",
                "decimal",
                "tinyint",
                "smallint",
                "integer",
                "bigint",
                "real",
                "float",
                "double"
        };

        if (Arrays.asList(real).contains(typeName)) {
            return Ontology.REAL;
        } else {
            return Ontology.NOMINAL;
        }
    }

}