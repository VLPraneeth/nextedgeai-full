package com.syncari.core.model;

import com.syncari.core.AbstractSyncariTest;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.repositories.customer.ActionDefinitionRepo;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class GenericActionConfigTest extends AbstractSyncariTest {

    @Autowired
    ActionDefinitionRepo actionDefinitionRepo;


    @Test
    public void validateActionConfig(){

    }
}
