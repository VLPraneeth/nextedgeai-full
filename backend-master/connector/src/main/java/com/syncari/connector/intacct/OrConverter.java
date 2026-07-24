package com.syncari.connector.intacct;

import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;

public class OrConverter implements Converter {

    @Override
    public boolean canConvert(Class type) {
        return Or.class.isAssignableFrom(type);
    }

    @Override
    public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
        Or between = (Or) source;

        writer.startNode("equalto");
        for (EqualTo value : between.getEqualto()) {
            writer.startNode("field");
            writer.setValue(value.getField());
            writer.endNode();
            writer.startNode("value");
            writer.setValue(value.getValue());
            writer.endNode();
        }
        writer.endNode();
    }

    @Override
    public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
        //Unsupported
        return new Or();
    }
}