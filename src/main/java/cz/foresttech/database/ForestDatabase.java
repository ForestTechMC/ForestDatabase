package cz.foresttech.database;

import java.sql.Connection;
import java.util.ArrayList;

/**
 * Interface used for uniting database logic.
 */
public interface ForestDatabase {

    void setup();
    Connection getConnection() throws Exception;
    void close();
    ArrayList<DBRow> query(final String query, final Object... variables);
}
