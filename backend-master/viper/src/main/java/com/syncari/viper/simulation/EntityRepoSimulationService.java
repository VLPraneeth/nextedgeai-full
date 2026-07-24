package com.syncari.viper.simulation;

import com.syncari.connector.EntityData;
import com.syncari.connector.Operation;
import com.syncari.core.SyncariContext;
import com.syncari.core.event.EventTypes;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.*;
import com.syncari.core.model.misc.DataScoreCard;
import com.syncari.core.model.misc.EntityDataResponse;
import com.syncari.core.model.misc.EntityScoreWrapper;
import com.syncari.core.model.pagination.Page;
import com.syncari.core.model.pagination.PageCursor;
import com.syncari.core.model.util.Status;
import com.syncari.core.pipeline.expression.Expression;
import com.syncari.core.service.EntityRepoService;
import com.syncari.core.utils.LookupCriteriaVisitor;
import com.syncari.core.utils.MongoCriteria;
import org.apache.commons.collections4.IterableUtils;

import java.io.InputStream;
import java.time.Instant;
import java.util.*;

import static com.syncari.utils.I18n.i18n;

public class EntityRepoSimulationService extends EntityRepoService {
    private static final int LIVE_FIELD_SCORE_AGG_THRESHOLD = 10000;

    @Override
    public int getLiveFieldScoreAggThreshold() {
        return LIVE_FIELD_SCORE_AGG_THRESHOLD;
    }
    @Override
    public Map<String, Object> getContactEmailValidationCount() {
        return Map.of();
    }

    @Override
    public Iterable<EntityData> findByIds(String entityId, Set<String> ids) {
        return IterableUtils.emptyIterable();
    }
    @Override
    public Iterable<EntityData> findRecordsByIds(EntityDefinition entity, Set<String> ids) {
        return IterableUtils.emptyIterable();
    }

    @Override
    public Page<EntityData> query(String entityId, Optional<Expression> filter, PageCursor pageInfo,boolean withCount) {
        return new Page<>();
    }

    @Override
    public Batch submitBatchDelete(String entityId, String filter, boolean deleteInEndSystems) {
        Batch batch = new Batch().setStatus(Status.NEW).setEntityId(entityId).setOperation(Operation.delete)
                .setConfig(Map.of("filter", filter, DELETE_IN_END_SYSTEMS, deleteInEndSystems));
        return batch;
    }

    @Override
    public Batch submitBatchPurge(String entityId) {
        Batch batch = new Batch().setStatus(Status.NEW).setEntityId(entityId).setOperation(Operation.purge)
                .setConfig(Map.of());
        return batch;
    }
    @Override
    public Batch submitBatchUpdate(String entityId, String filter, Map<String, Object> values) {
        Batch batch = new Batch().setStatus(Status.NEW).setEntityId(entityId).setOperation(Operation.update);
        batch.setCreatedBy(SyncariContext.getUser().getId());
        return batch;
    }

    @Override
    public Optional<EntityData> getRecordById(EntityDefinition entity, String recordId) {
        return Optional.empty();
    }

    @Override
    public EntityDataResponse update(EntityData record, EntityDefinition def) {
        return new EntityDataResponse();
    }

    @Override
    public void updateValues(EntityDefinition entityDefinition, List<EntityData> updatedValues, boolean changeTimestamp){
        SimulationEntityRepo repo = new SimulationEntityRepo();
        repo.updateValues(entityDefinition, updatedValues);
    }

    @Override
    public void deleteAllForEntity(String entityId) {
        // do nothing
    }

    @Override
    public long deleteAllForEntity(String entityId, Batch batch) {
        return 0l;
    }

    @Override
    public void deleteRecord(String entityName, String recordId, boolean deleteInEnd) {
        // do nothing
    }

    @Override
    public void deleteRecords(String entityName, List<EntityData> toBeDeleted, boolean deleteInEnd) {
        // do nothing
    }

    @Override
    public long deleteRecords(String entityId, Batch batch) {
        return 0l;
    }

    @Override
    public long updateRecords(String entityId, Batch batch) {
        return 0l;
    }

    @Override
    public InputStream getDocumentContents(EntityDefinition def, EntityData ed) {
        return InputStream.nullInputStream();
    }
    @Override
    public long getCount(String entity) {
        return 0l;
    }
    @Override
    public long count(EntityDefinition def,Optional<? extends MongoCriteria> visitor) {
        return 0l;
    }
    @Override
    public long getDeletedCount(String entity) {
        return 0l;
    }
    @Override
    public void computeScore(List<EntityData> entities, String entityApiName,Map<String, List<RuleAssignment>> ruleMap) {
        // nothing
    }
    @Override
    public void computeScore(List<EntityData> entities, String entityApiName) {

    }
    @Override
    public void initializeScore() {

    }
    @Override
    public void initializeScoreForEntity(String entityName) {

    }
    @Override
    public void initializeScoreForEntityById(String entityId) {

    }
    @Override
    public void snapshotScore() {

    }
    @Override
    public boolean isDfiEnabled(EntityDefinition entity) {
        return false;
    }
    @Override
    public int getAvgSourceScore(String entityId) {
        return 0;
    }
    @Override
    public int getOverallScore() {
        return 0;
    }
    @Override
    public int getOverallScore(Instant day) {
        return 0;
    }
    @Override
    public EntityScoreWrapper getAvgScores(EntityDefinition entity, Instant day) {
        return new EntityScoreWrapper();
    }

    @Override
    public EntityScoreWrapper getTop3AvgScores(String entityDefId) {

        return getTopNAvgScores(entityDefId, 3);
    }
    @Override
    public EntityScoreWrapper getTopNAvgScores(String entityDefId, Integer n) {
        return new EntityScoreWrapper();
    }
    @Override
    public Map<String, Integer> getDfiTrend(String entityId, int rangeInDays) {
        return Map.of();
    }
    @Override
    public Map<String, Integer> getOverallDfiTrend(int rangeInDays) {
        return Map.of();
    }
    @Override
    public List<DataScoreCard> getAllScoreCard() {
        return List.of();
    }
    @Override
    public DataScoreCard getScoreCard(EntityDefinition entity) {
        return null;
    }
    @Override
    public Map<String, Integer> getEntityScoreMap() {
        return Map.of();
    }
    @Override
    public EntityData save(EntityDefinition entityDefinition, EntityData record){
        return record;
    }
    @Override
    public void updateLastTransactionId(EntityDefinition syncariEntityDef, List<TransactionLog> savedTransactions, List<EntityData> entitiesBatch) {
        // no operation
    }
    @Override
    public double sum(EntityDefinition entity, AttributeDefinition a, Optional<LookupCriteriaVisitor> mongoCriteria) {
        return 0.0;
    }
    @Override
    public double avg(EntityDefinition entity, AttributeDefinition a, Optional<LookupCriteriaVisitor> mongoCriteria) {
        return 0.0;
    }
    @Override
    public Map<String, List<RuleAssignment>> getRulesForEntityByField(String entityApiName) {
        return Map.of();
    }
    @Override
    public void updateReferringEntities(String entityId, List<String> syncariIds) {
        //nothing
    }

}
