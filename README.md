# ForestDatabase

ForestDatabase is a Java library for SpigotMC projects which 
allows automated SQL queries generation using custom entity annotations. The library supports database system PostgreSQL.

## Table of contents

* [Getting started](#getting-started)
* [Setting up the API](#setting-up-the-api)
* [Annotations](#annotations)
* [Accessing the database](#accessing-the-database)
* [License](#license)

## Getting started

[![badge](https://jitpack.io/v/ForestTechMC/ForestDatabase.svg)](https://jitpack.io/#ForestTechMC/ForestDatabase)

Replace **VERSION** with the version you want to use. The latest is always recommended.

<details>
    <summary>Maven</summary>

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>com.github.ForestTechMC</groupId>
        <artifactId>ForestDatabase</artifactId>
        <version>1.0.8</version>
    </dependency>
</dependencies>
```
</details>

<details>
    <summary>Gradle</summary>

```groovy
allprojects {
    repositories {
        maven { url 'https://jitpack.io' }
    }
}

dependencies {
    implementation 'com.github.ForestTechMC:ForestDatabase:VERSION'
}
```
</details>

## Setting up the API

To use ForestDatabase, its API needs to be initialized first. It is recommended to do so in `onEnable` method.

```java
@Override
public void onEnable() {
    DatabaseAPI databaseAPI = new DatabaseAPI(this);
    databaseAPI.setup();
}
```

After initial setup, database object needs to be registered to the API.

```java
HikariDatabase hikariDatabase = new HikariDatabase(
        "localhost:5432",
        "my_database",
        "username",
        "password");

databaseAPI.addDatabase("database_id", hikariDatabase);
```

All connections shall be closed using `databaseAPI#closeAll()` call.

## Annotations

To make entity be recognized by the ForestDatabase, it needs to be annotated with special annotations and must include empty constructor.

Each entity needs to be annotated with `@DatabaseEntity` annotation. Each field which shall be recognized by ForestDatabase shall include `@Column` annotation.

```java
import cz.foresttech.database.annotation.*;

@DatabaseEntity
public class Car {

    @Column
    @PrimaryKey
    @AutoIncrement
    private int id;

    @Column
    @Text(customLength = 50)
    private String brandName;

    @Column
    private double price;

    @Column
    @NullableColumn
    @Text
    private String description;
    
    // Will be ignored by ForestDatabase
    private boolean internalValue;
    
    public Car() {
    }
    
    /* .. getters, setter, another constructors ... */
}
```

ForestDatabase will automatically convert class names and field names to snake case, if custom name is not used.

```java
@DatabaseEntity(table="custom_table_name")
@Column(key="custom_column_name")
```

## Accessing the database

To access the database and load/edit the data, you can use direct methods on DatabaseAPI instance.

```java
// Creates the table (if not exist)
databaseAPI.createTable("database_id", Car.class);

// Inserts the object data into the database
Car car = new Car();
databaseAPI.insertOrUpdate("database_id", car);

// Removes the object from the database asynchronously
databaseAPI.deleteAsync("database_id", car);

// Retrieves all objects from the database
databaseAPI.findAll("database_id", Car.class).forEach(car -> {
    // ... do stuff
});
```

## License
ForestDatabase is licensed under the permissive MIT license. Please see [`LICENSE.txt`](https://github.com/ForestTechMC/ForestRedisAPI/blob/master/LICENSE.txt) for more information.