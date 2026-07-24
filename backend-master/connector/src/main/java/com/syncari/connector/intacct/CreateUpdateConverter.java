package com.syncari.connector.intacct;

import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;

import java.util.Map;

public class CreateUpdateConverter implements Converter {
    private final Converter defaultConverter;

    CreateUpdateConverter(Converter defaultConverter) {
        this.defaultConverter = defaultConverter;
    }

    @Override
    public void marshal(Object o, HierarchicalStreamWriter hierarchicalStreamWriter, MarshallingContext marshallingContext) {
        CUDOperation cudOperation = (CUDOperation) o;
        createElementField(hierarchicalStreamWriter, cudOperation.getObjectName(), cudOperation.getObjectMap());
    }

    private void createElementField(HierarchicalStreamWriter hierarchicalStreamWriter, String key, Object value) {
        hierarchicalStreamWriter.startNode(key);
        if (value == null) {
            // Setting this empty for now for returning something upstream
            hierarchicalStreamWriter.setValue("");
        } else if (value instanceof Map) {
            ((Map<String, Object>) value).forEach(
                    (k, v) -> {
                        createElementField(hierarchicalStreamWriter, k, v);
                    }
            );
        } else {
            hierarchicalStreamWriter.setValue(value.toString());
        }
        hierarchicalStreamWriter.endNode();
    }

    @Override
    public Object unmarshal(HierarchicalStreamReader hierarchicalStreamReader, UnmarshallingContext unmarshallingContext) {
        return defaultConverter.unmarshal(hierarchicalStreamReader, unmarshallingContext);
    }

    @Override
    public boolean canConvert(Class aClass) {
        return CUDOperation.class.isAssignableFrom(aClass);
    }
}
