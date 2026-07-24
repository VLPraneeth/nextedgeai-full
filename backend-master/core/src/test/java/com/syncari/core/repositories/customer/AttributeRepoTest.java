package com.syncari.core.repositories.customer;

import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.datatype.StringType;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.util.Status;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.Assert.*;

public class AttributeRepoTest extends AbstractSyncariTest {
    @Autowired
    AttributeRepo attributeProxyRepo;
    @Test
    public void findByEntityIdAndApiName() {
        AttributeDefinition attributeDefinition = new AttributeDefinition();
        attributeDefinition.setApiName("TestAPi").setEntityId("someId").setStatus(Status.ACTIVE).setDisplayName("Test API")
                .setDataType(StringType.VALUE);
        attributeProxyRepo.save(attributeDefinition);
        assertEquals("TestAPi", attributeProxyRepo.findByEntityIdAndApiName("someId","TestApi").get().getApiName());
        assertEquals("TestAPi", attributeProxyRepo.findByEntityIdAndApiName("someId","testapi").get().getApiName());
        assertFalse(attributeProxyRepo.findByEntityIdAndApiName("someId","estap").isPresent());
    }

    @Test(expected = DuplicateKeyException.class)
    @Ignore
    public void insertDuplicateAttribute() {

        AttributeDefinition attributeDefinition = null;
        AttributeDefinition dupAttributeDefinition = null;
        try {
            attributeDefinition = new AttributeDefinition();
            attributeDefinition.setApiName("CASE sensitive API").setEntityId("someId").setStatus(Status.ACTIVE).setDisplayName("Case Sensitive API")
                    .setDataType(StringType.VALUE);
            attributeDefinition = attributeProxyRepo.save(attributeDefinition);

            dupAttributeDefinition = new AttributeDefinition();
            dupAttributeDefinition.setApiName("case SENSITIVE api").setEntityId("SOMEID").setStatus(Status.ACTIVE).setDisplayName("Case Sensitive API")
                    .setDataType(StringType.VALUE);
            attributeProxyRepo.save(dupAttributeDefinition);
        } finally {
            if (attributeDefinition != null) {
                attributeProxyRepo.delete(attributeDefinition);
            }
        }
    }
}