package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import com.syncari.core.model.FunctionDefinition;
import com.syncari.core.model.SimpleFunctionNodeConfig;
import com.syncari.core.model.util.MappingNodeType;
import com.syncari.core.repositories.customer.MappingNodeRepo;
import com.syncari.core.service.FunctionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.Optional;

@Slf4j
public class SYN_9804_FixFunctionNodeRef {

    @ChangeSet(order = "001", id = "fixFunctionNodeRef", author = "abhinav", runAlways = true)
    public void fixFunctionNodeRef(MongoTemplate db) {
        MappingNodeRepo nodeRepo = MigrationContext.getMappingNodeRepo();
        String nodeId = System.getProperty("nodeId");

        FunctionService funcService = MigrationContext.getFunctionService();

        nodeRepo.findById(nodeId).ifPresent(node -> {
            if(MappingNodeType.FUNCTION.equals(node.getType())) {
                log.info("Fixing function ref for node {} with id {} using function {}", node.getName(), node.getId(), node.getApiName());

                // retrieve function
                Optional<FunctionDefinition> funcDef = funcService.findByNameAndScope(node.getApiName(), node.getScope());
                if(funcDef.isEmpty()){
                    log.info("Function with name {} and scope {} not found", node.getApiName(), node.getScope().name());
                    return;
                }

                SimpleFunctionNodeConfig funcNodeConfig = node.getTypedConfiguration();
                funcNodeConfig.getFunctionCall().setFunctionDefinition(funcDef.get());

                node.setConfiguration(funcNodeConfig);
                nodeRepo.save(node);

                log.info("Successfully updated function ref for node {}", node.getName());
            }

        });

    }
}
