package com.syncari.core.service;

import static com.syncari.utils.I18n.i18n;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.syncari.core.Features;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.syncari.core.datatype.PasswordType;
import com.syncari.core.functions.ApexAnalytixFunctionsSeed;
import com.syncari.core.model.AbstractActionConfig;
import com.syncari.core.model.ActionDefinition;
import com.syncari.core.model.DataQualityRule;
import com.syncari.core.model.FunctionConfiguration;
import com.syncari.core.model.GroupNodeConfig;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.MappingNode;
import com.syncari.core.model.SimpleFunctionNodeConfig;
import com.syncari.core.model.util.MappingNodeType;
import com.syncari.core.model.versioning.Diff;
import com.syncari.core.model.versioning.DiffDetails;
import com.syncari.core.model.versioning.DiffType;
import com.syncari.core.pipeline.DiffInfoContext;
import com.syncari.core.pipeline.DiffInfoFactory;
import com.syncari.utils.Pair;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class MappingGraphDiffHelper {
	private static String MASK = "*****";
	@Autowired
    private MappingGraphService mappingGraphService;
	@Autowired
    private DiffInfoFactory diffInfoFactory;
	@Autowired
    ActionService actionService;
	@Autowired
    DataQualityService dataQualityService;
	@Autowired
	FeatureService featureService;
	
	private List<String> excludeList = List.of("targetId", "configId", "definition", "graphVersion", "configuration", "childNodeIds");
	
	public List<Diff> diffGraphs(Optional<MappingGraph> v1Graph, Optional<MappingGraph> v2Graph) {
		var diffs = new ArrayList<Diff>();
		Map<String, MappingNode> v1Nodes = new HashMap<String, MappingNode>();
		if(v1Graph.isPresent()) {
			v1Nodes.putAll(getNodeMap(mappingGraphService.findNodesByGraphId(v1Graph.get().getId())));
			if(log.isDebugEnabled()) {
				log.debug("Diff left(v1) graph {}  nodes {}", v1Graph.get().getName() + "(" + v1Graph.get().getId() + ")", v1Nodes.values().stream().map(n -> n.getName() + "(" + n.getId() + ")").collect(Collectors.toList()));
			}
		}
		Map<String, MappingNode> v2Nodes = new HashMap<String, MappingNode>();
		if(v2Graph.isPresent()) {
			v2Nodes.putAll(getNodeMap(mappingGraphService.findNodesByGraphId(v2Graph.get().getId())));
			if(log.isDebugEnabled()) {
				log.debug("Diff right(v2) graph {}  nodes {}", v2Graph.get().getName() + "(" + v2Graph.get().getId() + ")", v2Nodes.values().stream().map(n -> n.getName() + "(" + n.getId() + ")").collect(Collectors.toList()));
			}
		}
		
		var removedNodeIds = v1Nodes.keySet().stream().filter(v1Node -> !v2Nodes.keySet().contains(v1Node)).collect(Collectors.toSet());
		var addedNodeIds = v2Nodes.keySet().stream().filter(v2Node -> !v1Nodes.keySet().contains(v2Node)).collect(Collectors.toSet());
		var modifiedNodeIds = v1Nodes.keySet().stream().filter(v2Nodes.keySet()::contains).collect(Collectors.toSet());
		
		removedNodeIds.stream().forEach(originalId -> {
			var node = v1Nodes.get(originalId);
			diffs.add(Diff.builder()
					.op(DiffType.remove)
					.nodeType(getNodeTypeString(node.getType()))
					.itemName(getNodeName(node))
					.displayName(node.getName())
					.values(diffNode(node, null)).build());
		});
		addedNodeIds.stream().forEach(originalId -> {
			var node = v2Nodes.get(originalId);
			diffs.add(Diff.builder()
					.op(DiffType.add)
					.nodeType(getNodeTypeString(node.getType()))
					.itemName(getNodeName(node))
					.displayName(node.getName())
					.values(diffNode(null, node)).build());
		});
		modifiedNodeIds.stream().forEach(originalId -> {
			var node1 = v1Nodes.get(originalId);
			var node2 = v2Nodes.get(originalId);
			var nodeDiff = diffNode(node1, node2);
			if(CollectionUtils.isNotEmpty(nodeDiff)) {
				diffs.add(Diff.builder()
						.op(DiffType.modified)
						.nodeType(getNodeTypeString(node1.getType()))
						.itemName(getNodeName(node1))
						.displayName(node2.getName())
						.values(nodeDiff).build());
			}
		});
		addDataQualityDiffs(v1Graph, v2Graph, diffs);
		return diffs;
	}

	private void addDataQualityDiffs(Optional<MappingGraph> v1Graph, Optional<MappingGraph> v2Graph, List<Diff> diffs) {
		if (!featureService.isEnabled(Features.DfiV2Provisioning)) {
			return;
		}

		var v1DqRules = v1Graph.isPresent() ? getDataQualityMap(mappingGraphService.findDataQualityRulesByGraphId(v1Graph.get().getId())) : new HashMap<String, DataQualityRule>();
		var v2DqRules = v2Graph.isPresent() ? getDataQualityMap(mappingGraphService.findDataQualityRulesByGraphId(v2Graph.get().getId())) : new HashMap<String, DataQualityRule>();

		var removedDqRuleIds = v1DqRules.keySet().stream().filter(v1DqRule -> !v2DqRules.containsKey(v1DqRule)).collect(Collectors.toSet());
		var addedDqRuleIds = v2DqRules.keySet().stream().filter(v2DqRule -> !v1DqRules.containsKey(v2DqRule)).collect(Collectors.toSet());
		var modifiedDqRuleIds = v1DqRules.keySet().stream().filter(v2DqRules.keySet()::contains).collect(Collectors.toSet());

		addedDqRuleIds.stream().forEach(originalId -> {
			var dqRule = v2DqRules.get(originalId);
			diffs.add(Diff.builder()
					.op(DiffType.add)
					.nodeType("Data Quality Rule")
					.itemName(dqRule.getName())
					.displayName(dqRule.getName())
					.values(diffDataQualityRule(v1Graph.get(), null, dqRule)).build());
		});
		modifiedDqRuleIds.stream().forEach(originalId -> {
			var dqRule1 = v1DqRules.get(originalId);
			var dqRule2 = v2DqRules.get(originalId);
			var dqRuleDiff = diffDataQualityRule(v1Graph.get(), dqRule1, dqRule2);
			if(CollectionUtils.isNotEmpty(dqRuleDiff)) {
				diffs.add(Diff.builder()
						.op(DiffType.modified)
						.nodeType("Data Quality Rule")
						.itemName(dqRule1.getName())
						.displayName(dqRule2.getName())
						.values(dqRuleDiff).build());
			}
		});
		removedDqRuleIds.stream().forEach(originalId -> {
			var dqRule = v1DqRules.get(originalId);
			diffs.add(Diff.builder()
					.op(DiffType.remove)
					.nodeType("Data Quality Rule")
					.itemName(dqRule.getName())
					.displayName(dqRule.getName())
					.values(diffDataQualityRule(v1Graph.get(), dqRule, null)).build());
		});
	}

	private List<DiffDetails> diffDataQualityRule(MappingGraph graph, DataQualityRule rule1, DataQualityRule rule2) {
		// Rule added
		if (rule1 == null) {
			List<DiffDetails> detailsList = new ArrayList<DiffDetails>();
			// Name
			detailsList.add(DiffDetails.builder().id(rule2.getId()).label("Name")
					.value(rule2.getName()).build());
			// Scope
			// detailsList.add(DiffDetails.builder().id(rule2.getId()).label("Scope")
			// 		.value(dataQualityService.getScopeById(graph.getTargetId(), rule2.getScope()).get().get("label")).build());
			// Category
			detailsList.add(DiffDetails.builder().id(rule2.getId()).label("Category")
					.value(dataQualityService.getCategoryById(rule2.getCategory()).getName()).build());
			// Policy
			detailsList.add(DiffDetails.builder().id(rule2.getId()).label("Policy")
					.value(dataQualityService.getPolicyById(rule2.getPolicy()).get().get("label")).build());

			return detailsList;
		}

		if (rule2 != null && rule1 != null) {
			List<DiffDetails> detailsList = new ArrayList<DiffDetails>();

			// Name diff
			if (!rule1.getName().equalsIgnoreCase(rule2.getName())) {
				detailsList.add(DiffDetails.builder().id(rule2.getId()).label("Name")
					.previousValue(rule1.getName())
					.value(rule2.getName()).build());
			}
			// Scope diff
			// if (!rule1.getScope().equalsIgnoreCase(rule2.getScope())) {
			// 	detailsList.add(DiffDetails.builder().id(rule2.getId()).label("Scope")
			// 		.previousValue(dataQualityService.getScopeById(graph.getTargetId(), rule2.getScope()).get().get("label"))
			// 		.value(dataQualityService.getScopeById(graph.getTargetId(), rule2.getScope()).get().get("label")).build());
			// }
			// Category diff
			if (!rule1.getCategory().equalsIgnoreCase(rule2.getCategory())) {
				detailsList.add(DiffDetails.builder().id(rule2.getId()).label("Category")
					.previousValue(dataQualityService.getCategoryById(rule1.getPolicy()).getName())
					.value(dataQualityService.getCategoryById(rule2.getPolicy()).getName()).build());
			}
			// Policy diff
			if (!rule1.getPolicy().equalsIgnoreCase(rule2.getPolicy())) {
				detailsList.add(DiffDetails.builder().id(rule2.getId()).label("Policy")
					.previousValue(dataQualityService.getPolicyById(rule2.getPolicy()).get().get("label"))
					.value(dataQualityService.getPolicyById(rule2.getPolicy()).get().get("label")).build());
			}
			return detailsList;
		}

		// Rule deleted
		if (rule2 == null) {
			List<DiffDetails> detailsList = new ArrayList<DiffDetails>();
			detailsList.add(DiffDetails.builder().id(rule1.getId()).label(i18n("name"))
					.previousValue(rule1.getName()).build());
			// Name
			detailsList.add(DiffDetails.builder().id(rule1.getId()).label("Name")
					.previousValue(rule1.getName()).build());
			// Scope
			// detailsList.add(DiffDetails.builder().id(rule1.getId()).label("Scope")
			// 		.previousValue(dataQualityService.getScopeById(graph.getTargetId(), rule1.getScope()).get().get("label")).build());
			// Category
			detailsList.add(DiffDetails.builder().id(rule1.getId()).label("Category")
					.previousValue(dataQualityService.getCategoryById(rule1.getCategory()).getName()).build());
			// Policy
			detailsList.add(DiffDetails.builder().id(rule1.getId()).label("Policy")
					.previousValue(dataQualityService.getPolicyById(rule1.getPolicy()).get().get("label")).build());
					return detailsList;
		}
		return List.of();
	}

	private Map<String, DataQualityRule> getDataQualityMap(List<DataQualityRule> dqRules) {
		return CollectionUtils.isEmpty(dqRules) ? Map.of()
				: dqRules.stream()
						.collect(Collectors.toMap(
								dqRule -> dqRule.getOriginalId() != null ? dqRule.getOriginalId() : dqRule.getId(),
								Function.identity()));
	}



	private List<DiffDetails> diffNode(MappingNode left, MappingNode right) {
		if (left == null && right == null)
			return List.of();
		if (right == null) {
			List<DiffDetails> detailsList = new ArrayList<DiffDetails>();
			if (left.getConfiguration() != null && left.getConfiguration().getConfigMap() != null) {
				left.getConfiguration().getConfigMap().entrySet().stream()
						.filter(k -> !excludeList.contains(k.getKey())).forEach(e -> {
							var values = getConfigValue(left, e.getKey());
							if (CollectionUtils.isNotEmpty(values)) {
								values.forEach(v -> {
									var val = isPassword(left, v.x) ? MASK : v.y;
									detailsList.add(DiffDetails.builder().id(v.x).label(getConfigName(left, v.x))
											.renderHtml(getRenderHtml(left, v.x))
											.previousValue(val).build());
								});

							}
						});
			}
			detailsList.add(DiffDetails.builder().id(left.getId()).label(i18n("display_label"))
					.previousValue(left.getName()).build());
			return detailsList;
		}
		if (left == null) {
			List<DiffDetails> detailsList = new ArrayList<DiffDetails>();
			if (right.getConfiguration() != null && right.getConfiguration().getConfigMap() != null) {
				right.getConfiguration().getConfigMap().entrySet().stream()
						.filter(k -> !excludeList.contains(k.getKey())).forEach(e -> {
							var values = getConfigValue(right, e.getKey());
							if (CollectionUtils.isNotEmpty(values)) {
								values.forEach(v -> {
									var val = isPassword(right, v.x) ? MASK : v.y;
									detailsList.add(DiffDetails.builder().id(v.x).label(getConfigName(right, v.x))
											.renderHtml(getRenderHtml(right, v.x))
											.value(val).build());
								});
							}
						});
			}
			detailsList.add(DiffDetails.builder().id(right.getId()).label(i18n("display_label"))
					.value(right.getName()).build());
			return detailsList;
		}
		if(left.getConfiguration() != null 
				&& left.getConfiguration().getConfigMap() != null
				&& right.getConfiguration() != null 
				&& right.getConfiguration().getConfigMap() != null) {
			var diffList = new ArrayList<DiffDetails>();
			var leftConfigMap = left.getConfiguration().getConfigMap();
			var rightConfigMap = right.getConfiguration().getConfigMap();
			var allKeys = new HashSet<String>(leftConfigMap.keySet());
			allKeys.addAll(rightConfigMap.keySet());
			allKeys.stream()
			.filter(k -> !excludeList.contains(k))
			.forEach(key -> {
				var leftVal = leftConfigMap.get(key);
				var rightVal = rightConfigMap.get(key);
						if (leftVal != null && rightVal != null) {
							if (!String.valueOf(leftVal).equals(String.valueOf(rightVal))) {
								var leftValues = getConfigValue(left, key);
								var rightValues = getConfigValue(right, key);
								var updatedKeys = new LinkedHashSet<String>();
								if (leftValues != null) {
									updatedKeys.addAll(leftValues.stream().map(p -> p.x).collect(Collectors.toSet()));
								}
								if (rightValues != null) {
									updatedKeys.addAll(rightValues.stream().map(p -> p.x).collect(Collectors.toSet()));
								}
								updatedKeys.forEach(newKey -> {
									String newLeftVal = leftValues.stream().filter(p -> newKey.equals(p.x)).findFirst()
											.orElse(Pair.of(null, null)).y;
									String newRightVal = rightValues.stream().filter(p -> newKey.equals(p.x))
											.findFirst().orElse(Pair.of(null, null)).y;
									if (!String.valueOf(newLeftVal).equals(String.valueOf(newRightVal))) {
									  newLeftVal = isPassword(left, newKey) ? MASK : newLeftVal;
									  newRightVal = isPassword(right, newKey) ? MASK : newRightVal;
									  diffList.add(DiffDetails.builder().id(newKey).label(getConfigName(left, newKey))
									      .renderHtml(getRenderHtml(left, newKey))
									      .previousValue(newLeftVal).value(newRightVal).build());
									}
								});
							}
				} else if(leftVal != null) {
					var leftValues = getConfigValue(left, key);
					if(leftValues != null) {
						leftValues.forEach(lp -> {
							var val = isPassword(left, lp.x) ? MASK : lp.y;
							diffList.add(DiffDetails
									.builder()
									.id(lp.x)
									.label(getConfigName(left, lp.x))
									.renderHtml(getRenderHtml(left, lp.x))
									.previousValue(val)
									.build());
						});
					}
				} else if(rightVal != null) {
					var rightValues = getConfigValue(right, key);
					if(rightValues != null) {
						rightValues.forEach(rp -> {
							var val = isPassword(left, rp.x) ? MASK : rp.y;
							diffList.add(DiffDetails
									.builder()
									.id(rp.x)
									.label(getConfigName(right, rp.x))
									.renderHtml(getRenderHtml(right, rp.x))
									.value(val)
									.build());
						});
					}
				}
			});
			if (!Objects.equals(left.getName(), right.getName())) {
				diffList.add(DiffDetails.builder().id(right.getId()).label(i18n("display_label"))
						.previousValue(left.getName()).value(right.getName()).build());
			}
			return diffList;
		}
		return List.of();
	}
	
	private Map<String, MappingNode> getNodeMap(List<MappingNode> nodes) {
		return CollectionUtils.isEmpty(nodes) ? Map.of()
				: nodes.stream()
						.collect(Collectors.toMap(
								node -> node.getOriginalId() != null ? node.getOriginalId() : node.getId(),
								Function.identity()));
	}
	
	public boolean hasDiff(MappingGraph v1, MappingGraph v2) {
		var v1Nodes = getNodeMap(mappingGraphService.findNodesByGraphId(v1.getId()));
		var v2Nodes = getNodeMap(mappingGraphService.findNodesByGraphId(v2.getId()));
		
		var removedNodeCount = v1Nodes.keySet().stream().filter(v1Node -> !v2Nodes.keySet().contains(v1Node)).count();
		if(removedNodeCount > 0) {
			return true;
		}
		var addedNodeCount = v2Nodes.keySet().stream().filter(v2Node -> !v1Nodes.keySet().contains(v2Node)).count();
		if(addedNodeCount > 0) {
			return true;
		}
		var modifiedNodeIds = v1Nodes.keySet().stream().filter(v2Nodes.keySet()::contains).collect(Collectors.toSet());
		for(var originalId: modifiedNodeIds) {
			var node1 = v1Nodes.get(originalId);
			var node2 = v2Nodes.get(originalId);
			var nodeDiff = diffNode(node1, node2);
			if(CollectionUtils.isNotEmpty(nodeDiff)) {
				return true;
			}
		}
		return false;
	}
	
	private String getNodeTypeString(MappingNodeType type) {
		if (type == null) {
			return "";
		}
		switch (type) {
		case ACTION:
			return "Action";
		case FUNCTION:
			return "Function";
		case PREDICATE:
			return "Function";
		case CORE_ENTITY:
			return "Syncari";
		case CONNECTOR_ENTITY:
			return "Connector";
		case ENTITY_SINK:
			return "Destination";
		case ENTITY_SOURCE:
			return "Source";
		case ATTRIBUTE_SINK:
			return "Destination";
		case ATTRIBUTE_SOURCE:
			return "Source";
		case CORE_ATTRIBUTE:
			return "Syncari";
		case GROUP:
			return "Group";
		default:
			return type.name();
		}
	}
	
	private String getNodeName(MappingNode node) {
		if(node == null) {
			return "";
		}
		var config = node.getConfiguration();
		if(config instanceof SimpleFunctionNodeConfig) {
			var fCall = ((SimpleFunctionNodeConfig)config).getFunctionCall();
			if(fCall != null) {
				var fDef = fCall.getFunctionDefinition();
				if(fDef != null) {
					return i18n(fDef.getDisplayName() != null ? fDef.getDisplayName(): node.getApiName());
				}
			}
		} else if (config instanceof AbstractActionConfig) {
			var fCall  = actionService.getAction(node.getApiName()).get();
			if (fCall != null) {
				return i18n(fCall.getDisplayName() != null ? fCall.getDisplayName() : node.getApiName());
			}

		} else if (config instanceof GroupNodeConfig) {
			return "Group";
		}
		return i18n(node.getApiName());
	}
	
	private boolean getRenderHtml(MappingNode node, String configProperty) {
		if(node == null) {
			return false;
		}
		var config = node.getConfiguration();
		if(config instanceof SimpleFunctionNodeConfig) {
			var fCall = ((SimpleFunctionNodeConfig)config).getFunctionCall();
			if(fCall != null && fCall.getFunctionDefinition() != null) {
				FunctionConfiguration fConfig = fCall.getFunctionDefinition().getConfiguration().stream().filter(c -> c.getName().equals(configProperty)).findFirst().orElse(null);
				if(fConfig != null && fConfig.getAdditionalProperties() != null) {
					return BooleanUtils.toBoolean(String.valueOf(fConfig.getAdditionalProperties().getOrDefault("renderHtml", false)));
				}
				
			}
		} else if (config instanceof AbstractActionConfig) {
			var fCall  = actionService.getAction(node.getApiName()).get();
			if(fCall != null && fCall.getConfiguration() != null) {
				FunctionConfiguration fConfig = fCall.getConfiguration().stream().filter(c -> c.getName().equals(configProperty)).findFirst().orElse(null);
				if(fConfig != null && fConfig.getAdditionalProperties() != null) {
					return BooleanUtils.toBoolean(String.valueOf(fConfig.getAdditionalProperties().getOrDefault("renderHtml", false)));
				}
				
			}

		} 
		return false;
	}
	
	private String getConfigName(MappingNode node, String key) {
		if (node == null || key == null) {
			return "";
		}
		String count = "";
		if(key.contains("@@@")) {
			 String[] keyParts = key.split("@@@");
			 if(keyParts.length > 0) {
				 key = keyParts[0];
			 }
			 if(keyParts.length > 1) {
				 count = " " + keyParts[1];
			 }
		}
		var config = node.getConfiguration();
		if (config instanceof SimpleFunctionNodeConfig) {
			var fCall = ((SimpleFunctionNodeConfig) config).getFunctionCall();
			if (fCall != null) {
				var fDef = fCall.getFunctionDefinition();
				if (fDef != null) {
					Map<String, String> configNameLabelMap = fDef.getConfiguration().stream()
							.filter(c -> c.getName() != null && c.getLabel() != null)
							.collect(Collectors.toMap(c -> c.getName(), c -> c.getLabel()));
					return i18n(configNameLabelMap.getOrDefault(key, key)) + count;
				}
			}
		} else if (config instanceof AbstractActionConfig) {
			ActionDefinition actionDef = actionService.getAction(node.getApiName()).get();
			Map<String, String> configNameLabelMap = actionDef.getConfiguration().stream()
					.collect(Collectors.toMap(c -> c.getName(), c -> c.getLabel()));
			return i18n(configNameLabelMap.getOrDefault(key, key)) + count;
		}
		return i18n(key) + count;
	}
	
	private boolean isPassword(MappingNode node, String key) {
		if (node == null || key == null) {
			return false;
		}
		var config = node.getConfiguration();
		if (config instanceof SimpleFunctionNodeConfig) {
			var fCall = ((SimpleFunctionNodeConfig) config).getFunctionCall();
			if (fCall != null) {
				var fDef = fCall.getFunctionDefinition();
				if (fDef != null) {
					if(ApexAnalytixFunctionsSeed.APEX_ANALYTIX_COMPANY_ENRICH.equals(fDef.getName())) {
						return "serviceId".equals(key);
					} else {
						var conf = fDef.getConfiguration().stream()
								.filter(c -> c.getName() != null && c.getName().equals(key))
								.findFirst();
						if(conf.isPresent() && conf.get().getDatatype() != null) {
							return PasswordType.NAME.equals(conf.get().getDatatype().getName());
						}
					}
				}
			}
		}
		return false;
	}
	
	private List<Pair<String, String>> getConfigValue(MappingNode node, String key) {
		if(node == null || key == null) {
			return List.of();
		}
		return diffInfoFactory.getDiffInfoService(node).toUserFriendlyValue(new DiffInfoContext().setCurrentNode(node),
				key);
	}
}
