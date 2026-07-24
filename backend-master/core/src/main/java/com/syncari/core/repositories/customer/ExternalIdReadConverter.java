package com.syncari.core.repositories.customer;

import com.syncari.connector.ExternalId;
import com.syncari.core.datatype.Datatype;
import com.syncari.core.datatype.DatatypeFactory;
import org.bson.Document;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.stereotype.Component;

import javax.print.Doc;

@Component
public class ExternalIdReadConverter implements Converter<Document, ExternalId>{

	@Override
	public ExternalId convert(Document document) {
		return new ExternalId(document.getString("connectorId"),
				document.getString("entityDefinitionId"),document.getString("recordId"));
	}

}
