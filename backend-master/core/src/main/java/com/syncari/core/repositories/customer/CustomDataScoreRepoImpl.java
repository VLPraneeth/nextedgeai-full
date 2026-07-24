package com.syncari.core.repositories.customer;

import static com.mongodb.client.model.Filters.ne;
import static org.springframework.data.mongodb.core.query.Criteria.where;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.apache.commons.collections4.MapUtils;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.BsonField;
import com.syncari.core.model.ConditionAssignment;
import com.syncari.core.model.EntityDataScoreSnapshot;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.FieldDataScoreSnapshot;
import com.syncari.core.model.RuleAssignment;
import com.syncari.core.model.misc.EntityScoreWrapper;
import com.syncari.core.service.DfiRuleAssignmentService;
import com.syncari.core.service.SchemaService;
import com.syncari.utils.DateUtil;
import com.syncari.utils.Timer;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Repository
public class CustomDataScoreRepoImpl implements CustomDataScoreRepo {
    private static final String ENTITY_SCORE = "entity_score";
    private static final String AVERAGE_SCORE = "averageScore";
    @Autowired
    private MongoTemplate customerMongoTemplate;
    @Autowired
    DateUtil dateUtils;
    @Autowired
    SchemaService schemaService;
    @Autowired
    DfiRuleAssignmentService dfiRuleAssignmentService;

    public EntityScoreWrapper getAvgScores(EntityDefinition entity, Optional<Integer> numberOfRecords, Optional<String> computedDayString) {
        EntityScoreWrapper response = new EntityScoreWrapper();
        String computedDay = getLatest(entity.getId(), computedDayString);
        if(computedDay == null) return response;
        Map<String, List<RuleAssignment>> rulesByField = dfiRuleAssignmentService.getRulesForEntityByField(entity.getApiName());
        Query query = new Query().addCriteria(where("entityDefId").is(entity.getId()))
                .addCriteria(where("computedDay").is(computedDay))
                .with(Sort.by(AVERAGE_SCORE).ascending());

        List<FieldDataScoreSnapshot> fieldScores = customerMongoTemplate.find(query, FieldDataScoreSnapshot.class);
        Map<String, FieldDataScoreSnapshot> fieldScoresByKey = Maps.newLinkedHashMap();
        for (FieldDataScoreSnapshot fieldScore: fieldScores) {
            String key = fieldScore.getFieldName() + "_" + fieldScore.getRuleId() + "_" + fieldScore.getConditionName();
            fieldScoresByKey.put(key, fieldScore);
        }

        List<FieldDataScoreSnapshot> finalFieldScores = Lists.newArrayList();
        // We drive the finalFieldScores by the current version of the DFI rules defined for this entity.
        // This has two advantages,
        // 1. Field/Rules deleted will show effect immediately in the fields score widget of the DFI dashboard for the entity.
        // 2. New rules/fields added will show up in the fields score widget of the DFI dashboard for the entity with empty scores
        // even if the scores are not computed yet.
        for (Entry<String, List<RuleAssignment>> entry : rulesByField.entrySet()) {
            for (RuleAssignment rule : entry.getValue()) {
                for (ConditionAssignment condition : rule.getConditions()) {
                    String key = entry.getKey() + "_" + rule.getId() + "_" + condition.getName();
                    if (fieldScoresByKey.containsKey(key)) {
                        finalFieldScores.add(fieldScoresByKey.get(key));
                    } else {
                        // If a new field was added and the scores are not yet computed, still add them here to display in the UI.
                        // TODO: null score does not work because the arcade layer DTOs fail with NPE. We need to fix this.
                        finalFieldScores.add(new FieldDataScoreSnapshot(entity.getId(), entry.getKey(), rule.getId(), rule.getName(), 
                            condition.getName(), 0, Instant.now()));
                    }
                }
            }
        }

        Collections.sort(finalFieldScores, new Comparator<FieldDataScoreSnapshot>() {
            @Override
            public int compare(FieldDataScoreSnapshot f1, FieldDataScoreSnapshot f2) {
                return Integer.valueOf(f1.getAverageScore()).compareTo(Integer.valueOf(f2.getAverageScore()));
            }
        });
        finalFieldScores.sort(Comparator.comparing(FieldDataScoreSnapshot::getAverageScore));
        if(!numberOfRecords.isPresent()) {
            response.setFieldScores(finalFieldScores);
        } else {
            response.setFieldScores(finalFieldScores.subList(0, Math.min(finalFieldScores.size(), numberOfRecords.get())));
        }
        List<EntityDataScoreSnapshot> entityScore = customerMongoTemplate.find(query, EntityDataScoreSnapshot.class);
        response.setEntityScore(entityScore.isEmpty() ? new EntityDataScoreSnapshot() : entityScore.get(0));
        return response;
    }

    public EntityScoreWrapper computeAvgScores(EntityDefinition entity, String collectionName, Optional<Integer> numberOfFields) {
        Timer check = new Timer(2000, String.format("computeAvgScores on entity %s ", collectionName), log);
        EntityScoreWrapper response = new EntityScoreWrapper();
        List<FieldDataScoreSnapshot> results = new ArrayList<FieldDataScoreSnapshot>();
        Map<String, List<RuleAssignment>> rulesByField = dfiRuleAssignmentService.getRulesForEntityByField(entity.getApiName());
        if (MapUtils.isEmpty(rulesByField))
            return response;
        Map<String, Integer> scores = getAverageScoresByRule(collectionName, rulesByField);
        if(scores.containsKey(ENTITY_SCORE)) {
            response.setEntityScore(new EntityDataScoreSnapshot(entity.getId(), scores.get(ENTITY_SCORE), 0, Instant.now()));
        }
        for (Entry<String, List<RuleAssignment>> entry : rulesByField.entrySet()) {
            for (RuleAssignment rule : entry.getValue()) {
                for (ConditionAssignment condition : rule.getConditions()) {
                    String key = entry.getKey() + "_" + condition.getName();
                    if(ENTITY_SCORE.equalsIgnoreCase(key)) continue;
                    Integer score = scores.get(key);
                    if (score == null) continue;
                    results.add(new FieldDataScoreSnapshot(entity.getId(), entry.getKey(), rule.getId(), rule.getName(), 
                        condition.getName(), score, Instant.now()));
                }
            }
        }
        Collections.sort(results, new Comparator<FieldDataScoreSnapshot>() {
            @Override
            public int compare(FieldDataScoreSnapshot f1, FieldDataScoreSnapshot f2) {
                return Integer.valueOf(f1.getAverageScore()).compareTo(Integer.valueOf(f2.getAverageScore()));
            }
        });
        results.sort(Comparator.comparing(FieldDataScoreSnapshot::getAverageScore));
        if(numberOfFields.isEmpty()) {
            response.setFieldScores(results);
        } else {
            response.setFieldScores(results.subList(0, Math.min(results.size(), numberOfFields.get())));
        }
        log.info("computeAvgScores on entity {} took {} ms.", collectionName, check.getTimeTakenUntilNow());
        check.close();
        return response;
    }

    public Map<String, Integer> getDfiTrend(String entityId, int rangeInDays) {
        Map<String, Integer> result = new LinkedHashMap<>();
        Query query = new Query().addCriteria(where("entityDefId").is(entityId))
                .addCriteria(where("computedDay").gte(dateUtils.formatDate(Instant.now().minus(rangeInDays, ChronoUnit.DAYS), DateUtil.dateOnlyFormat2)))
                .limit(rangeInDays).with(Sort.by(Sort.Direction.ASC,"computedDay"));
        List<Document> list = customerMongoTemplate.getCollection("entityDataScoreSnapshot")
                .find(query.getQueryObject()).into(new ArrayList<>());
        list.stream().forEach(e -> {
            result.put(e.get("computedDay").toString(),
                    e.get("score") == null ? 0 : Integer.valueOf(e.get("score").toString()));
        });
        return result;
    }

    public int getAvgSourceScore(String collectionName) {
        AggregateIterable<org.bson.Document> aggregate = customerMongoTemplate.getCollection(collectionName)
                .aggregate(Arrays.asList(Aggregates.group("_id",
                        new BsonField(AVERAGE_SCORE, new Document("$avg", "$syncariScore.sourceScore")))));
        Document first = aggregate.first();
        if (first == null || first.getDouble(AVERAGE_SCORE) == null)
            return 0;
        return (int) Math.round(first.getDouble(AVERAGE_SCORE));
    }

    public Map<String, Integer> getAverageScoresByRule(String collectionName, Map<String, List<RuleAssignment>> rulesByField) {
        // First find the number of rules we may need to create the BsonField array below.
        Long ruleCount = rulesByField.entrySet().stream()
            .collect(Collectors.summingLong(x -> x.getValue().stream().map(RuleAssignment::getConditions).flatMap(Set::stream).count()));
        Map<String, Integer> result = new HashMap<>();
        BsonField[] aggregates = new BsonField[ruleCount.intValue() + 1];
        int i = 0;
        for (Entry<String, List<RuleAssignment>> entry : rulesByField.entrySet()) {
            for (RuleAssignment rule : entry.getValue()) {
                for (ConditionAssignment condition : rule.getConditions()) {
                    String key = entry.getKey() + "_" + condition.getName();
                    aggregates[i] = new BsonField(key,
                            new Document("$avg", "$syncariScore.fieldScores." + entry.getKey() + ".byRuleScores." + condition.getName()));
                    i++; 
                }   
            }
        }
        aggregates[i] = new BsonField(ENTITY_SCORE, new Document("$avg", "$syncariScore.recordScore"));
        // get avg score for field grouped by ruleName
        AggregateIterable<org.bson.Document> aggregate = customerMongoTemplate.getCollection(collectionName)
                .aggregate(Arrays.asList(Aggregates.match(ne("isDeleted", true)), Aggregates.group("_id", aggregates)));
        Document first = aggregate.first();
        if (first == null) return result;
        Iterator<String> iterator = first.keySet().iterator();
        while (iterator.hasNext()) {
            String key = iterator.next();
            if(!"_id".equalsIgnoreCase(key)) {
                int value = (first.getDouble(key) == null) ? 0 : (int) Math.round(first.getDouble(key));
                result.put(key, value);
            }
        }
        return result;
    }

    private String getLatest(String entityDefId, Optional<String> computedDayString) {
        Query query1 = new Query().addCriteria(where("entityDefId").is(entityDefId))
                .with(Sort.by("computedDay").descending()).limit(1);
        if (!computedDayString.isEmpty()) {
            query1 = new Query().addCriteria(where("entityDefId").is(entityDefId))
                .addCriteria(where("computedDay").lte(computedDayString.get()))
                .with(Sort.by("computedDay").descending()).limit(1);
        }
        EntityDataScoreSnapshot result = customerMongoTemplate.findOne(query1, EntityDataScoreSnapshot.class);
        return result == null ? null : result.getComputedDay();
    }
}

@Data
class AvgScore {
    Integer averageScore;
    String _id;
}
