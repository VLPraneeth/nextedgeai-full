package com.syncari.dbm.customscripts.customer;

import com.github.mongobee.changeset.ChangeSet;
import com.syncari.core.Features;
import com.syncari.core.MigrationContext;
import com.syncari.core.SyncariContext;
import com.syncari.core.model.AdvancedDedupeConfig;
import com.syncari.core.model.CoreEntityNodeConfig;
import com.syncari.core.model.MappingNode;
import com.syncari.core.repositories.customer.MappingNodeRepo;
import com.syncari.core.service.FeatureService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;
import java.util.Map;

@Slf4j
public class UpdateAdvancedDedupeConfigFMPredicates {

    @ChangeSet(order = "001", id = "updateMappingAdvanceDedupeConfigPredicate", author = "rohit", runAlways = true)
    public void updateMappingAdvanceDedupeConfigPredicate(MongoTemplate template) {
        boolean dryRunMode = Boolean.parseBoolean(System.getProperty("dryRun"));
        MappingNodeRepo mappingNodeRepo = MigrationContext.getMappingNodeRepo();
        List<MappingNode> fieldMergeMappingNodes = mappingNodeRepo.findAllContainsDedupeAndFieldMergePolicies();
        fieldMergeMappingNodes.forEach(node -> {
            boolean isNodeChanged = false;
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
                                        if (operator.equalsIgnoreCase("firstMatchingValue")){
                                            Object right = ((Map)p).getOrDefault("right","");
                                            if (null != right){
                                                Object value = ((Map)right).getOrDefault("value","");
                                                if (value instanceof List){
                                                    log.info("Right side value is list, we need to change this to map {}", value);
                                                    if (!dryRunMode){
                                                        ((Map)p).put("right",Map.of("value",Map.of("multivaluetext", value, "retainfields", List.of()),"type", "literal"));
                                                        isNodeChanged = true;
                                                    }
                                                }
                                            }
                                        }
                                        if ( (operator.equalsIgnoreCase("latest_with_value")) || (operator.equalsIgnoreCase("latest_created_with_value")) ||
                                                (operator.equalsIgnoreCase("oldest_created_with_value")) || (operator.equalsIgnoreCase("earliest_with_value"))||
                                                (operator.equalsIgnoreCase("max")) || (operator.equalsIgnoreCase("min"))){
                                            Object right = ((Map)p).getOrDefault("right",null);
                                            if (null == right){
                                                log.info("Right side is null, we need to change this to map with empty value for key retainfields");
                                                if (!dryRunMode){
                                                    ((Map)p).put("right",Map.of( "value",Map.of("retainfields", List.of()), "type", "literal"));
                                                    isNodeChanged = true;
                                                }
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

            if (!dryRunMode && isNodeChanged){
                log.info("Updating existing Mapping Node to new Mapping node with updated predicates {}", node);
                mappingNodeRepo.save(node);
            }else{
                log.info("Not Updating existing Mapping Node to new Mapping node with updated or may be not updated predicates");
            }
        });
    }
}
