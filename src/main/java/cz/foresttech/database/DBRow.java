package cz.foresttech.database;

import java.util.*;

public class DBRow {
    private final HashMap<String, Object> cells;

    public DBRow() {
        this.cells = new HashMap<>();
    }

    public void addCell(final String key, final Object value) {
        this.cells.put(key, value);
    }

    public Object getObject(final String key) {
        return this.cells.get(key);
    }

    public boolean hasColumn(final String key) {
        return this.cells.containsKey(key);
    }

    public String getString(final String key) {
        final Object obj = this.cells.get(key);
        if (obj == null) {
            return null;
        }
        if (obj instanceof Boolean) {
            return obj.toString().equalsIgnoreCase("true") ? "1" : "0";
        }
        return obj.toString();
    }

    public Long getLong(final String key) {
        final Object obj = this.cells.get(key);
        return (obj == null) ? 0 : ((Long) this.cells.get(key));
    }

}


