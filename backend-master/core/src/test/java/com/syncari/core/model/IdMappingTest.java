package com.syncari.core.model;

import org.junit.Test;

import static org.junit.Assert.*;

public class IdMappingTest {

    @Test
    public void findersExcludeDisconnectedMappings(){
        final IdMapping idMapping = new IdMapping()
                .addMapping("c1", "c1-e1-r1", "c1-e1")
                .addMapping("c2", "c2-e2-r2", "c2-e2")
                .addMapping("c3", "c3-e3-r3", "c3-e3");
        assertTrue(idMapping.findMapping("c1","c1-e1","c1-e1-r1").isPresent());
        assertTrue(idMapping.getMapping("c1","c1-e1").isPresent());
        assertEquals(1,idMapping.getMappings("c1","c1-e1").size());

        idMapping.disconnectMapping("c1","c1-e1","c1-e1-r1");
        assertFalse(idMapping.findMapping("c1","c1-e1","c1-e1-r1").isPresent());
        assertFalse(idMapping.getMapping("c1","c1-e1").isPresent());
        assertEquals(0,idMapping.getMappings("c1","c1-e1").size());
    }

    @Test
    public void isDisconnectedTest(){
        final IdMapping idMapping = new IdMapping()
                .addMapping("c1", "c1-e1-r1", "c1-e1")
                .addMapping("c2", "c2-e2-r2", "c2-e2");

        assertFalse(idMapping.isDisconnected("c3", "c3-e3"));
        assertFalse(idMapping.isDisconnected("c1", "c1-e1"));
        assertFalse(idMapping.isDisconnected("c2", "c2-e2"));

        final IdMapping idMapping1 = new IdMapping()
                .addMapping("c1", "c1-e1-r1", "c1-e1")
                .addMapping("c2", "c2-e2-r2", "c2-e2")
                .addMapping("c3", "c3-e3-r3", "c3-e3", true);
        assertTrue(idMapping1.isDisconnected("c3", "c3-e3"));


        final IdMapping idMapping2 = new IdMapping()
                .addMapping("c1", "c1-e1-r1", "c1-e1")
                .addMapping("c2", "c2-e2-r2", "c2-e2")
                .addMapping("c3", "c3-e3-r3", "c3-e3", true)
                .addMapping("c3", "c3-e3-r4", "c3-e3", true);
        assertTrue(idMapping1.isDisconnected("c3", "c3-e3"));
        assertTrue(idMapping2.isDisconnected("c3", "c3-e3"));

        final IdMapping idMapping3 = new IdMapping()
                .addMapping("c1", "c1-e1-r1", "c1-e1")
                .addMapping("c2", "c2-e2-r2", "c2-e2")
                .addMapping("c3", "c3-e3-r3", "c3-e3", false)
                .addMapping("c3", "c3-e3-r4", "c3-e3", true);
        assertFalse(idMapping3.isDisconnected("c3", "c3-e3"));

        final IdMapping idMapping4 = new IdMapping();
        assertFalse(idMapping4.isDisconnected("c1", "c1-e1"));

    }
}