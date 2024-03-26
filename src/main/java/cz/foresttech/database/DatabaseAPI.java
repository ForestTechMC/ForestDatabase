package cz.foresttech.database;

import cz.foresttech.database.processor.DatabaseValueProcessor;
import cz.foresttech.database.processor.ListProcessor;
import cz.foresttech.database.processor.MapProcessor;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Provides an API to interact with databases.
 * This class allows for operations such as creating tables, inserting, updating, and deleting records,
 * as well as retrieving records from databases. It uses custom value processors for handling different data types.
 */
public class DatabaseAPI {

    private final JavaPlugin javaPlugin;
    private final DatabaseEntityConvertor databaseEntityConvertor;
    private Map<Class, DatabaseValueProcessor> processorMap;
    private Map<String, ForestDatabase> databaseMap;

    public DatabaseAPI(JavaPlugin javaPlugin) {
        this.javaPlugin = javaPlugin;
        this.databaseEntityConvertor = new DatabaseEntityConvertor(this);
    }

    /**
     * Initializes the database API by setting up internal structures such as maps for processors and databases.
     */
    public void setup() {
        databaseMap = new HashMap<>();
        processorMap = new HashMap<>();

        registerNewProcessor(List.class, new ListProcessor());
        registerNewProcessor(Map.class, new MapProcessor());
        registerNewProcessor(HashMap.class, new MapProcessor());
    }

    /**
     * Closes all database connections that have been opened.
     */
    public void closeAll() {
        databaseMap.values().forEach(ForestDatabase::close);
    }

    /**
     * Registers a new processor for a specific class type.
     *
     * @param clazz                    The class for which the processor is to be registered.
     * @param databaseValueProcessor   The processor that will handle the specific class type.
     */
    public void registerNewProcessor(Class clazz, DatabaseValueProcessor databaseValueProcessor) {
        processorMap.put(clazz, databaseValueProcessor);
    }

    /**
     * Retrieves a registered processor for a specified class.
     *
     * @param clazz The class for which the processor is required.
     * @return The processor associated with the specified class.
     */
    public DatabaseValueProcessor getProcessor(Class clazz) {
        return processorMap.get(clazz);
    }

    /**
     * Adds a new {@link ForestDatabase} object to the local map.
     *
     * @param name           Name of the database (unique)
     * @param hikariDatabase {@link ForestDatabase} instance to be added
     */
    public void addDatabase(String name, ForestDatabase hikariDatabase) {
        if (databaseMap.containsKey(name.toUpperCase())) {
            return;
        }
        databaseMap.put(name.toUpperCase(), hikariDatabase);
    }

    /**
     * Retrieves the {@link ForestDatabase} object by its name.
     *
     * @param name Name of the database to retrieve
     * @return {@link ForestDatabase} stored by provided name. Returns null if no database by the name is present.
     */
    public ForestDatabase getDatabase(String name) {
        return databaseMap.get(name.toUpperCase());
    }

    /**
     * Retrieves the database entity converter.
     *
     * @return The instance of {@link DatabaseEntityConvertor} used by this API.
     */
    public DatabaseEntityConvertor getDatabaseEntityConvertor() {
        return databaseEntityConvertor;
    }

    /**
     * Creates a table in the specified database for a given class.
     *
     * @param database The name of the database.
     * @param clazz    The class representing the table structure.
     * @param <T>      The type parameter of the class.
     */
    public <T> void createTable(String database, Class<T> clazz) {
        getDatabase(database).query(databaseEntityConvertor.generateCreateScript(clazz));
    }

    /**
     * Performs an insert or update operation on the specified database.
     * If async is true, the operation is performed asynchronously.
     *
     * @param database The name of the database where the operation will be performed.
     * @param object   The object to be inserted or updated.
     * @param async    If true, performs the operation asynchronously.
     * @param <T>      The type of the object being operated on.
     */
    public <T> void insertOrUpdate(String database, T object, boolean async) {
        if (async) {
            Bukkit.getScheduler().runTaskAsynchronously(javaPlugin, ()-> insertOrUpdate(database, object));
            return;
        }
        insertOrUpdate(database, object);
    }

    /**
     * Asynchronously performs an insert or update operation on the specified database.
     *
     * @param database The name of the database where the operation will be performed.
     * @param object   The object to be inserted or updated.
     * @param <T>      The type of the object being operated on.
     */
    public <T> void insertOrUpdateAsync(String database, T object) {
        Bukkit.getScheduler().runTaskAsynchronously(javaPlugin, ()-> insertOrUpdate(database, object));
    }

    /**
     * Performs an insert or update operation on the specified database.
     * If async is true, the operation is performed asynchronously.
     *
     * @param database The name of the database where the operation will be performed.
     * @param object   The object to be inserted or updated.
     * @param <T>      The type of the object being operated on.
     */
    public <T> void insertOrUpdate(String database, T object) {
        Class<T> clazz = (Class<T>) object.getClass();
        try {
            List<DBRow> row = getDatabase(database).query(databaseEntityConvertor.insertOrUpdateScript(clazz, object));
            try {
                clazz.getField("id").set(object, row.get(0).getLong("id"));
            } catch (Exception ignored) {}
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Asynchronously performs a delete operation on the specified database for a given object.
     *
     * @param database The name of the database where the deletion will occur.
     * @param object   The object to be deleted.
     * @param <T>      The type of the object being deleted.
     */
    public <T> void deleteAsync(String database, T object) {
        Bukkit.getScheduler().runTaskAsynchronously(javaPlugin, ()-> delete(database, object));
    }

    /**
     * Asynchronously deletes all records of a specific class in the specified database.
     *
     * @param database The name of the database from which records will be deleted.
     * @param clazz    The class type representing the table from which records will be deleted.
     * @param <T>      The type parameter of the class.
     */
    public <T> void deleteAllAsync(String database, Class<T> clazz) {
        Bukkit.getScheduler().runTaskAsynchronously(javaPlugin, ()-> deleteAll(database, clazz));
    }

    /**
     * Deletes a given object from the specified database.
     *
     * @param database The name of the database where the deletion will occur.
     * @param object   The object to be deleted.
     * @param <T>      The type of the object being deleted.
     */
    public <T> void delete(String database, T object) {
        Class<T> clazz = (Class<T>) object.getClass();
        try {
            getDatabase(database).query(databaseEntityConvertor.deleteScript(clazz, object));
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Deletes all records of a specific class from the specified database.
     *
     * @param database The name of the database from which records will be deleted.
     * @param clazz    The class type representing the table from which records will be deleted.
     * @param <T>      The type parameter of the class.
     */
    public <T> void deleteAll(String database, Class<T> clazz) {
        getDatabase(database).query(databaseEntityConvertor.deleteAllScript(clazz));
    }

    /**
     * Asynchronously finds all records of a given class in the specified database.
     *
     * @param database The name of the database to search in.
     * @param clazz    The class type representing the table to search.
     * @param <T>      The type parameter of the class.
     * @return A CompletableFuture that, when completed, will yield a list of found objects.
     */
    public <T> CompletableFuture<List<T>> findAllAsync(String database, Class<T> clazz) {
        CompletableFuture<List<T>> future = new CompletableFuture<>();

        Bukkit.getScheduler().runTaskAsynchronously(javaPlugin, ()-> future.complete(findAll(database, clazz)));

        return future;
    }

    /**
     * Finds all records of a given class in the specified database.
     *
     * @param database The name of the database to search in.
     * @param clazz    The class type representing the table to search.
     * @param <T>      The type parameter of the class.
     * @return A list of found objects.
     */
    public <T> List<T> findAll(String database, Class<T> clazz) {
        List<T> dataList = new ArrayList<>();
        List<DBRow> list = getDatabase(database).query(databaseEntityConvertor.createBasicSelect(clazz));

        list.forEach(db -> {
            T t = databaseEntityConvertor.convertToEntity(clazz, db);
            if (t == null) {
                return;
            }
            dataList.add(t);
        });
        return dataList;
    }

    /**
     * Asynchronously finds all records of a given class in the specified database using a custom query.
     *
     * @param database    The name of the database to search in.
     * @param clazz       The class type representing the table to search.
     * @param customQuery The custom SQL query string to be used for retrieving the data.
     * @param <T>         The type parameter of the class.
     * @return A CompletableFuture that, when completed, will yield a list of found objects.
     */
    public <T> CompletableFuture<List<T>> findAllAsync(String database, Class<T> clazz, String customQuery) {
        CompletableFuture<List<T>> future = new CompletableFuture<>();

        Bukkit.getScheduler().runTaskAsynchronously(javaPlugin, ()-> future.complete(findAll(database, clazz, customQuery)));

        return future;
    }

    /**
     * Finds all records of a given class in the specified database using a custom query.
     *
     * @param database    The name of the database to search in.
     * @param clazz       The class type representing the table to search.
     * @param customQuery The custom SQL query string to be used for retrieving the data.
     * @param <T>         The type parameter of the class.
     * @return A list of found objects.
     */
    public <T> List<T> findAll(String database, Class<T> clazz, String customQuery) {
        List<T> dataList = new ArrayList<>();
        List<DBRow> list = getDatabase(database).query(customQuery);

        list.forEach(db -> {
            T t = databaseEntityConvertor.convertToEntity(clazz, db);
            if (t == null) {
                return;
            }
            dataList.add(t);
        });
        return dataList;
    }
}
