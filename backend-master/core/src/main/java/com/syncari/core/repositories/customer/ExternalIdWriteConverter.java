package com.syncari.core.repositories.customer;

import com.syncari.connector.ExternalId;
import org.bson.Document;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.stereotype.Component;

@Component
@WritingConverter
public class ExternalIdWriteConverter implements Converter<ExternalId, Document>{

	public Document convert(ExternalId externalId) {
		return new Document("connectorId",externalId.getConnectorId())
				.append("entityDefinitionId",externalId.getEntityDefinitionId()).append("recordId",externalId.getRecordId());
	}

}
