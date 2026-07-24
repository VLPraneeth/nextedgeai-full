package com.syncari.api.rest.controllers;


import com.syncari.api.rest.controllers.data.TagRequest;
import com.syncari.connector.Constants;
import com.syncari.core.model.Connector;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.misc.Taggable;
import com.syncari.core.repositories.customer.TagRepo;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.Base64;
import java.util.List;

import static com.syncari.core.security.Permissions.*;
import static org.junit.Assert.assertTrue;

public class TagControllerTest extends AbstractSyncariTest {
    @Autowired
    TagController controller;

    @Autowired
    TagRepo tagRepo;

    @Override
    public void setUp() {
        super.setUp();
        tagRepo.deleteAll();
    }

    @Override
    public void tearDown() {
        tagRepo.deleteAll();
    }

    @Test
    @WithMockUser(username = "test@email.com", authorities = {READ_TAG, ASSIGN_TAG})
    public void getTagsLike() {
        var uri = "http://syncari.com";
        var tag = new TagRequest();
        tag.setName(uri);
        tag.setTaggedId("123");
        tag.setType(Taggable.entity);
        controller.assign(List.of(tag));
        var result = controller.getTagsLike(new String(Base64.getEncoder().encode(uri.getBytes())));
        assertTrue(result.size() == 1);
    }
}


