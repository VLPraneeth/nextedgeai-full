package com.syncari.core.config;

import com.syncari.core.model.misc.ExternalValue;
import org.bson.Document;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.stereotype.Component;

@Component
@WritingConverter
public class ExternalValueWriteConverter implements Converter<ExternalValue, Document> {

    @Override
    public Document convert(ExternalValue value) {
        Document doc = new Document();
        doc.put("fieldId", value.getFieldId());
        doc.put("apiName", value.getApiName());
        doc.put("displayName", value.getDisplayName());
        doc.put("connectorId", value.getConnectorId());
        doc.put("connectorName", value.getConnectorName());
        doc.put("value", value.getValue());
        return doc;
    }
}