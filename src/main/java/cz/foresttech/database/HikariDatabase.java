package cz.foresttech.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.InputStream;
import java.io.PrintWriter;
import java.sql.*;
import java.util.ArrayList;
import java.util.Properties;

public class HikariDatabase implements ForestDatabase {

    private final String host;
    private final String port;
    private final String username;
    private final String password;
    private final String databaseName;
    private HikariDataSource hikariDataSource;

    public HikariDatabase(String host, String databaseName, String username, String password) {
        String[] splitHost = host.split(":");
        if (splitHost.length > 1) {
            this.host = splitHost[0];
            this.port = splitHost[1];
        } else {
            this.host = host;
            this.port = "3306";
        }

        this.databaseName = databaseName;
        this.username = username;
        this.password = password;
    }

    @Override
    public void setup() {
        Properties props = new Properties();
        props.setProperty("dataSource.user", this.username);
        props.setProperty("dataSource.password", this.password);
        props.setProperty("dataSource.databaseName", this.databaseName);
        props.put("dataSource.logWriter", new PrintWriter(System.out));
        props.setProperty("dataSource.serverName", host);
        props.setProperty("dataSource.portNumber", port);

        HikariConfig config = new HikariConfig(props);
        config.setJdbcUrl("jdbc:postgresql://" + host + ":" + port +  "/" + databaseName + "?user=" + username + "&password=" + password);
        config.setDriverClassName(org.postgresql.Driver.class.getName());

        hikariDataSource = new HikariDataSource(config);
    }

    @Override
    public Connection getConnection() throws SQLException {
        if (hikariDataSource == null) {
            setup();
        }
        return hikariDataSource.getConnection();
    }

    @Override
    public void close() {
        if (this.hikariDataSource != null) {
            this.hikariDataSource.close();
        }
    }

    @Override
    public final ArrayList<DBRow> query(final String query, final Object... variables) {
        final ArrayList<DBRow> rows = new ArrayList<>();

        ResultSet result = null;
        PreparedStatement pState = null;
        Connection connection = null;

        try {
            connection = this.getConnection();
            pState = connection.prepareStatement(query);
            for (int i = 1; i <= variables.length; ++i) {
                Object obj = variables[i - 1];
                if (obj != null && obj.toString().equalsIgnoreCase("null")) {
                    obj = null;
                }
                if (obj instanceof Blob) {
                    pState.setBlob(i, (Blob) obj);
                } else if (obj instanceof InputStream) {
                    pState.setBinaryStream(i, (InputStream) obj);
                } else if (obj instanceof byte[]) {
                    pState.setBytes(i, (byte[]) obj);
                } else if (obj instanceof Boolean) {
                    pState.setBoolean(i, (boolean) obj);
                } else if (obj instanceof Integer) {
                    pState.setInt(i, (int) obj);
                } else if (obj instanceof String) {
                    pState.setString(i, (String) obj);
                } else {
                    pState.setObject(i, obj);
                }
            }
            if (pState.execute()) {
                result = pState.getResultSet();
            }
            if (result != null) {
                final ResultSetMetaData mtd = result.getMetaData();
                final int columnCount = mtd.getColumnCount();
                while (result.next()) {
                    final DBRow row = new DBRow();
                    for (int l = 0; l < columnCount; ++l) {
                        final String columnName = mtd.getColumnName(l + 1);
                        row.addCell(columnName, result.getObject(columnName));
                    }
                    rows.add(row);
                }
            }
        } catch (Exception exception) {
            exception.printStackTrace();
        } finally {
            try {
                connection.close();
                pState.close();
                result.close();
            } catch (Exception ignored) {
            }
        }

        return rows;
    }

}
