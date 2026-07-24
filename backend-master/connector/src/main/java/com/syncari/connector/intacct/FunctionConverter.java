package com.syncari.connector.intacct;

import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.converters.reflection.ReflectionProvider;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.mapper.Mapper;

public class FunctionConverter implements Converter {
    private Mapper mapper;
    private final Converter defaultConverter;
    private final ReflectionProvider reflectionProvider;

    FunctionConverter(Mapper mapper, Converter defaultConverter, ReflectionProvider reflectionProvider) {
        this.mapper = mapper;
        this.defaultConverter = defaultConverter;
        this.reflectionProvider = reflectionProvider;
    }

    @Override
    public void marshal(Object o, HierarchicalStreamWriter hierarchicalStreamWriter, MarshallingContext marshallingContext) {
        Function function = (Function) o;
        hierarchicalStreamWriter.addAttribute("controlid",function.getControlid());
        String simpleName = function.getFunctionBody().getClass().getSimpleName();
        XStreamAlias annotation = function.getFunctionBody().getClass().getAnnotation(XStreamAlias.class);
        if(annotation!=null){
            simpleName = annotation.value();
        }
        hierarchicalStreamWriter.startNode(simpleName);
        marshallingContext.convertAnother(function.getFunctionBody());
        hierarchicalStreamWriter.endNode();
    }

    @Override
    public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
        Function func = new Function();
        reader.moveDown();
        FunctionBody body = (FunctionBody) context.convertAnother(func, mapper.realClass(reader.getNodeName()),defaultConverter);
        func.setFunctionBody(body);
        reader.moveUp();

        return func;
    }

    @Override
    public boolean canConvert(Class aClass) {
        return Function.class.isAssignableFrom(aClass);
    }
}
