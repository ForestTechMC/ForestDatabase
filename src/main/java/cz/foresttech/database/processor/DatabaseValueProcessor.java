package cz.foresttech.database.processor;

import java.lang.reflect.Type;

public interface DatabaseValueProcessor<T> {

    String getValue(T object);
    T getFromString(String string);
    default <F> T getFromString(Type fieldType, String string) {
        return getFromString(string);
    }
    String getType();

}
