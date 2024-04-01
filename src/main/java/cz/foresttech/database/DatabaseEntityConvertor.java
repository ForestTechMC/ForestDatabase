package cz.foresttech.database;

import cz.foresttech.database.annotation.*;
import cz.foresttech.database.processor.DatabaseValueProcessor;

import java.lang.reflect.Field;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class DatabaseEntityConvertor {

    private final DatabaseAPI databaseAPI;

    public DatabaseEntityConvertor(DatabaseAPI databaseAPI) {
        this.databaseAPI = databaseAPI;
    }

    /**
     * Converts a DBRow to an instance of the specified class.
     *
     * @param clazz the class of the object to be created.
     * @param row   the DBRow object containing database column data.
     * @return an instance of T populated with data from the DBRow, or null in case of failure.
     */
    public <T> T convertToEntity(Class<T> clazz, DBRow row) {
        try {
            T instance = clazz.getDeclaredConstructor().newInstance();
            getDeclaredFields(clazz)
                    .forEach(field -> populateFieldFromDBRow(instance, field, row));
            return instance;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Populates a field of an instance with the corresponding value from a DBRow.
     *
     * @param instance the object instance whose field is to be populated.
     * @param field    the field to be populated.
     * @param row      the DBRow object containing database column data.
     */
    private <T> void populateFieldFromDBRow(T instance, Field field, DBRow row) {
        try {
            if (!isColumnField(field)) return;

            field.setAccessible(true);
            String dbName = getDatabaseColumnName(field);
            if (!row.hasColumn(dbName)) return;

            Object fieldValue = getFieldValue(field, row, dbName);
            field.set(instance, fieldValue);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Checks if a given field is annotated as a column.
     *
     * @param field the field to be checked.
     * @return true if the field is annotated as a column, otherwise false.
     */
    private boolean isColumnField(Field field) {
        return field.getAnnotation(Column.class) != null;
    }

    /**
     * Retrieves the database column name associated with a field.
     *
     * @param field the field whose database column name is required.
     * @return the database column name for the field.
     */
    private String getDatabaseColumnName(Field field) {
        Column column = field.getAnnotation(Column.class);
        return column == null || column.key().isEmpty() ? camelCaseToSnakeCase(field.getName()) : column.key();
    }

    /**
     * Gets the value for a field from a DBRow.
     *
     * @param field  the field for which value is required.
     * @param row    the DBRow containing the data.
     * @param dbName the name of the database column.
     * @return the value corresponding to the field from the DBRow.
     */
    private Object getFieldValue(Field field, DBRow row, String dbName) {
        String rawValue = row.getString(dbName);
        Object newValue;

        if (rawValue == null) {
            if (field.getType() == int.class) {
                return 0;
            }

            if (field.getType() == long.class) {
                return 0L;
            }

            if (field.getType() == double.class) {
                return 0.0;
            }

            if (field.getType() == boolean.class) {
                return false;
            }

            if (field.getType() == float.class) {
                return 0.0f;
            }

            if (field.getType() == char.class) {
                return 'x';
            }

            if (field.isAnnotationPresent(NullableColumn.class)) {
                return null;
            }

            return null;
        }

        if (field.getType().equals(UUID.class)) {
            newValue = UUID.fromString(rawValue);
        } else if (field.getType().isEnum()) {
            newValue = Enum.valueOf((Class<Enum>) field.getType(), rawValue);
        } else {
            DatabaseValueProcessor databaseValueProcessor = databaseAPI.getProcessor(field.getType());
            if (databaseValueProcessor != null) {
                newValue = databaseValueProcessor.getFromString(field.getGenericType(), rawValue);
            } else {
                newValue = row.getObject(dbName);
            }
        }

        return newValue;
    }

    /**
     * Generates a basic SELECT SQL script for a given class.
     *
     * @param clazz the class for which the SELECT script is required.
     * @return a SELECT SQL script for the given class.
     */
    public String createBasicSelect(Class<?> clazz) {
        String tableName = getTableName(clazz);
        return "SELECT * FROM " + tableName + ";";
    }

    /**
     * Generates a SQL script to delete all records from the table associated with a class.
     *
     * @param clazz the class for which the DELETE script is required.
     * @return a DELETE SQL script for the given class.
     */
    public <T> String deleteAllScript(Class<T> clazz) {
        String tableName = getTableName(clazz);
        if (tableName.isEmpty()) {
            return null;
        }

        return "DELETE FROM " +
                tableName +
                ";";
    }

    /**
     * Generates a SQL script to delete a specific record associated with a class instance.
     *
     * @param clazz  the class for which the DELETE script is required.
     * @param object the instance of the class to identify the record to be deleted.
     * @return a DELETE SQL script for a specific record of the given class.
     */
    public <T> String deleteScript(Class<T> clazz, T object) throws IllegalAccessException {
        String tableName = getTableName(clazz);
        if (tableName.isEmpty()) {
            return null;
        }

        String condition = processDeleteConditionScript(clazz, object);
        return String.format("DELETE FROM %s WHERE (%s);", tableName, condition);
    }

    /**
     * Generates an SQL script for inserting or updating a record based on a class instance.
     *
     * @param clazz  the class for which the script is required.
     * @param object the instance of the class for which the record is to be inserted or updated.
     * @return an INSERT or UPDATE SQL script for the given class instance.
     */
    public <T> String insertOrUpdateScript(Class<T> clazz, T object) throws IllegalAccessException {
        String tableName = getTableName(clazz);
        if (tableName.isEmpty()) {
            return null;
        }

        String columns = getColumnsFromField(clazz);
        String values = getValuesFromField(clazz, object);

        String conflictPolicy = getConflictPolicy(clazz);

        return String.format("INSERT INTO %s (%s) VALUES (%s) ON CONFLICT (%s) DO UPDATE SET (%s) = (%s);",
                tableName, columns, values, conflictPolicy, columns, values);
    }

    /**
     * Generates a SQL script to create a table based on a class definition.
     *
     * @param clazz the class for which the CREATE TABLE script is required.
     * @return a CREATE TABLE SQL script based on the class definition.
     */
    public String generateCreateScript(Class<?> clazz) {
        String tableName = getTableName(clazz);
        if (tableName.isEmpty()) {
            return null;
        }

        String fieldsDefinition = getFieldsDefinition(clazz);
        String primaryKeyConstraint = getPrimaryKeyConstraint(clazz);

        return String.format("CREATE TABLE IF NOT EXISTS %s (%s%s);", tableName, fieldsDefinition, primaryKeyConstraint);
    }

    /**
     * Processes fields of a class to generate a part of SQL script for delete operations.
     *
     * @param clazz  the class whose fields are to be processed.
     * @param object the instance of the class.
     * @return a string representing a part of SQL script.
     */
    private <T> String processDeleteConditionScript(Class<T> clazz, T object) throws IllegalAccessException {
        StringBuilder keys = new StringBuilder();
        StringBuilder values = new StringBuilder();

        for (Field field : getDeclaredFields(clazz)) {
            field.setAccessible(true);

            boolean isPrimaryKey = field.isAnnotationPresent(PrimaryKey.class);
            if (!isPrimaryKey) continue;

            Column column = field.getAnnotation(Column.class);
            if (column == null) continue;

            String dbName = getDbName(field, column);
            Object fieldValue = field.get(object);
            DatabaseValueProcessor valueProcessor = databaseAPI.getProcessor(field.getType());

            String processedValue = processFieldValue(fieldValue, valueProcessor);
            keys.append(dbName).append(",");
            values.append(processedValue).append(",");
        }

        if (keys.isEmpty()) return "";

        keys.setLength(keys.length() - 1);
        values.setLength(values.length() - 1);

        return "(" + keys + ") = (" + values + ")";
    }

    private <T> String getValuesFromField(Class<T> clazz, T object) throws IllegalAccessException {
        StringBuilder values = new StringBuilder();

        for (Field field : getDeclaredFields(clazz)) {
            field.setAccessible(true);
            Column column = field.getAnnotation(Column.class);
            if (column == null) continue;
            String dbName = getDbName(field, column);
            Object fieldValue = field.get(object);
            DatabaseValueProcessor valueProcessor = databaseAPI.getProcessor(field.getType());

            String processedValue = processFieldValue(fieldValue, valueProcessor);
            values.append(processedValue).append(",");
        }

        if (!values.isEmpty()) values.setLength(values.length() - 1);

        return values.toString();
    }

    private <T> String getColumnsFromField(Class<T> clazz) {
        StringBuilder keys = new StringBuilder();

        for (Field field : getDeclaredFields(clazz)) {
            field.setAccessible(true);
            Column column = field.getAnnotation(Column.class);
            if (column == null) continue;
            String dbName = getDbName(field, column);
            keys.append(dbName).append(",");
        }

        if (!keys.isEmpty()) keys.setLength(keys.length() - 1);

        return keys.toString();
    }

    /**
     * Processes the value of a field for inclusion in an SQL script.
     *
     * @param value     the value to be processed.
     * @param processor the DatabaseValueProcessor for processing the value.
     * @return a string representation of the value suitable for SQL script.
     */
    private String processFieldValue(Object value, DatabaseValueProcessor processor) {
        if (value == null) return "NULL";

        if (processor != null) {
            value = processor.getValue(value);
        }

        return "'" + ((value instanceof String) ? escapeString((String) value) : value.toString()) + "'";
    }

    /**
     * Retrieves the conflict policy for a given class.
     *
     * @param clazz the class whose conflict policy is required.
     * @return a string representing the conflict policy.
     */
    private String getConflictPolicy(Class<?> clazz) {
        DatabaseEntity databaseEntity = clazz.getAnnotation(DatabaseEntity.class);
        if (databaseEntity != null && !databaseEntity.conflictPolicy().isEmpty()) {
            return databaseEntity.conflictPolicy();
        } else {
            return getPrimaryKey(clazz);
        }
    }

    /**
     * Builds a SQL fields definition part for a CREATE TABLE script based on a class definition.
     *
     * @param clazz the class whose fields are to be defined in SQL.
     * @return a string representing the fields definition for SQL CREATE TABLE script.
     */
    private String getFieldsDefinition(Class<?> clazz) {
        StringBuilder definition = new StringBuilder();

        for (Field field : getDeclaredFields(clazz)) {
            if (field.isAnnotationPresent(Column.class)) {
                Column column = field.getAnnotation(Column.class);
                String dbName = getDbName(field, column);
                definition.append(dbName).append(" ").append(getSqlType(field, column)).append(",");
            }
        }

        if (!definition.isEmpty()) {
            definition.setLength(definition.length() - 1); // remove trailing comma
        }

        return definition.toString();
    }

    /**
     * Determines the SQL type of a field based on its annotations and type.
     *
     * @param field  the field whose SQL type is needed.
     * @param column the Column annotation of the field.
     * @return a string representing the SQL type of the field.
     */
    private String getSqlType(Field field, Column column) {
        StringBuilder sqlTypeBuilder = new StringBuilder();

        // Retrieve custom database value processor, if available
        DatabaseValueProcessor valueProcessor = databaseAPI.getProcessor(field.getType());
        if (valueProcessor != null) {
            sqlTypeBuilder.append(valueProcessor.getType());
        } else if (!column.type().isEmpty()) {
            // Use type specified in the Column annotation
            sqlTypeBuilder.append(column.type());
        } else {
            // Default handling for various Java types
            Class<?> fieldType = field.getType();

            if (fieldType == String.class) {
                if (field.isAnnotationPresent(Text.class)) {
                    Text textAnnotation = field.getAnnotation(Text.class);
                    if (textAnnotation.customLength() > 10485760 || textAnnotation.customLength() < 0) {
                        sqlTypeBuilder.append("TEXT");
                    } else {
                        sqlTypeBuilder.append(String.format("VARCHAR(%d)", textAnnotation.customLength()));
                    }
                } else {
                    sqlTypeBuilder.append("VARCHAR(30)");
                }
            } else if (fieldType == int.class) {
                sqlTypeBuilder.append("INTEGER");
            } else if (fieldType == long.class) {
                sqlTypeBuilder.append("BIGINT");
            } else if (fieldType == double.class) {
                sqlTypeBuilder.append("DOUBLE PRECISION");
            } else if (fieldType == boolean.class) {
                sqlTypeBuilder.append("BOOLEAN");
            } else if (fieldType == UUID.class) {
                sqlTypeBuilder.append("VARCHAR(36)");
            } else if (fieldType == Timestamp.class) {
                sqlTypeBuilder.append("TIMESTAMP");
            } else if (fieldType.isEnum()) {
                sqlTypeBuilder.append("TEXT");
            } else if (fieldType == List.class) {
                sqlTypeBuilder.append("TEXT");
            } else if (field.isAnnotationPresent(AutoIncrement.class)) {
                sqlTypeBuilder.append("SERIAL");
            } else {
                sqlTypeBuilder.append("TEXT");
            }
        }

        // Append NOT NULL constraint
        if (field.isAnnotationPresent(PrimaryKey.class) || !field.isAnnotationPresent(NullableColumn.class)) {
            sqlTypeBuilder.append(" NOT NULL");
        }

        // Append UNIQUE constraint
        if (field.isAnnotationPresent(Unique.class)) {
            sqlTypeBuilder.append(" UNIQUE");
        }

        return sqlTypeBuilder.toString();

    }

    /**
     * Builds the primary key constraint part of a SQL CREATE TABLE script based on a class definition.
     *
     * @param clazz the class whose primary key constraint is to be built.
     * @return a string representing the primary key constraint for SQL CREATE TABLE script.
     */
    private String getPrimaryKeyConstraint(Class<?> clazz) {
        List<String> primaryKeys = getDeclaredFields(clazz).stream()
                .filter(f -> f.isAnnotationPresent(PrimaryKey.class))
                .map(f -> getDbName(f, f.getAnnotation(Column.class)))
                .collect(Collectors.toList());

        if (!primaryKeys.isEmpty()) {
            return ", CONSTRAINT " + clazz.getSimpleName() + "_pk PRIMARY KEY (" + String.join(", ", primaryKeys) + ")";
        }

        return "";
    }

    /**
     * Extracts the primary key field names from a class definition and formats them as a comma-separated list in snake case.
     *
     * @param clazz the class from which to extract the primary key.
     * @return a string representing the primary key fields in snake case, or null if no primary keys are defined.
     */
    private static String getPrimaryKey(Class<?> clazz) {
        return getDeclaredFields(clazz).stream()
                .filter(field -> field.isAnnotationPresent(PrimaryKey.class))
                .map(field -> {
                    Column column = field.getAnnotation(Column.class);
                    return getDbName(field, column);
                })
                .collect(Collectors.joining(", "));
    }

    /**
     * Retrieves the table name for a given class, based on its annotations.
     *
     * @param clazz the class whose table name is required.
     * @return the table name in snake case.
     */
    private static String getTableName(Class<?> clazz) {
        String tableName = null;

        if (clazz.isAnnotationPresent(DatabaseEntity.class)) {
            DatabaseEntity databaseEntity = clazz.getAnnotation(DatabaseEntity.class);
            tableName = databaseEntity.table();
        }

        if (tableName == null || tableName.isEmpty()) {
            tableName = camelCaseToSnakeCase(clazz.getSimpleName());
        }

        if (tableName.isEmpty()) {
            return tableName;
        }

        return "\"" + tableName + "\"";
    }

    /**
     * Retrieves the database column name for a given field, based on its annotations.
     *
     * @param field  the field whose database column name is required.
     * @param column the Column annotation of the field.
     * @return the database column name in snake case.
     */
    private static String getDbName(Field field, Column column) {
        return column.key().isEmpty() ? camelCaseToSnakeCase(field.getName()) : column.key();
    }

    /**
     * Converts a camelCase string to a snake_case string.
     *
     * @param input the camelCase string to be converted.
     * @return the snake_case version of the string.
     */
    private static String camelCaseToSnakeCase(String input) {
        return input.replaceAll("([a-z])([A-Z]+)", "$1_$2").toLowerCase();
    }

    /**
     * Escapes a string for safe inclusion in an SQL script.
     *
     * @param input the string to be escaped.
     * @return the escaped string.
     */
    private static String escapeString(String input) {
        if (input == null) {
            return null;
        }

        return input.replace("'", "''");
    }

    /**
     * Retrieves all declared fields of a class, including inherited fields.
     *
     * @param clazz the class whose fields are required.
     * @return a list of all declared fields of the class.
     */
    private static List<Field> getDeclaredFields(Class<?> clazz) {
        List<Field> fieldList = new ArrayList<>();

        if (clazz.getSuperclass() != null && clazz != Object.class) {
            fieldList.addAll(getDeclaredFields(clazz.getSuperclass()));
        }
        fieldList.addAll(Arrays.asList(clazz.getDeclaredFields()));

        return fieldList;
    }

}
