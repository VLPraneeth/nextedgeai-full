package com.syncari.api.rest.controllers.data;

import org.junit.Test;

import com.syncari.api.rest.controllers.AbstractSyncariTest;
import com.syncari.api.rest.controllers.data.studio.AttributeNodeConfig;
import com.syncari.core.datatype.StringType;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.Connector;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.util.MappingNodeType;
import com.syncari.core.utils.SchemaHelper;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.Assert.*;
import static com.syncari.core.utils.GraphHelper.createConnector;

public class AttributeNodeConfigTest extends AbstractSyncariTest {

    @Test
    public void sourceNodeConfiguration() {

        Connector connector = createConnector("sourceConnector", "sourceConnectorId", "sourceConnectorMeta");
        EntityDefinition srcEntity = SchemaHelper.createEntityDef("srcAccount", "Source Account", connector);
        AttributeDefinition srcField = SchemaHelper.createAttribute("srcField", StringType.VALUE, srcEntity.getId());

        AttributeNodeConfig config = new AttributeNodeConfig(srcEntity, srcField, true, connector);
        var configurations = config.getNodeConfiguration();
        assertNotNull(configurations);

        // Should have all the new confgiurations
        var attrbuteConfigurations = Arrays.asList("entityDefinitionId", "dataType", "multiValues", "required");
        var foundConfigurations = configurations.stream().filter(c -> {
        var name = c.get("name");
        if (null != name) {
            return attrbuteConfigurations.contains(name.toString());
        }
        return false;
        }).collect(Collectors.toList());
        assertEquals(attrbuteConfigurations.size(), foundConfigurations.size());

        // Should not have any of the sink configurations
        var sinkOnlyConfigurations = Arrays.asList("defaultValue", "alwaysUseDefaultOnEmpty", "rejectEmpty");
        var sinkOnlyFoundConfigurations = configurations.stream().filter(c -> {
        var name = c.get("name");
        if (null != name) {
            return sinkOnlyConfigurations.contains(name.toString());
        }
        return false;
        }).collect(Collectors.toList());
        assertEquals(0, sinkOnlyFoundConfigurations.size());

        var nodeTypeConfiguration = configurations.stream().anyMatch(c -> {
            var name = c.get("name");
            if (null != name && name.toString().equalsIgnoreCase("nodeType")) {
                var value = c.get("value");
                if (null != value && value.toString().equalsIgnoreCase(MappingNodeType.ATTRIBUTE_SOURCE.name())) {
                    return true;
                }
            }
            return false;
        });
        assertTrue(nodeTypeConfiguration);
    }

    @Test
    public void sinkNodeConfiguration() {

        Connector connector = createConnector("sinkConnector", "sinkConnectorId", "sinkConnectorMeta");
        EntityDefinition sinkEntity = SchemaHelper.createEntityDef("sinkAccount", "Sink Account", connector);
        AttributeDefinition sinkField = SchemaHelper.createAttribute("readOnlyField", StringType.VALUE, sinkEntity.getId());

        AttributeNodeConfig config = new AttributeNodeConfig(sinkEntity, sinkField, false, connector);
        var configurations = config.getNodeConfiguration();
        assertNotNull(configurations);

        // Should have all the expected sink configurations
        var attrbuteConfigurations = Arrays.asList("entityDefinitionId", "dataType", "multiValues", "required", "defaultValue", "alwaysUseDefaultOnEmpty", "rejectEmpty");
        var foundConfigurations = configurations.stream().filter(c -> {
        var name = c.get("name");
        if (null != name) {
            return attrbuteConfigurations.contains(name.toString());
        }
        return false;
        }).collect(Collectors.toList());
        assertEquals(attrbuteConfigurations.size(), foundConfigurations.size());

        var nodeTypeConfiguration = configurations.stream().anyMatch(c -> {
            var name = c.get("name");
            if (null != name && name.toString().equalsIgnoreCase("nodeType")) {
                var value = c.get("value");
                if (null != value && value.toString().equalsIgnoreCase(MappingNodeType.ATTRIBUTE_SINK.name())) {
                    return true;
                }
            }
            return false;
        });
        assertTrue(nodeTypeConfiguration);
    }
}
