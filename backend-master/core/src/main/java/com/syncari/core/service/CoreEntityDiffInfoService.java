package com.syncari.core.service;

import static com.syncari.utils.I18n.i18n;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.beanutils.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.FieldMergePolicyParser;
import com.syncari.core.model.SelectWinnerPredicateParser;
import com.syncari.core.model.WinnerOverridePolicy;
import com.syncari.core.model.WinnerValueSelectionPolicy;
import com.syncari.core.pipeline.DiffInfoContext;
import com.syncari.core.pipeline.DiffInfoExpressionVisitor;
import com.syncari.core.pipeline.expression.Expression;
import com.syncari.core.pipeline.expression.PredicateParser;
import com.syncari.core.pipeline.expression.dedupe.LatestCreatedRecordExpression;
import com.syncari.core.pipeline.expression.dedupe.LatestUpdatedRecordExpression;
import com.syncari.core.pipeline.expression.dedupe.MostCompleteRecordExpression;
import com.syncari.core.pipeline.expression.dedupe.OldestCreatedRecordExpression;
import com.syncari.core.pipeline.expression.dedupe.OldestUpdatedRecordExpression;
import com.syncari.core.repositories.customer.MappingNodeRepo;
import com.syncari.utils.Pair;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class CoreEntityDiffInfoService implements DiffInfoService {
	@Autowired
	ConnectorService connectorService;
	@Autowired
	private SchemaService schemaService;
	@Autowired
	private MappingNodeRepo nodeRepo;
	@Override
	public List<Pair<String, String>> toUserFriendlyValue(DiffInfoContext context, String configProperty) {
		if(context != null && context.getCurrentNode() != null) {
			List<Pair<String, String>> response = new ArrayList<Pair<String,String>>();
			var config = context.getCurrentNode().getConfiguration().getConfigMap();
			if("selectWinner".equals(configProperty)) {
				Map<String, Object> selectWinnerConfigMap = (Map<String, Object>) config.getOrDefault("selectWinner", Map.of());
		        List<Map<String, Object>> compositeValues = (List<Map<String, Object>>) selectWinnerConfigMap.getOrDefault("compositeValues", List.of());
		        List<String> userFriendly = new ArrayList<>();
		        compositeValues.forEach(p -> {
		            Map<String, Object> findDupePredicate = new HashMap<>((Map<String, Object>) p.getOrDefault("winnerSelectionPredicate", Map.of()));
		            Map<String, Object> predicate = (Map<String, Object>) findDupePredicate.getOrDefault("value", Map.of());
		            userFriendly.add(translatePredicate(context, predicate, new SelectWinnerPredicateParser()));
		        });
		        response.add(Pair.of(configProperty, String.join(", ", userFriendly)));
				return response;
			} else if("findDupes".equals(configProperty)) {
				Map<String, Object> selectWinnerConfigMap = (Map<String, Object>) config.getOrDefault("findDupes", Map.of());
		        List<Map<String, Object>> compositeValues = (List<Map<String, Object>>) selectWinnerConfigMap.getOrDefault("compositeValues", List.of());
		        List<String> userFriendly = new ArrayList<>();
		        compositeValues.forEach(p -> {
		            Map<String, Object> findDupePredicate = new HashMap<>((Map<String, Object>) p.getOrDefault("findDupesPredicate", Map.of()));
		            Map<String, Object> predicate = (Map<String, Object>) findDupePredicate.getOrDefault("value", Map.of());
		            userFriendly.add(translatePredicate(context, predicate, new PredicateParser()));
		        });
		        response.add(Pair.of(configProperty, String.join(", ", userFriendly)));
		        return response;
			} else if ("defaultMergePolicy".equals(configProperty)) {
				String policy = (String) config.get("defaultMergePolicy");
				if (policy != null) {
					try {
						response.add(Pair.of(configProperty, i18n("winner_selection_policy_"
								+ WinnerValueSelectionPolicy.valueOf(policy).name().toLowerCase())));
						return response;
					} catch (Exception e) {
						// dont do anything. default behavior will take care
					}
				}
			} else if ("defaultOverridePolicy".equals(configProperty)) {
				String policy = (String) config.get("defaultOverridePolicy");
				if (policy != null) {
					try {
						response.add(Pair.of(configProperty, WinnerOverridePolicy.valueOf(policy).label));
						return response;
					} catch (Exception e) {
						// dont do anything. default behavior will take care
					}
				}
			} else if("fieldMergePolicies".equals(configProperty)) {
				Map<String, Object> selectWinnerConfigMap = (Map<String, Object>) config.getOrDefault("fieldMergePolicies", Map.of());
		        List<Map<String, Object>> compositeValues = (List<Map<String, Object>>) selectWinnerConfigMap.getOrDefault("compositeValues", List.of());
		        List<String> userFriendly = new ArrayList<>();
		        compositeValues.forEach(p -> {
		            Map<String, Object> findDupePredicate = new HashMap<>((Map<String, Object>) p.getOrDefault("fieldMergePredicate", Map.of()));
		            Map<String, Object> predicate = (Map<String, Object>) findDupePredicate.getOrDefault("value", Map.of());
		            userFriendly.add(translatePredicate(context, predicate, new FieldMergePolicyParser()));
		        });
		        response.add(Pair.of(configProperty, String.join(", ", userFriendly)));
				return response;
			} else if("entityDefinition".equals(configProperty)) {
				var entity = schemaService.findEntity((String) config.get("entityDefinition"));
				var entityName = entity.orElse(new EntityDefinition()).getDisplayName();
				entityName = entityName == null ? (String) config.get("entityDefinition") : entityName;
				response.add(Pair.of(configProperty, entityName));
				return response;
			}
		}
		return DiffInfoService.super.toUserFriendlyValue(context, configProperty);
	}
	
	private String translatePredicate(DiffInfoContext context, Map<String, Object> predicate, PredicateParser parser) {
		try {
		Expression filterExpression = parser.fromMap(predicate);
			if(filterExpression instanceof OldestCreatedRecordExpression
					|| filterExpression instanceof OldestUpdatedRecordExpression
					|| filterExpression instanceof MostCompleteRecordExpression
					|| filterExpression instanceof LatestUpdatedRecordExpression
					|| filterExpression instanceof LatestCreatedRecordExpression) {
				try {
					return String.format("[%s]", i18n(BeanUtils.getSimpleProperty(filterExpression, "name")));
				} catch (Exception e) {
					log.error("Expression evaluation error ", e);
					return String.valueOf(predicate);
				}
			} else {
		        var evaluator = new DiffInfoExpressionVisitor(schemaService, nodeRepo);
		        filterExpression.accept(evaluator);
		        return evaluator.getValue();
			}
		}catch (Exception e) {
			log.error("Expression contains error ", e);
			return String.format("[Expression contains error (%s): %s]", e.getMessage(), String.valueOf(predicate)); 
		}
	}
}
