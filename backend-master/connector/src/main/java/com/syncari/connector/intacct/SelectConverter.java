package com.syncari.connector.intacct;

import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;

import java.util.ArrayList;
import java.util.List;

public class SelectConverter implements Converter {

    @Override
    public boolean canConvert(Class type) {
        return Select.class.isAssignableFrom(type);
    }

    @Override
    public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
        Select select = (Select) source;

        for (String value : select.getField()) {
            writer.startNode("field");
            writer.setValue(value);
            writer.endNode();
        }
    }

    @Override
    public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
        List<String> values = new ArrayList<>();

        while (reader.hasMoreChildren()) {
            reader.moveDown();
            if ("field".equals(reader.getNodeName())) {
                values.add(reader.getValue());
            }
            reader.moveUp();
        }

        return new Select().setField(values);
    }
}