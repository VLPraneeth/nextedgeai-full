package com.syncari.api.rest.controllers;

import com.syncari.api.rest.controllers.data.NodeDef;
import com.syncari.core.schema.EntityDef;
import com.syncari.core.service.SchemaService;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.syncari.core.security.Permissions.READ_STUDIO;
import static com.syncari.core.security.Permissions.WRITE_STUDIO;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FunctionControllerTest extends AbstractSyncariTest {

    @Autowired
    FunctionController controller;

    @Autowired
    SchemaService schemaService;

    @Override
    public void setUp() {
        super.setUp();
    }
    
    @Override
    public void tearDown() {
    }

    @Test
    @WithMockUser(username = "admin", authorities = {WRITE_STUDIO, READ_STUDIO})
    public void listFunctions() throws Exception {
        EntityDef contact = schemaService.getSyncariSchema().findEntityByName("contact").get();
        List<NodeDef> functions = controller.getFunctionsWithEntityContext(contact.getId());
        assertEquals(46, functions.size());
    }

    @Test
    @WithMockUser(username = "admin", authorities = {WRITE_STUDIO, READ_STUDIO})
    public void syncariEntityFunctions() throws Exception {
        ArrayList values = controller.getFunctionsWithEntityContext(schemaService.getSyncariSchema().findEntityByName("contact").get().getId()).stream()
                .filter(func-> func.getName().equalsIgnoreCase("advancedLookUpSyncariRecord")).findFirst().get()
                .getConfiguration().stream().filter(conf -> conf.containsKey("values")).collect(Collectors.toList()).get(0).get("values");
        assertTrue(values.size() >= 8);
    }
}
