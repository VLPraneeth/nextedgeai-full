package com.syncari.core.config;

import com.syncari.core.model.misc.ExternalValue;
import org.bson.Document;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class ExternalValueReadConverter implements Converter<Object, ExternalValue> {

    /*
    Need this class/method to handle old transaction logs where external value is just a object (with actual value)
     */
    @Override
    public ExternalValue convert(Object value) {

        if (value instanceof Document) {
            Document doc = (Document) value;
            return new ExternalValue().setFieldId(doc.getString("fieldId"))
                    .setApiName(doc.getString("apiName"))
                    .setDisplayName(doc.getString("displayName"))
                    .setDataType(doc.getString("dataType"))
                    .setConnectorId(doc.getString("connectorId"))
                    .setConnectorName(doc.getString("connectorName"))
                    .setValue(doc.get("value"));
        } else {
            return new ExternalValue().setValue(value);
        }
    }
}