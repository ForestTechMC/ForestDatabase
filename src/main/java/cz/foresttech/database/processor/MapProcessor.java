package cz.foresttech.database.processor;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

public class MapProcessor implements DatabaseValueProcessor<Map> {

    @Override
    public String getValue(Map object) {
        Map<String, Object> supportedMap = (Map<String, Object>) object;

        return supportedMap.entrySet().stream()
                .map(entry -> entry.getKey() + ":" + entry.getValue())
                .collect(Collectors.joining(";"));
    }

    @Override
    public Map getFromString(String string) {
        Map<String, Object> map = Arrays.stream(string.split(";"))
                .map(entry -> entry.split(":"))
                .filter(entry -> entry.length == 2)
                .collect(Collectors.toMap(entry -> entry[0], entry -> entry[1]));

        return map;
    }

    @Override
    public String getType() {
        return "TEXT";
    }

}
