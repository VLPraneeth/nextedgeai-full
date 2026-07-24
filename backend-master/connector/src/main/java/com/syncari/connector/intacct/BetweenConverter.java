package com.syncari.connector.intacct;

import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;

import java.util.ArrayList;
import java.util.List;

public class BetweenConverter implements Converter {

    @Override
    public boolean canConvert(Class type) {
        return Between.class.isAssignableFrom(type);
    }

    @Override
    public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
        Between between = (Between) source;

        writer.startNode("field");
        writer.setValue(between.getField());
        writer.endNode();

        for (String value : between.getValue()) {
            writer.startNode("value");
            writer.setValue(value);
            writer.endNode();
        }
    }

    @Override
    public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
        String field = null;
        List<String> values = new ArrayList<>();

        while (reader.hasMoreChildren()) {
            reader.moveDown();
            if ("field".equals(reader.getNodeName())) {
                field = reader.getValue();
            } else if ("value".equals(reader.getNodeName())) {
                values.add(reader.getValue());
            }
            reader.moveUp();
        }

        return new Between().setField(field).setValue(values);
    }
}