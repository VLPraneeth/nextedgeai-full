package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.MigrationContext;
import com.syncari.core.model.AdvancedDedupeConfig;
import com.syncari.core.model.CoreEntityNodeConfig;
import com.syncari.core.model.MappingNode;
import com.syncari.core.repositories.customer.MappingNodeRepo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;
import java.util.Map;

@Slf4j
public class FindAdvanceDedupeConfigFMPredicatesCustomers {

    @ChangeSet(order = "001", id = "findMappingAdvanceDedupeConfigPredicate", author = "rohit", runAlways = true)
    public void findMappingAdvanceDedupeConfigPredicate(MongoTemplate template) {
        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));
        MappingNodeRepo mappingNodeRepo = MigrationContext.getMappingNodeRepo();
        List<MappingNode> fieldMergeMappingNodes = mappingNodeRepo.findAllContainsDedupeAndFieldMergePolicies();
        fieldMergeMappingNodes.forEach(node -> {
            if (node.getConfiguration() instanceof CoreEntityNodeConfig){
                AdvancedDedupeConfig advancedDedupeConfig = ((CoreEntityNodeConfig)node.getConfiguration()).getAdvancedDedupeConfig();
                if (null != advancedDedupeConfig){
                    Map<String, Object> fieldMergePoliciesMap = advancedDedupeConfig.getFieldMergePoliciesMap();
                    List<Object> compositevals = (List)fieldMergePoliciesMap.getOrDefault("compositeValues",List.of());
                    for(Object cv : compositevals){
                        {
                            Map<String, Object> fieldMergePredicate = (Map)((Map)cv).getOrDefault("fieldMergePredicate", Map.of());
                            Map<String, Object> values = (Map)fieldMergePredicate.getOrDefault("value", Map.of());
                            List<Object> predicates = (List)values.getOrDefault("predicates", List.of());
                            for(Object p : predicates){
                                {
                                    String operator = (String)((Map)p).getOrDefault("operator","");
                                    if (StringUtils.isNotEmpty(operator)){
                                        if ( (operator.equalsIgnoreCase("latest_with_value")) || (operator.equalsIgnoreCase("latest_created_with_value")) ||
                                                (operator.equalsIgnoreCase("oldest_created_with_value")) || (operator.equalsIgnoreCase("earliest_with_value"))){
                                            log.info("Customer with operator in field merge policy is {} with node api name {} and mappingGraphId {}", MigrationContext.getSyncariId(), node.getApiName(), node.getMappingGraphId());
                                            Object right = ((Map)p).getOrDefault("right",null);
                                            if (null == right){
                                                log.info("Right side is null, we need to change this to map with empty value for key retainfields");
                                            }
                                            Object left = ((Map)p).getOrDefault("left",null);
                                            if (null != right){
                                                String label  = ((Map)left).get("label").toString();
                                                String fieldId  = ((Map)left).get("value").toString();
                                                log.info("Impacted field name is {} and its id is {}", label, fieldId);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }else{
                    log.info("advancedDedupeConfig is null for CoreEntityNode, no change required");
                }
            }else{
                log.info("Mapping node not instance of CoreEntityNode, no change required");
            }
        });
    }
}
