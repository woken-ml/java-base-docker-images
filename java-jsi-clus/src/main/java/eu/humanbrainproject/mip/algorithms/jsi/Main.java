
package eu.humanbrainproject.mip.algorithms.jsi;

import java.util.logging.Level;
import java.util.logging.Logger;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Files;

import eu.humanbrainproject.mip.algorithms.jsi.common.ClusAlgorithm;
import eu.humanbrainproject.mip.algorithms.jsi.common.ClusHelper;
import eu.humanbrainproject.mip.algorithms.jsi.common.ClusMeta;
import eu.humanbrainproject.mip.algorithms.jsi.serializers.pfa.ClusModelPFASerializer;
import si.ijs.kt.clus.model.ClusModel;
import eu.humanbrainproject.mip.algorithms.Configuration;
import eu.humanbrainproject.mip.algorithms.ResultsFormat;
import eu.humanbrainproject.mip.algorithms.db.OutputDataConnector;


/**
 * Entry point
 *
 * @author Martin Breskvar
 */
public class Main<M extends ClusModel> {

    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());

    private final ClusModelPFASerializer<M> algorithmSerializer;
    private final ClusMeta clusMeta;


    public Main(ClusModelPFASerializer<M> algorithmSerializer, ClusMeta clusMeta) {
        this.algorithmSerializer = algorithmSerializer;
        this.clusMeta = clusMeta;        
    }


    public void run() throws Exception {

    	LOGGER.log(Level.FINEST, "Strating the CLUS library");
    	LOGGER.log(Level.FINEST, "Preparing WEKA database props");
    	
    	// weka properties
        Path targetDbProps = FileSystems.getDefault().getPath("/opt", "weka", "props", "weka", "experiment", "DatabaseUtils.props");
        if (Configuration.INSTANCE.inputJdbcUrl().startsWith("jdbc:postgresql:")) {
            Path dbProps = FileSystems.getDefault().getPath("/opt", "weka", "databases-props", "DatabaseUtils.props.postgresql");
            Files.createLink(targetDbProps, dbProps);
        }
               
        
        ClusAlgorithm<M> experiment = new ClusAlgorithm<>(algorithmSerializer, clusMeta);


        try {
            experiment.run();
        }
        catch (Exception e)
        {
        	  LOGGER.log(Level.SEVERE, "Cannot execute the algorithm", e);
        }
        finally {
            // get constructed PFA and save to database
            String pfa = experiment.toPFA();

            // for debugging
            String isDebug = System.getenv(ClusHelper.ClusConstants.CLUS_DEBUG_ENV_VARIABLE);
            if (isDebug != null && isDebug.toLowerCase().equals("yes")) {
                ClusHelper.writeDebug(pfa);
            }

            // Write results PFA in DB - it can represent also an error
            OutputDataConnector out = OutputDataConnector.fromEnv();
            out.saveResults(pfa, ResultsFormat.PFA_JSON);
            
            if (false) // if algo has a visualization - TBD
            {            	
            	String visualization = "";
            	out.saveResults(visualization,  ResultsFormat.JAVASCRIPT_VISJS);
            }
        }
    }


//    public static void main(String[] args) {
//
//        try {
//            String settingsPath = (args.length == 0) ? "settings.properties" : args[0];
//
//            // weka properties
//            Path targetDbProps = FileSystems.getDefault().getPath("/opt", "weka", "props", "weka", "experiment", "DatabaseUtils.props");
//            if (Configuration.INSTANCE.inputJdbcUrl().startsWith("jdbc:postgresql:")) {
//                Path dbProps = FileSystems.getDefault().getPath("/opt", "weka", "databases-props", "DatabaseUtils.props.postgresql");
//                Files.createLink(targetDbProps, dbProps);
//            }
//
//            Properties settings = new Properties();
//            settings.load(Main.class.getResourceAsStream(settingsPath));
//
//
//            final String modelSerializerClassName = settings.getProperty("serializer");
//            Class<?> modelSerializerClass = Class.forName(modelSerializerClassName);
//            ClusGenericSerializer<ClusModel> modelSerializer = (ClusGenericSerializer<ClusModel>) modelSerializerClass.newInstance();
//
//            ClusModelPFASerializer<ClusModel> mainSerializer = new ClusModelPFASerializer<>(modelSerializer);
//            
//            final String metaClassName = settings.getProperty("meta"); 
//            Class<?> metaClass = Class.forName(metaClassName);
//            ClusMeta meta = (ClusMeta)metaClass.newInstance();
//            
//            // read parameters from environment
//            // TODO
//            
//            // run the algorithm
//            Main main = new Main(mainSerializer, meta);
//            main.run();
//        }
//        catch (Exception e) {
//            LOGGER.log(Level.SEVERE, "Cannot execute the algorithm", e);
//            System.exit(1);
//        }
//    }

}
