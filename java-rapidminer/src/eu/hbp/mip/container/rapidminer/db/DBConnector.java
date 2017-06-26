package eu.hbp.mip.container.rapidminer.db;

import java.io.IOException;
import java.sql.* ;

import eu.hbp.mip.container.rapidminer.RapidMinerExperiment;

/**
 *
 * @author Arnaud Jutzeler
 *
 */
public class DBConnector {

    private static final String IN_URL = System.getenv("IN_JDBC_URL");
    private static final String IN_USER = System.getenv("IN_JDBC_USER");
    private static final String IN_PASS = System.getenv("IN_JDBC_PASSWORD");

    private static final String OUT_URL = System.getenv("OUT_JDBC_URL");
    private static final String OUT_USER = System.getenv("OUT_JDBC_USER");
    private static final String OUT_PASS = System.getenv("OUT_JDBC_PASSWORD");
    private static final String OUT_TABLE = System.getenv().getOrDefault("RESULT_TABLE", "job_result");

    private String query = null;
    private Direction direction = null;

    private transient Connection conn = null;
    private transient Statement stmt = null;
    private transient ResultSet rs = null;

    public enum Direction {
        DATA_IN  (IN_URL, IN_USER, IN_PASS),
        DATA_OUT (OUT_URL, OUT_USER, OUT_PASS);

        private final String url;
        private final String user;
        private final String pass;
        Direction(String url, String user, String pass) {
            this.url = url;
            this.user = user;
            this.pass = pass;
        }
        private Connection getConnection() throws SQLException {
            return DriverManager.getConnection(url,user, pass);
        }
    }

    public DBConnector(String query, Direction direction) {
        this.query = query;
        this.direction = direction;
    }

    public ResultSet connect() throws DBException {
        try {

            //conn = DriverManager.getConnection(IN_URL, IN_USER, IN_PASS);
            conn = direction.getConnection();
            conn.setAutoCommit(false);
            // TODO The seed must be passed as a query parameters and generated above
            conn.prepareStatement("SELECT setseed(0.67)").execute();
            stmt = conn.createStatement();
            rs = stmt.executeQuery(query);
            conn.commit();
            conn.setAutoCommit(true);
            return rs;

        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException e2) {}
            }
            if (stmt != null) {
                try {
                    stmt.close();
                } catch (SQLException e2) {}
            }
            if (rs != null) {
                try {
                    rs.close();
                } catch (SQLException e2) {}
            }
            throw new DBException(e);
        }
    }

    public void disconnect() {

        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException e) {}
            conn = null;
        }
        if (stmt != null) {
            try {
                stmt.close();
            } catch (SQLException e) {}
            stmt = null;
        }
        if (rs != null) {
            try {
                rs.close();
            } catch (SQLException e) {}
            rs = null;
        }
    }

    public static void saveResults(RapidMinerExperiment experiment)
            throws DBException {

        Connection conn = null;
        Statement stmt = null;
        try {


            conn = DriverManager.getConnection(OUT_URL, OUT_USER, OUT_PASS);

            String jobId = System.getProperty("JOB_ID", System.getenv("JOB_ID"));
            String node = System.getenv("NODE");
            //String outFormat = System.getenv("OUT_FORMAT");
            String function = System.getenv().getOrDefault("FUNCTION", "JAVA");

            String shape = "pfa_json";
            // Escape single quote with another single quote (SQL)
            String pfa = experiment.toPFA().replaceAll("'", "''");

            String statement = "INSERT INTO " + OUT_TABLE + " (job_id, node, data, shape, function)" +
                    "VALUES ('" + jobId + "', '" + node + "', '" + pfa + "', '" + shape + "', '" + function + "')";

            Statement st = conn.createStatement();

            st.executeUpdate(statement);

        } catch (SQLException | IOException e) {
            throw new DBException(e);
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException e) {}
            }
            if (stmt != null) {
                try {
                    stmt.close();
                } catch (SQLException e) {}
            }
        }
    }

    public static class DBResults {

        public String node;
        public String shape;
        public String data;

        public DBResults(String node, String shape, String data) {
            this.node = node;
            this.shape = shape;
            this.data = data;
        }
    }

    public static DBResults getDBResult(String jobId) throws DBException {

        Connection conn = null;
        Statement stmt = null;
        ResultSet rs = null;
        try {

            String URL = System.getenv("OUT_JDBC_URL");
            String USER = System.getenv("OUT_JDBC_USER");
            String PASS = System.getenv("OUT_JDBC_PASSWORD");
            String TABLE = System.getenv().getOrDefault("RESULT_TABLE", "job_result");
            conn = DriverManager.getConnection(URL, USER, PASS);

            Statement st = conn.createStatement();
            rs = st.executeQuery("select node, data, shape from " + TABLE + " where job_id ='" + jobId + "'");

            DBResults results = null;
            while (rs.next()) {
                results = new DBResults(rs.getString("node"), rs.getString("shape"), rs.getString("data"));
            }

            return results;

        } catch (SQLException e) {
            e.printStackTrace();
            throw new DBException(e);
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException e) {}
            }
            if (stmt != null) {
                try {
                    stmt.close();
                } catch (SQLException e) {}
            }
            if (rs != null) {
                try {
                    rs.close();
                } catch (SQLException e) {}
            }
        }
    }
}
