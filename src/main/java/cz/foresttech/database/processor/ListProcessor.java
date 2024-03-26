package cz.foresttech.database.processor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class ListProcessor implements DatabaseValueProcessor<List> {

    @Override
    public String getValue(List object) {
        List<String> test = (List<String>) object;
        return test.stream()
                .map(t -> Objects.toString(t, null))
                .collect(Collectors.joining(";"));
    }

    @Override
    public List getFromString(String string) {
        String[] split = string.split(";");
        List<String> list = List.of(split);
        return new ArrayList(list);
    }

    @Override
    public String getType() {
        return "TEXT";
    }

}
