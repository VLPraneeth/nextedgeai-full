package com.syncari.core.service;

import com.syncari.connector.EntityData;
import com.syncari.connector.Operation;
import com.syncari.connector.exception.ErrorCodes;
import com.syncari.connector.exception.NonRetriableException;
import com.syncari.core.datatype.DateType;
import com.syncari.core.dedupe.EnrichDedupeConfig;
import com.syncari.core.event.store.EventStore;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.*;
import com.syncari.core.model.dedupe.*;
import com.syncari.core.model.misc.SyncError;
import com.syncari.core.model.pagination.Page;
import com.syncari.core.model.util.ValidationError;
import com.syncari.core.pipeline.DiffInfoExpressionVisitor;
import com.syncari.core.pipeline.DynamicDispatchVisitor;
import com.syncari.core.pipeline.GraphContext;
import com.syncari.core.pipeline.SkipWhenFilterEvaluationVisitor;
import com.syncari.core.pipeline.expression.Expression;
import com.syncari.core.pipeline.jtwig.JTWigFieldMergeVisitor;
import com.syncari.core.pipeline.jtwig.JTWigSelectWinnerVisitor;
import com.syncari.core.pipeline.jtwig.JTwigResult;
import com.syncari.core.repositories.customer.EntityRepo;
import com.syncari.core.repositories.customer.MappingNodeRepo;
import com.syncari.core.token.TokenHelper;
import com.syncari.core.utils.*;
import com.syncari.core.validation.CoreEntityNodeValidator;
import com.syncari.core.validation.ValidationContext;
import com.syncari.utils.I18n;
import com.syncari.utils.Pair;
import com.syncari.utils.Timer;
import com.syncari.utils.Timers;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.jtwig.value.Undefined;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;


@Service
@Slf4j
public class RecordMergeService {
    @Autowired
    private EntityRepo entityRepo;

    @Autowired
    private SchemaService schemaService;

    @Autowired
    private UnresolvedReferenceService unresolvedReferenceService;
    @Autowired
    EntityRepoService entityRepoService;
    @Autowired
    IdMappingService idMappingService;

    @Autowired
    TokenHelper tokenHelper;
    @Autowired
    protected EventStore eventStore;

    @Autowired
    EnrichDedupeConfig enrichDedupeConfig;

    @Autowired
    ErrorNotificationService errorNotificationService;

    @Autowired
    NotificationService notificationService;

    @Autowired
    ConnectorService connectorService;

    @Autowired
    CoreEntityNodeValidator coreEntityNodeValidator;

    @Autowired
    FeatureService featureService;

    @Autowired
    RedisUtils redisUtils;
    
    @Autowired
    MappingNodeRepo nodeRepo;

    /**
     *
     * @param entityDefinition - Must have attributes set
     * @param dedupeConfig
     * @param incomingRecord
     * @return
     */
    public MergeOperation createMergeOperation(EntityDefinition entityDefinition, DedupeConfig dedupeConfig, EntityData incomingRecord) {
        if(!dedupeConfig.isEnableDeduplicate() || dedupeConfig.getDedupeFields().isEmpty() || dedupeConfig.getWinnerStrategy()==WinnerStrategy.DO_NOTHING || incomingRecord.isDeleted()){
            return new MergeOperation().setWinningRecord(incomingRecord);
        }
        var criteria = createCriteria(dedupeConfig, incomingRecord, entityDefinition);
        createIndexesIfNeeded(entityDefinition,dedupeConfig);
        var attributes = entityDefinition.getAttributes();
        var potentialDuplicates = entityRepo.search(entityDefinition, criteria, Pageable.unpaged());
        var winner = findWinningRecord(dedupeConfig, incomingRecord, potentialDuplicates);
        EntityData merged = merge(dedupeConfig, attributes, winner, incomingRecord);
        Timers timer = new Timers(log);
        List<ReferencedRecords> loserRefRecords = findRecordsReferringToLosers(entityDefinition, winner.y, timer, new GraphContext());
        timer.logInfo();
        return new MergeOperation().setEntity(entityDefinition)
                .setLosingRecords(winner.y)
                .setWinningRecord(merged)
                //TODO: Find all records that reference losers
                .setLoserReferencedEntities(loserRefRecords);
    }

    protected void createIndexesIfNeeded(EntityDefinition entityDefinition, AdvancedDedupeConfig advancedDedupeConfig, EntityData incomingRecord,
            GraphContext graphContext) {
        // For simulation test modes, we do not have the entityRepo set.
        if (graphContext.isSimulationMode()) return;
        boolean processedIndexes = graphContext.cachedOrDefault("processedIndexes", false);
        if (!processedIndexes) {
            for(Expression expression : advancedDedupeConfig.findDupesCriteria()){
                var mongoCriteria = new MongoFindDedupeCriteriaVisitor(incomingRecord, expression,entityDefinition, entityRepo);
                // This is needed to populate the attributeApiNames in the criteriavisitor.
                var criteria = mongoCriteria.createCriteria();
                List<String> fieldsInCriteria = new ArrayList<>(mongoCriteria.getAttributeApiNames());
                List<AttributeDefinition> fieldsToIndex = fieldsInCriteria.stream()
                    .filter(dedupeField -> entityDefinition.getField(dedupeField).isPresent())
                    .map(dedupeField -> entityDefinition.getField(dedupeField).get())
                    .collect(Collectors.toList());
                entityRepo.createIndexes(entityDefinition, fieldsToIndex);
                log.debug("Processed Indexes for this sync context for the advanced dedupe criteria field {} ", fieldsInCriteria);
            }
            graphContext.cache("processedIndexes", true);
        }
    }

    private void createIndexesIfNeeded(EntityDefinition entityDefinition, DedupeConfig dedupeConfig) {
        List<AttributeDefinition> fieldsToIndex = dedupeConfig.getDedupeFields().stream()
                .map(dedupeField -> entityDefinition.getAttribute(dedupeField)).collect(Collectors.toList());
        entityRepo.createIndexes(entityDefinition, fieldsToIndex);
    }

    private List<ReferencedRecords> findRecordsReferringToLosers(EntityDefinition entity, List<EntityData> losers, Timers timer, GraphContext context) {
        List<Reference> references = (entity.getReferences() != null) ? entity.getReferences() : schemaService.getReferringAttributes(entity);
        return references.stream().map(reference -> {
            var entityDefinition = reference.getFromEntity();
            List<Object> loserReferences= losers.stream().map(l->
                    //assume id field if toAttribute is not set
                reference.getToAttribute()==null || reference.getToAttribute().isIdField() ? l.getSyncariEntityId() : l.getValue(reference.getToAttribute().getApiName())
            ).filter(x -> !ObjectUtils.isEmpty(x)).collect(Collectors.toList());
            // TODO: optimize by caching the info that we already processed the indexes or move it to the very beginning of merge processing.
            var indexesPresent = context.cachedOrDefault(String.format("createdReferredIndexes_%s_%s",
                    entityDefinition.getId(),reference.getFromAttribute().getId()), false);
            if (!indexesPresent) {
                entityRepo.createIndexes(entityDefinition, List.of(reference.getFromAttribute()));
                context.cache(String.format("createdReferredIndexes_%s_%s",
                        entityDefinition.getId(),reference.getFromAttribute().getId()), true);
            }

            String timerText = String.format("entityRepo:findByAttribute:%s:%s", entityDefinition.getApiName(), reference.getFromAttribute().getApiName());
            List<EntityData> loserReferencedRecords = entityRepo.findByAttribute(entityDefinition.getApiName(),
                    reference.getFromAttribute().getApiName(), loserReferences).stream().map(ed -> new EntityData(ed.getName()).setSyncariEntityId(ed.getSyncariEntityId()).setId(ed.getId())).collect(Collectors.toList());
            ReferencedRecords referenceRecords = timer.time(timerText, () ->
                new ReferencedRecords().setReference(reference).setReferencedRecords(loserReferencedRecords)
            );
            return referenceRecords;
        }).collect(Collectors.toList());

    }

    public void apply(MergeOperation operation, GraphContext context) {
        EntityDefinition syncariEntity = schemaService.getEntity(operation.getEntity().getId());
        List<EntityData> losingRecords = operation.getLosingRecords();
        final Timers mergeTimers = new Timers(log);
        try(Timer ignored = mergeTimers.timer("merge:deleteLosers")){
            entityRepo.deleteAll(syncariEntity, losingRecords);
        }

        try(Timer ignored = mergeTimers.timer("merge:saveWinner")){
            //set the hash of dedupe criteria on the record
            operation.getWinningRecord().setDedupeHash(context.cached(getDedupeHashKey(context)));
            entityRepo.save(syncariEntity, operation.getWinningRecord());
        }
        //when we find a set of losers, a loser may be connected to one or more synapses that don't have a duplicate.
        //This will remap such "losers" to the winning record, so taht they are not inadvertently deleted in the synapse.
        //Example: Salesforce and hubspot are connected. There is a single record in syncari S1 that is connected to
        //records SFDC1 and HUB1. Now a dupe is created in SFDC, called SFDC2, and mapped to S2 in syncari When that comes in, if S1 gets marked as a
        //loser due to some winning criteria, it will delete SFDC1 and HUB1. But we don't want HUB1 to be deleted, but simply connected to S2.

        try(Timer ignored = mergeTimers.timer("merge:migrateRetainedLosers")){
            IdMapping winner = migrateRetainedLosers(operation);
        }
        //"fix" foreign keys
        updateReferences(operation, context, mergeTimers);
        // delete unresolved references
        List<String> loserIds = operation.getLosingRecords().stream().map(EntityData::getSyncariEntityId).collect(Collectors.toList());
        try(Timer ignored = mergeTimers.timer("merge:removeUnnresolvedRefs")) {
            unresolvedReferenceService.removeBy(operation.getEntity().getId(), loserIds);
        }
        // move references to losers that are not yet consumed my dependent pipeelines to winner
        try(Timer ignored = mergeTimers.timer("merge:reparentLoserReferences")) {
            unresolvedReferenceService.reparentLoserReferences(loserIds, operation.getWinningRecord().getSyncariEntityId());
        }
        mergeTimers.logInfo();
    }

    private void updateReferences(MergeOperation operation, GraphContext context, Timers mergeTimers) {
        try(Timer ignored = mergeTimers.timer("merge:updateRefs")){
            final Map<String, List<EntityData>> referenceUpdates = generateReferenceUpdates(operation);
            referenceUpdates.forEach((childEntityDefId, records)->{
                EntityDefinition childEntityDef = context.cache(childEntityDefId, ()->schemaService.getEntity(childEntityDefId));
                entityRepo.updateAll(childEntityDef, referenceUpdates.getOrDefault(childEntityDef.getId(),List.of()));
            });
        }
    }

    protected IdMapping migrateRetainedLosers(MergeOperation operation) {
         /*
        1. find all loser idmappings
        2. for every synapse+entitydef that is not the same as winner synapse+entitydef, if there is only one mapping, reassign that to winner's idmapping
        3. If there are multiple records for synapse+entitydef, reassign the latest idmapping to winner
         */
        //remap losers to winner
        List<EntityData> losingRecords = operation.getLosingRecords();
        Map<String, EntityData> losingRecordMap =new HashMap<>();
        losingRecords.forEach(losingRecord -> {
            losingRecordMap.put(losingRecord.getSyncariEntityId(),losingRecord);
        });
        List<String> syncariIds = losingRecords.stream().map(r->r.getSyncariEntityId()).collect(Collectors.toList());
        syncariIds.add(operation.getWinningRecord().getSyncariEntityId());
        List<IdMapping> idMappings = idMappingService.findBySyncariIds(operation.getEntity().getApiName(),syncariIds);
        Optional<IdMapping> winnerIdMapping = idMappings.stream().filter(idMapping->idMapping.getSyncariId().equals(operation.getWinningRecord().getSyncariEntityId())).findFirst();
        List<FlattenedIdMapping> loserIdMappings = idMappings
                .stream()
                .filter(idMapping->!idMapping.getSyncariId().equals(operation.getWinningRecord().getSyncariEntityId()))
                .flatMap(r->FlattenedIdMapping.fromIdMapping(r,losingRecordMap.get(r.getSyncariId()))).collect(Collectors.toList());

        IdMapping winner = winnerIdMapping.orElse(new IdMapping().setEntityName(operation.getEntity().getApiName())
                .setSyncariId(operation.getWinningRecord().getSyncariEntityId()));

        //sort by lastmodified, desc
        loserIdMappings.sort(Comparator.comparingLong(FlattenedIdMapping::getRecordLastModifiedAt).reversed());
        Map<String, FlattenedIdMapping> retainedLosers = new HashMap<>();
        //latest losers that are to be retained (that belong to other synapses)
        loserIdMappings.forEach(loser-> {
            if(!retainedLosers.containsKey(loser.getExternalEntityDefinitionId()) && !winner.isMapped(loser.getExternalEntityDefinitionId())){
                retainedLosers.put(loser.getExternalEntityDefinitionId(),loser);
            }
        });
        //if no winner idmapping is present, create a new one
        retainedLosers.forEach((loserExternalEntityDefinitionId, loser)->{
            //migrate idmapping for losers to be retained to winners idmappings
            //Remove from matching old id mappings
            idMappings.forEach(idMapping->{
                idMapping.findAllMapping(loser.getConnectorId(),loser.getExternalEntityDefinitionId(),loser.getExternalRecordId()).ifPresent(m->{
                    idMapping.removeMapping(loser.getConnectorId(),loser.getExternalEntityDefinitionId(),loser.getExternalRecordId());
                    if(!idMapping.hasConnectedMappings()){
                        log.info("IdMapping to be deleted is {}",idMapping);
                        idMappingService.delete(idMapping);
                    }else {
                        idMappingService.save(idMapping);
                    }
                });
            });
            //add to the winner
            winner.addMapping(loser.getConnectorId(),loser.getExternalRecordId(),loser.getExternalEntityDefinitionId(), loser.isDisconnected());
        });
        //save the new idmapping
        if(winner.hasConnectedMappings()){
            idMappingService.upsert(winner);
        }

        return winner;
    }

    protected Map<String, List<EntityData>> generateReferenceUpdates(MergeOperation operation) {
        Map<String, List<EntityData>> referenceUpdatesByEntityDefId = new HashMap<>();
        List<SyncError> refFieldErrors = new ArrayList<>();
        operation.getLoserReferencedEntities().forEach(entry -> {
            Reference reference = entry.getReference();
            List<EntityData> recordIds = entry.getReferencedRecords();
            var childEntityDef = reference.getFromEntity();
            if (reference.getToAttribute() != null) {
                recordIds.forEach(recordId -> {
                    Object newValue = reference.getToAttribute().isIdField() ? operation.getWinningRecord().getSyncariEntityId() :
                            operation.getWinningRecord().getValue(reference.getToAttribute().getApiName());
                    EntityData referenceUpdate = new EntityData(childEntityDef.getApiName())
                            .setSyncariEntityId(recordId.getSyncariEntityId())
                            .setReparented(true)
                            .addValue(reference.getFromAttribute().getApiName(), newValue);
                    final List<EntityData> updates = referenceUpdatesByEntityDefId.getOrDefault(childEntityDef.getId(), new ArrayList<>());
                    updates.add(referenceUpdate);
                    referenceUpdatesByEntityDefId.put(childEntityDef.getId(), updates);
                });
            } else {
                log.warn("Invalid reference found {}", reference);
                final String errorMessage = I18n.i18n("merge_invalid_reference", reference.getFromAttribute().getApiName(), reference.getFromEntity().getDisplayName(), reference.getToEntity().getDisplayName());
                refFieldErrors.add(new SyncError(operation.getEntity().getConnectorId(), "Syncari", operation.getBatchId(),
                        operation.getEntity().getApiName(), null, Operation.merge.name(), "invalid_reference",
                        errorMessage, operation.getWinningRecord().getSyncariEntityId(), null, Instant.now()));
            }
        });
        if (!refFieldErrors.isEmpty()) {
            eventStore.insertErrorLogs(refFieldErrors);
        }
        return referenceUpdatesByEntityDefId;
    }

    private EntityData merge(DedupeConfig dedupeConfig, List<AttributeDefinition> attributes, Pair<EntityData, List<EntityData>> winner, EntityData incomingRecord) {
        switch (dedupeConfig.getMergeStrategy()) {
            case INTELLIGENT_MERGE:
                return merge(winner.x, winner.y, attributes);
            case INCOMING_RECORD:
                return mergeWithIncoming(winner.x, incomingRecord, attributes);
            case WINNER_TAKES_ALL:
            default:
                return winner.x;
        }
    }

    private EntityData merge(EntityData winner, List<EntityData> losers, List<AttributeDefinition> attributes) {
        Comparator<EntityData> comparator = Comparator.comparingLong(e -> e.getLastModified());
        var reversed = comparator.reversed();
        //For every attribute in the entity
        List<EntityData> sorted = losers.stream().sorted(reversed).collect(Collectors.toList());
        for (AttributeDefinition attributeDefinition : attributes) {
            if(attributeDefinition.isSystem()) continue;
            String apiName = attributeDefinition.getApiName();
            //If winner has no value set,
            if (winner.getValue(apiName) == null) {
                //find the latest non-null value from loserss
                var maybeValue = sorted.stream()
                        .filter(e -> e.getValue(apiName) != null)
                        .findFirst()
                        .map(e -> e.getValue(apiName));
                //and set that value on winner, if present
                maybeValue.ifPresent(newValue -> winner.addValue(apiName, newValue));
            }
        }
        return winner;
    }

    private EntityData mergeWithIncoming(EntityData winner, EntityData incoming, List<AttributeDefinition> attributes) {
        for (AttributeDefinition attributeDefinition : attributes) {
            if(attributeDefinition.isSystem()) continue;
            String apiName = attributeDefinition.getApiName();
            //If incoming has a value for the field....
            if (!StringUtils.isBlank(incoming.getValueAsString(apiName))){
                //... set that value on winner
                winner.addValue(apiName, incoming.getValue(apiName));
            }
        }
        return winner;
    }


    private Pair<EntityData, List<EntityData>> findWinningRecord(DedupeConfig dedupeConfig, EntityData incomingRecord, Slice<EntityData> potentialDuplicates) {
        var winner = incomingRecord;
        List<EntityData> allRecords = new ArrayList<>(potentialDuplicates.getContent());
        if (!incomingRecord.isNew()) {
            for (EntityData record : allRecords) {//same records, with an update. Incoming record contains ONLY updated fields
                if (record.getSyncariEntityId().equals(incomingRecord.getSyncariEntityId())) {
                    incomingRecord.getValues().forEach((field, value) -> {
                        record.addValue(field, value);
                    });
                    record.setLastModified(incomingRecord.getLastModified());
                }
            }
        } else {
            allRecords.add(incomingRecord);
        }

        switch (dedupeConfig.getWinnerStrategy()) {
            case LATEST:
                winner = findLatestRecord(incomingRecord, allRecords);
                break;
            case LATEST_EXISTING:
                winner = findLatestExistingRecord(incomingRecord, allRecords);
                break;
            case SELECTED_CONNECTOR:
                winner = findRecordForSelectedSource(incomingRecord, allRecords, dedupeConfig);
                break;
            default:
                break;
        }
        var winnerId = winner.getSyncariEntityId();
        return Pair.of(winner, allRecords.stream().filter(r -> !r.getSyncariEntityId().equals(winnerId)).collect(Collectors.toList()));
    }

    private EntityData findRecordForSelectedSource(EntityData incomingRecord, List<EntityData> allRecords, DedupeConfig dedupeConfig) {
        var connectorId = dedupeConfig.getSelectedConnectorId();
        if (connectorId == null) return incomingRecord;
        var recordsForSelectedSource = allRecords.stream().filter(record -> connectorId.equals(record.getOriginatingConnectorId()));
        //find the latest record
        Comparator<EntityData> comparator = Comparator.comparingLong(e -> e.getLastModified());
        return recordsForSelectedSource.sorted(comparator.reversed()).findFirst().orElse(incomingRecord);
    }


    private EntityData findLatestRecord(EntityData incomingRecord, List<EntityData> allRecords) {
        //find the latest record
        Comparator<EntityData> comparator = Comparator.comparingLong(e -> e.getLastModified());
        return allRecords.stream().sorted(comparator.reversed()).findFirst().orElse(incomingRecord);
    }

    private EntityData findLatestExistingRecord(EntityData incomingRecord, List<EntityData> allRecords) {
        //find the latest record
        Comparator<EntityData> comparator = Comparator.comparingLong(e -> e.getLastModified());
        return allRecords.stream().filter(r->!r.getSyncariEntityId().equals(incomingRecord.getSyncariEntityId()))
                .sorted(comparator.reversed()).findFirst().orElse(incomingRecord);
    }

    private SearchCriteria createCriteria(DedupeConfig dedupeConfig, EntityData incomingRecord, EntityDefinition entityDefinition) {
        Map<String, Object> searchFieldNameValues = new HashMap<>();
        for (String field : dedupeConfig.getDedupeFields()) {
            if(entityDefinition.getAttribute(field)!=null) {
                String attributeName = entityDefinition.getAttribute(field).getApiName();
                searchFieldNameValues.put(attributeName, incomingRecord.getValue(attributeName));
            }else{
                log.warn("Could not find attribute with id {} in entity {} during search",field,entityDefinition.getApiName());
            }
        }
        return new SearchCriteria().setSearchFieldNameValues(searchFieldNameValues);
    }

    //================================================= DEDUPE/MERGE V2 =================================================

    public Optional<MergeOperation> advancedDedupeMerge(AdvancedDedupeConfig advancedDedupeConfig, EntityData incomingRecord, EntityDefinition entityDefinition,
                                                        GraphContext graphContext, TransactionLog txnLog, Optional<EntityData> existingRecord){
        return advancedDedupeMerge(advancedDedupeConfig, incomingRecord, entityDefinition, graphContext, txnLog, existingRecord, Collections.emptyList());
    }

    /**
     *
     * @param advancedDedupeConfig
     * @param incomingRecord
     * @param entityDefinition
     * @param graphContext
     * @return
     */
    public Optional<MergeOperation> advancedDedupeMerge(AdvancedDedupeConfig advancedDedupeConfig, EntityData incomingRecord, EntityDefinition entityDefinition,
                                                        GraphContext graphContext, TransactionLog txnLog, Optional<EntityData> existingRecord, List<EntityData> entitiesBatch){

        if(advancedDedupeConfig == null || ((!graphContext.isResync() && !isDedupeNeeded(entityDefinition, advancedDedupeConfig, incomingRecord, graphContext, txnLog, existingRecord))) ) {
            log.debug("Skipping dedupe for {} record {}", entityDefinition.getApiName(), incomingRecord.getId());
            return Optional.empty();
        }
        Expression skipWhen = advancedDedupeConfig.skipWhenCriteria();
    	if(skipWhen != null) {
    		SkipWhenFilterEvaluationVisitor skipWhenEvaluator = new SkipWhenFilterEvaluationVisitor(graphContext, tokenHelper, entityDefinition, incomingRecord);
    		skipWhen.accept(new DynamicDispatchVisitor(skipWhenEvaluator));
    		if(BooleanUtils.toBoolean(String.valueOf(skipWhenEvaluator.getValue()))) {
    			log.info("Skipping dedupe due to skip when condition for {} record {}", entityDefinition.getApiName(), incomingRecord.getId());
                return Optional.of(new MergeOperation()
                    .setFilterCondition(
                        getExpressionForTx(graphContext, advancedDedupeConfig.skipWhenCriteria()))
                    .setRecords(List.of(incomingRecord)));
    		}
    	}
        Timers timer = new Timers(log);
        MergeInfo mergeInfo = new MergeInfo();

        // validate dedupe configuration
        graphContext.cache("graphValidation_" + entityDefinition.getId(), () -> validateDedupeCriteria(graphContext, entityDefinition));

        timer.time("advancedDedupeMerge:createIndexesIfNeeded",
            () -> createIndexesIfNeeded(entityDefinition, advancedDedupeConfig, incomingRecord, graphContext));

        List<EntityData> duplicates = timer.time("advancedDedupeMerge:findDuplicates",()->findDuplicates(advancedDedupeConfig, incomingRecord, entitiesBatch, entityDefinition, mergeInfo, graphContext));
        Optional<MergeOperation> possibleSkipWhenOperation = Optional.empty();
        if(skipWhen != null) {
    		List<EntityData> filteredDuplicates = duplicates.stream().filter(e -> {
    			SkipWhenFilterEvaluationVisitor skipWhenEvaluator = new SkipWhenFilterEvaluationVisitor(graphContext, tokenHelper, entityDefinition, e);
    			skipWhen.accept(new DynamicDispatchVisitor(skipWhenEvaluator));
    			return !BooleanUtils.toBoolean(String.valueOf(skipWhenEvaluator.getValue()));
    		}).collect(Collectors.toList());
    		if(duplicates.size() > filteredDuplicates.size()) {
    		  //Log Transaction here
    		  List<EntityData> skippedRecords = new ArrayList<EntityData>(duplicates);
    		  skippedRecords.removeAll(filteredDuplicates);
              possibleSkipWhenOperation = Optional.of(new MergeOperation()
                  .setFilterCondition(
                      getExpressionForTx(graphContext, advancedDedupeConfig.skipWhenCriteria()))
                  .setRecords(skippedRecords));
    		}
    		duplicates.clear();
    		duplicates.addAll(filteredDuplicates);
    	}

        if(duplicates.isEmpty()){
            timer.logDebug();
            return possibleSkipWhenOperation;
        }
        EntityData winner =timer.time("advancedDedupeMerge:selectWiner",()->
            selectWinner(advancedDedupeConfig, incomingRecord, duplicates, entityDefinition, mergeInfo).orElse(null)
        );
        if (null == winner){
          return possibleSkipWhenOperation;
        }

        List<EntityData> losers = new ArrayList<>(duplicates);
        losers.add(incomingRecord);
        Iterator<EntityData> iterator = losers.iterator();
        while(iterator.hasNext()){
            if(iterator.next().getSyncariEntityId().equals(winner.getSyncariEntityId())){
                iterator.remove();
            }
        }
        //EntityData potentialWinner = applyMergePoliciesWithCriteria(advancedDedupeConfig, winner, losers, entityDefinition, graphContext);
        EntityData potentialWinner = timer.time("advancedDedupeMerge:applyMergePoliciesWithCriteria",
                ()->applyMergePoliciesWithCriteria(advancedDedupeConfig, winner, losers, entityDefinition, mergeInfo, graphContext)
        );
        EntityData mergedWinner = timer.time("advancedDedupeMerge:merge",()->
                merge(advancedDedupeConfig, potentialWinner, incomingRecord, losers, entityDefinition)
        );
        List<ReferencedRecords> loserRefRecords = findRecordsReferringToLosers(entityDefinition, losers, timer, graphContext);
        timer.logInfo();

        // Map used for UI display
        Map<String, Map<String, Object>> attribDefinitionMap = entityDefinition.getAttributes().stream()
                .collect(Collectors.toMap(AttributeDefinition::getApiName,
                        e -> Map.of("displayName", StringUtils.isBlank(e.getDisplayName()) ? "" : e.getDisplayName(), "dataType", e.getDataType())));

        var mergeOperation = new MergeOperation().setEntity(entityDefinition)
            .setLosingRecords(losers)
            .setWinningRecord(mergedWinner)
            .setLoserReferencedEntities(loserRefRecords)
            .setMergeAction(advancedDedupeConfig.getMergeAction())
            .setAttributeDefinitionMap(attribDefinitionMap)
            .setMergeInfo(mergeInfo)
            .setMaxAllowedDupes(advancedDedupeConfig.getMaximumAllowedDupes())
            .setBatchId(graphContext.getCurrentBatch()==null? "N/A" : graphContext.getCurrentBatch().getCurrentBatchId());
        possibleSkipWhenOperation.ifPresent(sr -> {
          mergeOperation.setRecords(sr.getRecords());
          mergeOperation.setFilterCondition(sr.getFilterCondition());
        });
        return Optional.of(mergeOperation);
    }

    private String getExpressionForTx(GraphContext graphContext, Expression skipWhenCriteria) {
      var evaluator = new DiffInfoExpressionVisitor(graphContext, schemaService, nodeRepo, tokenHelper);
      skipWhenCriteria.accept(evaluator);
      return evaluator.getValue();
    }

    private boolean isDedupeNeeded(EntityDefinition entityDefinition, AdvancedDedupeConfig advancedDedupeConfig, EntityData incomingRecord,
                                   GraphContext graphContext, TransactionLog txnLog, Optional<EntityData> existingRecord) {
        if(txnLog == null) return true;
        String cacheKey = String.format("dedupe_%s_%s", graphContext.getGraph().getName(), graphContext.getCurrentBatch().getCurrentBatchId());
        log.debug("Searching for cache key {}", cacheKey);
        List<String> fieldsInCriteria = new ArrayList<>();
        if(!graphContext.getCache().containsKey(cacheKey)) {
            for(Expression expression : advancedDedupeConfig.findDupesCriteria()){
                var mongoCriteria = new MongoFindDedupeCriteriaVisitor(incomingRecord, expression, entityDefinition, entityRepo);
                // This is needed to populate the attributeApiNames in the criteriavisitor.
                mongoCriteria.createCriteria();
                fieldsInCriteria.addAll(mongoCriteria.getAttributeApiNames().stream()
                        .flatMap(dedupeField -> entityDefinition.getField(dedupeField).stream())
                        .map(dedupeField -> dedupeField.getId())
                        .collect(Collectors.toList()));
            }
            graphContext.getCache().put(cacheKey, fieldsInCriteria);
        } else {
            fieldsInCriteria = (List<String>) graphContext.getCache().get(cacheKey);
        }
        String dedupeHashCacheKey = getDedupeHashKey(graphContext);
        String currentDedupeHash = graphContext.cache(dedupeHashCacheKey, ()-> advancedDedupeConfig.getDedupeHash());
        incomingRecord.setDedupeHash(currentDedupeHash);
        // If there is no change in dedupe criteria, then do there is no need to run dedupe
        boolean hasDedupeCriteriaChanged = existingRecord.map(e-> !currentDedupeHash.equals(e.getDedupeHash())).orElse(true);
        return  hasDedupeCriteriaChanged || fieldsInCriteria.stream().anyMatch(field -> txnLog.hasChangeFor(field));
    }

    public String getDedupeHashKey(GraphContext graphContext) {
        return String.format("dedupe_hash_%s_%s_%s", graphContext.getCurrentSyncariId(), graphContext.getGraph().getName(), graphContext.getCurrentBatch().getCurrentBatchId());
    }

    private void validateDedupeCriteria(GraphContext graphContext, EntityDefinition coreEntity) {
        MappingGraph graph = graphContext.getGraph();
        MappingNode coreNode = graph.getCoreNode();
        ValidationContext validationContext = new ValidationContext().setGraph(graph).setNode(coreNode).setTopoSortedNodes(graph.toposort())
                .setSyncariConnector(connectorService.getSyncariConnector()).setValidationType(ValidationContext.ValidationType.NODE).setCoreEntity(coreEntity);

        var errors = coreEntityNodeValidator.validateWithoutException(validationContext);

        if(!errors.isEmpty()){
            // get first error and throw as fatal error as sync cannot be continued
            ValidationError error = errors.get(0);
            String errorMsg = String.format("Invalid dedupe criteria in entity pipeline %s. Error: %s", graph.getName(), error.getMessage());
            log.error(errorMsg);
            throw new NonRetriableException(ErrorCodes.FATAL_ERROR.name(), errorMsg, "INVALID_DEDUPE_CONFIG");
        }
    }

    public List<EntityData> findDuplicates(AdvancedDedupeConfig advancedDedupeConfig,EntityData incomingRecord,EntityDefinition entityDefinition, MergeInfo mergeInfo){
        // for all tests use DB for dedupe
        return findDuplicates(advancedDedupeConfig, incomingRecord, entityDefinition, mergeInfo, new GraphContext());
    }

    public List<EntityData> findDuplicates(AdvancedDedupeConfig advancedDedupeConfig,EntityData incomingRecord, List<EntityData> entityBatch,
                                           EntityDefinition entityDefinition, MergeInfo mergeInfo, GraphContext context){
        List<EntityData> inMemoryDuplicates = findDuplicatesInBatch(advancedDedupeConfig, incomingRecord, entityDefinition, entityBatch);
        Map<String, EntityData> inMemoryDuplicatesMap = inMemoryDuplicates.stream().collect(Collectors.toMap(EntityData::getId, Function.identity(), (first, second) -> second));
        Map<String, EntityData> entityDataMap = entityBatch.stream().collect(Collectors.toMap(EntityData::getId, Function.identity(), (first, second) -> second));

        List<EntityData> dbDuplicates = findDuplicates(advancedDedupeConfig, incomingRecord, entityDefinition, mergeInfo, context);

        /*
            Cases:
            1. In memory duplicate db duplicate is found
            2. In memory duplicate found but db duplicate is not found
            3. In memory duplicate not found but db duplicate is found - use db duplicate if it is not present in the batch
            4. In memory duplicate not found and db duplicate is not found
         */

        for (EntityData entityData : dbDuplicates) {
            if(!inMemoryDuplicatesMap.containsKey(entityData.getId()) && !entityDataMap.containsKey(entityData.getId())){
                // check if the db duplicate is not present in the batch
                inMemoryDuplicates.add(entityData);
            }
        }
        return inMemoryDuplicates;
    }

    public List<EntityData> findDuplicates(AdvancedDedupeConfig advancedDedupeConfig,EntityData incomingRecord,EntityDefinition entityDefinition, MergeInfo mergeInfo, GraphContext context){
        int expressionIndex =0;

        boolean isTestMode = context.isTestMode() || context.isSimulationMode();

        boolean useCache = entityRepo.useCache(entityDefinition) && !isTestMode ?
                context.cache(entityDefinition.getApiName() + "_index_status",
                        () -> redisUtils.indexStatus(entityDefinition.getApiName())) : false;

        for(Expression expression : advancedDedupeConfig.findDupesCriteria()){

            Criteria redisCriteria = new RedisFindDedupeCriteriaVisitor(incomingRecord, expression,entityDefinition);
            MongoFindDedupeCriteriaVisitor mongoCriteria = new MongoFindDedupeCriteriaVisitor(incomingRecord, expression,entityDefinition, entityRepo);

            Page<EntityData> search = entityRepo.searchWithFallback(entityDefinition, Optional.of(redisCriteria), Optional.of(mongoCriteria), useCache, Page.MAX_PAGE_SIZE -1);
            
            if(!search.getRecords().isEmpty()){
                log.info("Found duplicates with expression #{} Record: {}, ({}) records {}", expressionIndex,incomingRecord,expression, search.getRecords().size(),search.getRecords());
                mergeInfo.setDuplicateSelector(enrichDedupeConfig.fromMap(advancedDedupeConfig.getDedupPredicate(expressionIndex)));
                return search.getRecords();
            }
            expressionIndex++;
        }
        log.debug("No duplicates found for record {}",incomingRecord);
        return List.of();
    }

    public List<EntityData> findDuplicatesInBatch(AdvancedDedupeConfig advancedDedupeConfig, EntityData incomingRecord, EntityDefinition entityDefinition,
                                                  List<EntityData> entityBatch) {

        int expressionIndex =0;
        for (Expression expression : advancedDedupeConfig.findDupesCriteria()) {
            var records = entityBatch.stream().filter(e -> {
                        var dedupeVisitor = new DedupeEvaluationVisitor(e, entityDefinition, incomingRecord);
                        expression.accept(new DynamicDispatchVisitor(dedupeVisitor));
                        return Boolean.TRUE.equals(dedupeVisitor.getValue());
                    }).collect(Collectors.toList());

            // filter out incoming record
            records = records.stream().filter(e->!e.getId().equals(incomingRecord.getId())).collect(Collectors.toList());

            if (!records.isEmpty()) {
                log.info("Found duplicates in memory batch with expression #{} Record: {}, ({})", expressionIndex, incomingRecord, expression);
                log.debug("Record Ids {}", records.stream().map(EntityData::getId).collect(Collectors.joining(",")));
                return records;
            }
            expressionIndex++;
        }
        return new ArrayList<>();
    }

    public Optional<EntityData> selectWinner(AdvancedDedupeConfig advancedDedupeConfig, EntityData incomingRecord, List<EntityData> candidates,
                                             EntityDefinition entityDefinition, MergeInfo  mergeInfo){
        List<Expression> selectWinnerPredicates = advancedDedupeConfig.getWinnerSelectionPredicates();
        //If expression-style winner policies are not set,
        if(selectWinnerPredicates.isEmpty()){
            //find and convert legacy policies to expressions
            List<WinnerSelection> winnerSelectionPolicies = advancedDedupeConfig.getWinnerSelectionPolicies();
            selectWinnerPredicates = winnerSelectionPolicies.stream()
                    .map(policy-> policy.toExpression())//translate legacy selection policy to expressions
                    .filter(e->e!=null)
                    .collect(Collectors.toList());
        }
        //if no winner policies set, return Empty as the winner
        if(selectWinnerPredicates.isEmpty()){
            return Optional.empty();
        }else{
            return selectWinnerByPredicate(selectWinnerPredicates,advancedDedupeConfig,incomingRecord,candidates,entityDefinition, mergeInfo);
        }
    }

    protected <T> T evaluate(String expression, Map<String, Object> context,Class<T> type){
        try {
            Pair<String, Object> results = tokenHelper.evaluateExpression(context, expression);
            Object result = results.y;
            if(result==null || result == Undefined.UNDEFINED){
                return null;
            }
            if(type.isAssignableFrom(result.getClass())){
                return type.cast(result);
            }else{
                log.error("Cannot cast {} of type {} to {}",result, result.getClass(),type);
                throw new SyncariValidationException("Cannot cast %s of type %s to %s",result, result.getClass(),type);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            JTwigResult.remove();
        }
    }

    protected Optional<EntityData> selectWinnerByPredicate(List<Expression> winnerSelectionPredicates,AdvancedDedupeConfig advancedDedupeConfig,
                                                           EntityData incomingRecord, List<EntityData> candidates,
                                                           EntityDefinition entityDefinition, MergeInfo mergeInfo){
        boolean isProgressiveSelection = advancedDedupeConfig.isProgressiveWinnerSelection();
        log.debug("Progressive selection flag is {}", isProgressiveSelection);
        Map<String, Object> context = new HashMap<>();
        List<EntityData> allRecords = new ArrayList<>(candidates);
        allRecords.add(incomingRecord);
        RecordLevelSelector recordLevelSelector = new RecordLevelSelector(allRecords,entityDefinition);
        FieldLevelSelector fieldLevelSelector = new FieldLevelSelector(allRecords,entityDefinition);
        context.put("recordSelector", recordLevelSelector);
        context.put("fieldSelector", fieldLevelSelector);
        //sort by last modified as a tie breaker
        allRecords.sort(Comparator.comparingLong(EntityData::getLastModified).reversed());
        List<EntityData> edDataListToIterate = new ArrayList<>(allRecords);
        int expressionIndex = 0;
        for(Expression winnerSelector : winnerSelectionPredicates){
            List<EntityData> edListNotMatched = new ArrayList<>();
            List<EntityData> edListMatched = new ArrayList<>();
            JTWigSelectWinnerVisitor visitor = new JTWigSelectWinnerVisitor(tokenHelper,entityDefinition);
            winnerSelector.accept(new DynamicDispatchVisitor(visitor));
            for(EntityData record: edDataListToIterate){
                context.put("record",record);
                entityDefinition.getActiveAttributes().forEach(attribute -> context.put("field_"+attribute.getId(),getRecordValue(attribute, record)));
                Boolean matches = evaluate(visitor.getGeneratedBody(),context,Boolean.class);
                if(matches!=null && matches){
                    if (!isProgressiveSelection){
                        log.info("Found winner with winner selection {} winning record: {}, incoming: {}",winnerSelector, record, incomingRecord);
                        mergeInfo.setWinnerSelectorPredicate(advancedDedupeConfig.getWinnerSelectionPredicate(expressionIndex));
                        return Optional.of(record);
                    }
                    edListMatched.add(record);
                }else{
                    edListNotMatched.add(record);
                }
            }
            if (isProgressiveSelection){
                if (CollectionUtils.isNotEmpty(edListMatched) && (edListMatched.size() == 1)){
                    log.info("Found winner with progressive selection and winner selection {} winning record: {}, incoming: {}",winnerSelector, edListMatched.get(0), incomingRecord);
                    mergeInfo.setWinnerSelectorPredicate(advancedDedupeConfig.getWinnerSelectionPredicate(expressionIndex));
                    return Optional.of(edListMatched.get(0));
                }
                if(CollectionUtils.isNotEmpty(edListMatched) && edListMatched.size() > 1) {
                    edDataListToIterate = edListMatched;
                }else{
                    edDataListToIterate = edListNotMatched;
                }
            }
            log.info("No winner found for predicate {}, current record list to iterate is {} and its size is {},Progressive selection flag is {}",winnerSelector,edDataListToIterate,edDataListToIterate, edDataListToIterate.size(),isProgressiveSelection);
            recordLevelSelector.setCandidates(edDataListToIterate);
            fieldLevelSelector.setCandidates(edDataListToIterate);
            expressionIndex++;
        }
        log.info("No Winners found. Defaulting to incoming record {}", incomingRecord);
        return Optional.of(incomingRecord);
    }

    private Object getRecordValue(AttributeDefinition attribute, EntityData record){
        if ((null != record.getValue(attribute.getApiName())) && (attribute.getDataType() instanceof DateType)){
            return ((Date)attribute.convert(record.getValue(attribute.getApiName()))).toInstant();
        }
        return record.getValue(attribute.getApiName());
    }


    protected Optional<EntityData> selectProgressiveWinnerByPredicate(List<Expression> winnerSelectionPredicates,AdvancedDedupeConfig advancedDedupeConfig,
                                                           EntityData incomingRecord, List<EntityData> candidates,
                                                           EntityDefinition entityDefinition, MergeInfo mergeInfo){
        Map<String, Object> context = new HashMap<>();
        List<EntityData> allRecords = new ArrayList<>(candidates);
        allRecords.add(incomingRecord);
        //sort by last modified as a tie breaker
        allRecords.sort(Comparator.comparingLong(EntityData::getLastModified).reversed());
        List<EntityData> progressiveRecords = new ArrayList<>(allRecords);
        int expressionIndex = 0;
        for(Expression winnerSelector : winnerSelectionPredicates){
            context.put("recordSelector", new RecordLevelProgressiveSelector(progressiveRecords,entityDefinition));
            context.put("fieldSelector", new FieldLevelProgressiveSelector(progressiveRecords,entityDefinition));
            context.put("record", progressiveRecords);
            JTWigSelectWinnerVisitor visitor = new JTWigSelectWinnerVisitor(tokenHelper,entityDefinition);
            winnerSelector.accept(new DynamicDispatchVisitor(visitor));
            progressiveRecords = evaluate(visitor.getGeneratedBody(),context,List.class);
            if ((CollectionUtils.isNotEmpty(progressiveRecords)) && (progressiveRecords.size() == 1)){
                log.info("Found winner with winner selection {} winning record: {}, incoming: {}",winnerSelector, progressiveRecords.get(0), incomingRecord);
                mergeInfo.setWinnerSelectorPredicate(advancedDedupeConfig.getWinnerSelectionPredicate(expressionIndex));
                return Optional.of(progressiveRecords.get(0));
            }
            if (CollectionUtils.isEmpty(progressiveRecords)){
                progressiveRecords = allRecords;
            }
            expressionIndex++;
        }
        log.info("No Winners found for progressive selection. Defaulting to incoming record {}", incomingRecord);
        return Optional.of(incomingRecord);
    }

    public EntityData applyMergePolicies(AdvancedDedupeConfig advancedDedupeConfig, EntityData winner, List<EntityData> losers,EntityDefinition entityDefinition){
        EntityData projectedWinner = winner.withValues(new HashMap<>(winner.getValues()));
        log.info("Default merge policy is {} with default winner override {} on entity {}",
                advancedDedupeConfig.getDefaultWinnerValueSelectionPolicy(),
                advancedDedupeConfig.getDefaultWinnerOverridePolicy(), winner.getName());

        Map<String,WinningAttributeOverride> attributeOverrides = advancedDedupeConfig.getFieldOverrides().stream().collect(Collectors.toMap(o -> o.getAttributeId(),o->o));
        entityDefinition.getAttributes().forEach(attributeDefinition -> {
            var mergePolicy = attributeOverrides.containsKey(attributeDefinition.getId())? attributeOverrides.get(attributeDefinition.getId()) :
                    new WinningAttributeOverride()
                            .setAttributeId(attributeDefinition.getId())
                            .setValueSelectionPolicy(advancedDedupeConfig.getDefaultWinnerValueSelectionPolicy())
                            .setOverridePolicy(advancedDedupeConfig.getDefaultWinnerOverridePolicy());

            String apiName = attributeDefinition.getApiName();
            log.info("Applying merge policy {} with winner override {} on field {}",mergePolicy.getValueSelectionPolicy(),mergePolicy.getOverridePolicy(), apiName);
            Object mergedValue = mergePolicy.getValueSelectionPolicy().apply(apiName, winner, losers,Map.of());
            String syncariRecordId = projectedWinner.getSyncariEntityId();
            switch (mergePolicy.getOverridePolicy()){
                case ALWAYS:
                    log.info("Winner value was {} on field {}. Overriding with value {} on winner with id {}",projectedWinner.getValue(apiName), apiName,mergedValue, syncariRecordId);
                    projectedWinner.addValue(apiName,mergedValue);
                    break;
                case WHEN_BLANK:
                    if(projectedWinner.getValue(apiName)==null){
                        log.info("Winner value is blank on field {}. Setting value {} on winner with id {}",mergedValue, apiName, syncariRecordId);
                        projectedWinner.addValue(apiName,mergedValue);
                    }
                    break;
                default:
                    log.info("Winner value is {} on field {}. No merge take on winner with id {}",projectedWinner.getValue(apiName), apiName, syncariRecordId);
                    break;
            }
        });

        return projectedWinner;
    }

    public EntityData applyMergePoliciesWithCriteria(AdvancedDedupeConfig advancedDedupeConfig, EntityData winner, List<EntityData> losers, EntityDefinition entityDefinition, MergeInfo mergeInfo, GraphContext graphContext){
        MappingGraph currentGraph = graphContext.getGraph();
        EntityData projectedWinner = winner.withValues(new HashMap<>(winner.getValues()));
        log.info("Default merge policy is {} with default winner override {} on entity {}",
                advancedDedupeConfig.getDefaultWinnerValueSelectionPolicy(),
                advancedDedupeConfig.getDefaultWinnerOverridePolicy(), winner.getName());

        mergeInfo.setWinnerOverridePolicy(advancedDedupeConfig.getDefaultWinnerOverridePolicy());
        mergeInfo.setWinnerValueSelectionPolicy(advancedDedupeConfig.getDefaultWinnerValueSelectionPolicy());

        List<FieldMergePolicy> attributeOverrides = advancedDedupeConfig.getFieldMergePolicies();
        List<EntityData> candidates = new ArrayList<>(losers);
        candidates.add(winner);
        Set<String> overriddenFields= new HashSet<>();
        MergeValueSelector mergeValueSelector = new MergeValueSelector(candidates, entityDefinition);
        graphContext.put("fieldMerge", mergeValueSelector);
        attributeOverrides.forEach(policy ->{
            JTWigFieldMergeVisitor fieldMergeVisitor = new JTWigFieldMergeVisitor(tokenHelper, graphContext);
            policy.getExpresson().accept(new DynamicDispatchVisitor(fieldMergeVisitor));
            String generatedBody = fieldMergeVisitor.getGeneratedBody();
            String attributeId = fieldMergeVisitor.getAttributeId();
            var attributeMaybe = entityDefinition.getFieldById(attributeId);
            if(attributeMaybe.isEmpty()){
                // throw the fatal error
                String errorMsg = String.format("Invalid field with id %s referenced in merge condition of dedupe config in entity pipeline %s", attributeId, currentGraph.getName());
                log.error(errorMsg);
                throw new NonRetriableException(ErrorCodes.FATAL_ERROR.name(), errorMsg, "INVALID_DEDUPE_CONFIG");
            }

            final AttributeDefinition attribute = entityDefinition.getAttribute(attributeId);
            String apiName = attribute.getApiName();
            overriddenFields.add(attributeId);
            mergeInfo.addFieldMergePolicy(apiName, policy);
            switch (policy.getOverridePolicy()){
                case ALWAYS: {
                    Object result = evaluate(generatedBody, graphContext, Object.class);
                    if (result instanceof Map){
                        Object res = ((Map<String, Object>)result).get(MergeValueSelector.RESULT);
                        Map<String, Object> retainFieldsData = (Map<String, Object>)((Map<String, Object>)result).getOrDefault(MergeValueSelector.RETAINFIELDS, Map.of());
                        // if result is null does not make sense to update projectedWinner
                        if (null != res){
                            projectedWinner.addValue(apiName, attribute.convert(res));
                        }else{
                            log.debug("Result evaluated null for generated body {}", generatedBody);
                        }
                        retainFieldsData.forEach( (k,v) -> {
                            final AttributeDefinition retainedAttribute = entityDefinition.getAttribute(k);
                            String retainFieldApiName = retainedAttribute.getApiName();
                            projectedWinner.addValue(retainFieldApiName, retainedAttribute.convert(v));
                            overriddenFields.add(k);
                        });
                    }else{
                        // if result is null does not make sense to update projectedWinner
                        if (null != result){
                            projectedWinner.addValue(apiName, attribute.convert(result));
                        }else{
                            log.debug("Evaluated result is null for generated body {}", generatedBody);
                        }
                    }
                    break;
                }
                case WHEN_BLANK: {
                    if(StringUtils.isBlank(projectedWinner.getValueAsString(apiName))) {
                        Object result = evaluate(generatedBody, graphContext, Object.class);
                        if (result instanceof Map) {
                            Object res = ((Map<String, Object>) result).get(MergeValueSelector.RESULT);
                            Map<String, Object> retainFieldsData = (Map<String, Object>) ((Map<String, Object>) result).getOrDefault(MergeValueSelector.RETAINFIELDS, Map.of());
                            projectedWinner.addValue(apiName, attribute.convert(res));
                            retainFieldsData.forEach((k, v) -> {
                                final AttributeDefinition retainedAttribute = entityDefinition.getAttribute(k);
                                String retainFieldApiName = retainedAttribute.getApiName();
                                projectedWinner.addValue(retainFieldApiName, retainedAttribute.convert(v));
                                overriddenFields.add(k);
                            });
                        } else {
                            projectedWinner.addValue(apiName, attribute.convert(result));
                        }
                    }
                    break;
                }
                case NEVER:
                    Object result = evaluate(generatedBody, graphContext, Object.class);
                    if (result instanceof Map) {
                        Map<String, Object> retainFieldsData = (Map<String, Object>) ((Map<String, Object>) result).getOrDefault(MergeValueSelector.RETAINFIELDS, Map.of());
                        retainFieldsData.forEach((k, v) -> {
                            overriddenFields.add(k);
                        });
                    }
                    break;
                default:
                    break;
            }
        });
        //TODO: Fix non-overrides
        entityDefinition.getAttributes().forEach(attributeDefinition -> {
            if (!overriddenFields.contains(attributeDefinition.getId())) {
                var mergePolicy =
                        new WinningAttributeOverride()
                                .setAttributeId(attributeDefinition.getId())
                                .setValueSelectionPolicy(advancedDedupeConfig.getDefaultWinnerValueSelectionPolicy())
                                .setOverridePolicy(advancedDedupeConfig.getDefaultWinnerOverridePolicy());
                String apiName = attributeDefinition.getApiName();
                Object mergedValue = mergePolicy.getValueSelectionPolicy().apply(apiName, winner, losers, Map.of());
                switch (mergePolicy.getOverridePolicy()) {
                    case ALWAYS:
                        projectedWinner.addValue(apiName, mergedValue);
                        break;
                    case WHEN_BLANK:
                        if (StringUtils.isBlank(projectedWinner.getValueAsString(apiName))) {
                            projectedWinner.addValue(apiName, mergedValue);
                        }
                        break;
                    default:
                        break;
                }
            }
        });
        return projectedWinner;
    }

    public EntityData merge(AdvancedDedupeConfig advancedDedupeConfig, EntityData mergedWinner, EntityData incomingRecord, List<EntityData> candidates, EntityDefinition entityDefinition) {
        return mergedWinner;
    }

}

@Data
@AllArgsConstructor
@Accessors(chain = true)
class FlattenedIdMapping {
    String syncariId;
    String entityName;
    String connectorId;
    String externalEntityDefinitionId;
    String externalRecordId;
    Date createdAt;
    long recordLastModifiedAt;
    boolean isDisconnected;


    public static Stream<FlattenedIdMapping> fromIdMapping(IdMapping idMapping, EntityData entityData){
        return  idMapping.getMappings().stream().map(m-> new FlattenedIdMapping(idMapping.getSyncariId(),
                idMapping.getEntityName(),m.getConnectorId(),m.getEntityDefinitionId(),
                m.getEntityId(),idMapping.getCreatedAt(),entityData.getLastModified(), m.isDisconnected()));

    }
}