package com.syncari.core.functions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.MongoClient;
import com.syncari.connector.AttachRecordData;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.EntityData;
import com.syncari.connector.Operation;
import com.syncari.connector.data.SearchRequest;
import com.syncari.connector.exception.NonRetriableException;
import com.syncari.connector.exception.RetriableException;
import com.syncari.connector.service.def.DataService;
import com.syncari.core.DataTransformer;
import com.syncari.core.Features;
import com.syncari.core.SyncariContext;
import com.syncari.core.datatype.*;
import com.syncari.core.enrich.ClearbitService;
import com.syncari.core.enrich.ZoomInfoService;
import com.syncari.core.exception.NotFoundException;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.file.GCSFileManager;
import com.syncari.core.model.*;
import com.syncari.core.model.insights.dataset.Dataset;
import com.syncari.core.model.insights.dataset.Variable;
import com.syncari.core.model.insights.dataset.VariableValue;
import com.syncari.core.model.misc.NotificationType;
import com.syncari.core.model.pagination.Page;
import com.syncari.core.model.pagination.PageCursor;
import com.syncari.core.model.pagination.PageDirection;
import com.syncari.core.pipeline.*;
import com.syncari.core.pipeline.expression.Expression;
import com.syncari.core.pipeline.expression.PredicateParser;
import com.syncari.core.repositories.customer.EntityRepo;
import com.syncari.core.repositories.customer.IdMappingRepo;
import com.syncari.core.repositories.customer.StagedBatchRecordRepo;
import com.syncari.core.service.*;
import com.syncari.core.token.JtwigModelSanitizer;
import com.syncari.core.token.TokenHelper;
import com.syncari.core.utils.Criteria;
import com.syncari.core.utils.LookupCriteriaVisitor;
import com.syncari.core.utils.RedisLookupCriteriaVisitor;
import com.syncari.core.utils.RedisUtils;
import com.syncari.utils.I18n;
import com.syncari.utils.ThrowingSupplier;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.bson.BsonDocument;
import org.bson.types.ObjectId;
import org.jtwig.JtwigModel;
import org.jtwig.JtwigTemplate;
import org.jtwig.environment.Environment;
import org.jtwig.resource.reference.ResourceReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.syncari.core.pipeline.jtwig.functions.SideChannelFunction.extractResult;
import static com.syncari.utils.I18n.i18n;

@Component
@Slf4j
public class LookUpFunctions extends FunctionsBase {

    public static final int UPDATE_RECORDS_PAGE_SIZE = 100;

    private static final int UPDATE_SYNCARI_RECORD_SEARCH_ALERT_SIZE = 10000;

    @Autowired
    ReferenceDataService refDataService;
    @Autowired
    ClearbitService clearbitService;
    @Autowired
    EntityRepo entityRepo;
    @Autowired
    EntityRepoService entityRepoService;
    @Autowired
    SchemaService schemaService;
    @Autowired
    ConnectorService connectorService;
    @Autowired
    DataServiceFactory dataServiceFactory;
    @Autowired
    DataTransformer transformer;
    @Autowired
    IdMappingRepo idMappingRepo;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    TokenHelper tokenHelper;
    @Autowired
    NotificationService notificationService;
    @Autowired
    UserService userService;
    @Autowired
    ServiceCredentialService credentialService;
    @Autowired
    FeatureService featureService;
    @Autowired
    MappingGraphService graphService;

    @Autowired
    RequeueService requeueService;
    @Autowired
    DatasetService datasetService;
    /**
     * Get rid of this after replacing JTWig
     */
    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    StagedBatchRecordRepo stagedBatchRecordRepo;
    @Autowired
    TransactionLogService transactionLogService;
    @Autowired
    @Qualifier("defaultEmailService")
    EmailService emailService;
    @Autowired
    ErrorNotificationService errorNotificationService;
    @Autowired
    IdMappingService idMappingService;

    @Autowired
    RedisUtils redisUtils;

    @Autowired
    GCSFileManager gcsFileManager;

    public static final String RETURN_INPUT="__input__";

    private static Set<String> VALID_MULTIVALUED_FIELD_OPERATIONS =Set.of("replace","append","prepend","remove");



    /*@Lazy
    @Autowired(required = false)
    private Supplier<SyncContext> syncContext;
*/

    @Function
    public Object lookUpRefData(Object lookUpValue, FunctionCall functionCall, GraphContext context) {
        return doLookup(lookUpValue, functionCall, context);
    }

    @Function
    public Object lookUpRefDataOnEntity(Object input, FunctionCall functionCall, GraphContext context) {
        String value = tokenHelper.resolveTokens(context, getConfig("lookUpValue", functionCall, context));
        Object result = doLookup(value, functionCall, context);
        context.addResult(result);
        return input;
    }

    private Object doLookup(Object lookUpValue, FunctionCall functionCall, GraphContext context) {
        if (lookUpValue == null) {
            final String defaultValue = resolveDefaultValue(getConfig("defaultValue", functionCall, context), null, context);
            context.recordNodeInputs("defaultValue", defaultValue);
            return defaultValue;
        }
        String datasetId = getConfig("datasetId", functionCall, context);
        String lookUpKey = getConfig("lookUpKey", functionCall, context);
        String destinationFieldName = getConfig("destinationFieldName", functionCall, context);
        String operator = getConfig("operator", functionCall, context);
        operator = StringUtils.isBlank(operator) ? LookupReferenceDataFunction.EXACTMATCH : operator;
        Boolean ignoreCase = false;
        try {
            ignoreCase = Boolean.parseBoolean(getConfigOrDefault("ignoreCase", functionCall, false, context).toString());
        } catch (Exception e) {
        }
        if (String.class.isAssignableFrom(lookUpValue.getClass())) {
            String defaultValue = resolveDefaultValue(getConfig("defaultValue", functionCall, context), lookUpValue.toString(), context);
            Object result = refDataService.lookUp(datasetId, lookUpKey, lookUpValue.toString(), destinationFieldName, operator, ignoreCase, context.getCache());
            return result == null ? defaultValue : result;
        } else if (List.class.isAssignableFrom(lookUpValue.getClass())) {
            List valueList = (List) lookUpValue;
            List resultList = new ArrayList<>();
            for (Object v : valueList) {
                String defaultValue = resolveDefaultValue(getConfig("defaultValue", functionCall, context), v.toString(), context);
                Object result = refDataService.lookUp(datasetId, lookUpKey, v.toString(), destinationFieldName, operator, ignoreCase, context.getCache());
                resultList.add(result == null ? defaultValue : result);
            }
            return resultList;
        }
        return lookUpValue;
    }

    private String resolveDefaultValue(String defaultValue, String lookUpValue, GraphContext context) {
        if(defaultValue == null) return defaultValue;
        return RETURN_INPUT.equals(defaultValue) ? lookUpValue : tokenHelper.resolveTokens(context, defaultValue);
    }

    @Function
        public Object enrichPerson(Object defaultValue, FunctionCall functionCall, GraphContext context) {
        String attributeId = getConfig("emailField", functionCall, context);
        String enrichUsing = getConfig("enrichUsing", functionCall, context);
        String contextKey = "field_" + attributeId;
        var inputValue = context.containsKey(contextKey) && context.get(contextKey) != null ? context.get(contextKey).toString() : null;
        //By default don't enrich if value present
        boolean enrichOnEmptyValue = getConfigOrDefault("enrichOnEmptyValue", functionCall, true, context);
        //Don't enrich if flag is sent and input is not empty
        if (enrichOnEmptyValue && defaultValue != null && !StringUtils.isBlank(defaultValue.toString())) {
            return defaultValue;
        }
        if (StringUtils.isBlank(inputValue)) {
            return defaultValue;
        }
        String additionalEnrichUsing = getConfig("additionalEnrichUsing", functionCall, context);
        String additionalEnrichFieldId = getConfig("additionalEnrichField", functionCall, context);
        String addtionalContextKey = "field_" + additionalEnrichFieldId;
        var additionalInputValue = context.containsKey(addtionalContextKey) && context.get(addtionalContextKey) != null ? context.get(addtionalContextKey).toString() : null;

        String returnFieldName = getConfig("lookUpKey", functionCall, context);
        String serviceId = getConfig("serviceId", functionCall, context);
        Object enrichedValue = null;
        // check serviceId if its ServiceCredential or Connector?
        // TODO: Remove ServiceCredential check once we migrate clearbit to new pattern

        Optional<Connector> connector = serviceId == null ? Optional.empty() : context.cache(serviceId, () -> connectorService.find(serviceId));
        if (!connector.isEmpty()) {
            Connector enrichConnector = connector.get();
            connectorService.refreshAuthentication(enrichConnector);
            LookupService service = dataServiceFactory.getLookupService(enrichConnector.getMetadata());
            SearchCriteria criteria = new SearchCriteria();
            criteria.setMetaFilters(Map.of("lookupEntity", "contact", "lookupField", returnFieldName));
            criteria.and(enrichUsing, inputValue);
            if (service instanceof ZoomInfoService && StringUtils.isNotBlank(additionalEnrichUsing) && StringUtils.isNotBlank(additionalInputValue)) {
                criteria.and(additionalEnrichUsing, additionalInputValue);
            }
            try {
                LookupData data = service.lookup(transformer.toConnectorInfo(enrichConnector), criteria);
                enrichedValue = data.getValue(returnFieldName);
            } catch (Exception e){
                log.error("Error Enriching Contact using {}", enrichConnector.getName());
                handleEnrichException(() -> {throw e;}, enrichConnector.getName(), enrichConnector.getId(), context);
                enrichedValue = null;
            }
        }else {
            ServiceCredential serviceCred = credentialService.getCredentials(serviceId)
                    .orElseThrow(() -> new NotFoundException(ServiceCredential.class, "id", serviceId));
            try {
                enrichedValue = clearbitService.lookUpLead(inputValue, returnFieldName, serviceId);
            } catch (Exception e){
                log.error(String.format("Error Enriching Contact using %s", serviceCred.getName()));
                handleEnrichException(() -> {throw e;}, serviceCred.getName(), serviceId, context);
            }
        }
        return enrichedValue == null || StringUtils.isBlank(enrichedValue.toString()) ? defaultValue : enrichedValue;
    }

    @Function
    public Object enrichCompany(Object defaultValue, FunctionCall functionCall, GraphContext context) {
        String attributeId = getConfig("domainField", functionCall, context);
        String contextKey = "field_" + attributeId;
        var inputValue = context.containsKey(contextKey) && context.get(contextKey) != null ? context.get(contextKey).toString() : null;

        // By default don't enrich if value present
        boolean enrichOnEmptyValue = getConfigOrDefault("enrichOnEmptyValue", functionCall, true, context);
        // Don't enrich if flag is sent and input is not empty
        if (enrichOnEmptyValue && (defaultValue != null) && !StringUtils.isBlank(defaultValue.toString())) {
            return defaultValue;
        }
        if (StringUtils.isBlank(inputValue)) {
            return defaultValue;
        }
        String returnFieldName = getConfig("lookUpKey", functionCall, context);
        String serviceId = getConfig("serviceId", functionCall, context);
        String enrichUsing = getConfig("enrichUsing", functionCall, context);
        Object enrichedValue = null;

        // TODO: Remove ServiceCredential check once we migrate clearbit to new pattern
        Optional<Connector> connector = serviceId == null ? Optional.empty() : connectorService.find(serviceId);
        if (!connector.isEmpty()) {
            Connector enrichConnector = connector.get();
            LookupService service = dataServiceFactory.getLookupService(enrichConnector.getMetadata());
            SearchCriteria criteria = new SearchCriteria();
            criteria.setMetaFilters(Map.of("lookupEntity", "company", "lookupField", returnFieldName));
            criteria.and(enrichUsing, inputValue);
            try {
                LookupData data = service.lookup(transformer.toConnectorInfo(enrichConnector), criteria);
                enrichedValue = data.getValue(returnFieldName);
            } catch (Exception e) {
                log.error(String.format("Error Enriching Company using %s", enrichConnector.getName()));
                handleEnrichException(() -> {throw e;}, enrichConnector.getName(), enrichConnector.getId(), context);
                enrichedValue = null;
            }
        }else {
            ServiceCredential serviceCred = credentialService.getCredentials(serviceId)
                    .orElseThrow(() -> new NotFoundException(ServiceCredential.class, "id", serviceId));
            try {
                if ("ip".equalsIgnoreCase(enrichUsing)) {
                    enrichedValue = clearbitService.lookUpCompanyByIPAddress(inputValue, returnFieldName, serviceId);
                } else {
                    enrichedValue = clearbitService.lookUpCompany(inputValue, returnFieldName, serviceId);
                }
            } catch (Exception e){
                log.error(String.format("Error Enriching Company using %s", serviceCred.getName()));
                handleEnrichException(() -> {throw e;}, serviceCred.getName(), serviceId, context);
            }
        }
        return enrichedValue == null ? defaultValue : enrichedValue;
    }

    private <T> T handleEnrichException(ThrowingSupplier<T> supplier, String enrichService, String serviceId, GraphContext context){
        try {
            return supplier.throwingGet();
        } catch(NonRetriableException | RetriableException e){
            log.error(e.getMessage(), e);
            String key = serviceId + "_" + e.getErrorCode();
            Set users = Stream.of(userService.getAdmins(), userService.getSuperAdmins()).flatMap(Collection::stream).collect(Collectors.toSet());
            sendEnrichErrorNotification(enrichService, key, e.getMessage(), context, new ArrayList<>(users), context.isTestMode() || context.isSimulationMode());

        } catch (Exception e) {
            log.error(e.getMessage());
            String key = serviceId + "_" + e.getMessage();
            Set users = Stream.of(userService.getAdmins(), userService.getSuperAdmins()).flatMap(Collection::stream).collect(Collectors.toSet());
            sendEnrichErrorNotification(enrichService, key, e.getMessage(), context, new ArrayList<>(users), context.isTestMode() || context.isSimulationMode());
        }
        return null;
    }

    private void sendEnrichErrorNotification(String enrichService, String key, String errorMsg, GraphContext context, List<User> users, boolean isTest){
        String subject = I18n.i18n("enrich_error_subject", enrichService, SyncariContext.getInstance().getName(), SyncariContext.getOrganziation().getName());
        String body = I18n.i18n("enrich_error_body", enrichService,
                context.getGraph().getName(),
                context.getSyncariEntity().getDisplayName(),
                StringUtils.isBlank(context.getCurrentSyncariId()) ? "UNKNOWN" : context.getCurrentSyncariId(),
                errorMsg);
        NotificationService.NotificationFrequency frequency = isTest ? NotificationService.NotificationFrequency.IMMEDIATE : NotificationService.NotificationFrequency.DAILY;
        boolean sent = false;
        for(User user: users){
            Notification notif = new Notification(key, subject, body, NotificationType.WARN, user.getId());
            sent = notificationService.sendWithFrequency(notif, frequency) || sent;
        }
        //errorNotificationService.sendErrorNotification(ErrorCategory.SYNAPSE, ErrorPriority.P2, key, subject, body);
        // if a single notification is sent then send the support email too
        if(sent) {
            emailService.sendSupportEmail(subject, body);
        }
    }

    @Deprecated
    @Function
    public FunctionResult lookUpSyncariRecord(Object input, FunctionCall functionCall, GraphContext context) {
        String syncariEntityDefId = getConfig("syncariEntityDefId", functionCall, context);
        String searchFieldId = getConfig("searchFieldId", functionCall, context);
        String inputFieldId = getConfig("inputFieldId", functionCall, context);
        String lookupValueKey = "field_" + inputFieldId;
        Object lookupValue = context.get(lookupValueKey);
        EntityDefinition entity = schemaService.getEntity(syncariEntityDefId);
        AttributeDefinition attribute = entity.getAttribute(searchFieldId);
        if (attribute == null) {
            log.warn("Could not find attribute with null id in lookUpSyncariRecord");
            return null;
        }

        Map<String, Object> searchFieldNameValues = new HashMap<>();
        searchFieldNameValues.put(attribute.getApiName(), lookupValue);
        SearchCriteria criteria = new SearchCriteria().setSearchFieldNameValues(searchFieldNameValues);
        Slice<EntityData> searchResult = entityRepo.search(entity, criteria, Pageable.unpaged());
        log.debug("Results of lookup syncari for syncariEntityId {} searching on {} using input {} with value {} and found records with ids {}"
                ,syncariEntityDefId,searchFieldId,inputFieldId,lookupValue,
                searchResult.isEmpty() ? "" : searchResult.getContent().stream().map(EntityData::getId).collect(Collectors.toList()));

        Object result = searchResult.isEmpty() ? null : searchResult.getContent().get(0);
        return new FunctionResult(input, ObjectType.VALUE, result);
    }
    protected Object evaluateFilter(String filterBody,FunctionCall call,GraphContext context){
        try {
            ResourceReference resource = new ResourceReference(
                    ResourceReference.STRING,
                    filterBody

            );
            JtwigTemplate jtwigTemplate = new JtwigTemplate(applicationContext.getBean(Environment.class), resource);
            JtwigModelSanitizer sanitizer = JtwigModelSanitizer.newModel(context);
            JtwigModel model = JtwigModel.newModel(sanitizer.getValues());

            Object result = jtwigTemplate.render(model);
            Object extractedResult = extractResult(result);
            if(extractedResult!=null && extractedResult instanceof FunctionResult){
                FunctionResult functionResult = FunctionResult.class.cast(extractedResult);
                return functionResult.getResult();
            }
            return  extractedResult;
        }catch(TerminateExecutionPathException e){
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            FunctionSideChannel.remove();
        }

    }
    @Function
    public FunctionResult advancedLookUpSyncariRecord(Object input, FunctionCall functionCall, GraphContext context) {
        EntityData incomingRecord = (EntityData) input;
        String syncariEntityDefId = getConfig("syncariEntityDefId", functionCall, context);
        Map<String, Object> predicates = getConfig("predicate", functionCall, context);
        boolean dontMatchBlank = getDontMatchBlankFlag(functionCall, context);
        Boolean findAll = getConfigOrDefault("findAll", functionCall, false, context);
        try {
            int pageSize = findAll ? 1000 : 1;
            EntityDefinition entity = context.cache(syncariEntityDefId, () -> schemaService.getEntity(syncariEntityDefId));
            Expression expression = MapUtils.isEmpty(predicates) ? Expression.notEmpty(Expression.var("lastModified")) :
                    new PredicateParser(StringUtils.EMPTY).fromMap(predicates);

            List<LookupCriteriaVisitor.Sort> sorts = toSortList(functionCall, "lastModified", "desc", context);

            boolean useCache = entityRepo.useCache(entity) && !context.isTestMode() && !context.isSimulationMode() ?
                    context.cache(entity.getApiName() + "_index_status",
                            () -> redisUtils.indexStatus(entity.getApiName())) : false;

            Optional<Criteria> redisCriteria = Optional.of(new RedisLookupCriteriaVisitor(context, expression, tokenHelper, entity, sorts));

            Optional<Criteria> mongoCriteria = Optional.of(new LookupCriteriaVisitor(context, expression, tokenHelper,
                        entity.getIdToAttributes(),sorts, (key) -> entityRepo.hasCaseInsensitiveIndexOnField(entity, key)));
            boolean foundEmptyValuedPredicates = mongoCriteria.map(c->((LookupCriteriaVisitor)c).foundEmptyValuedPredicates()).orElse(true);

			List<EntityData> search = (dontMatchBlank && foundEmptyValuedPredicates)
                    ? List.of() : entityRepo.searchWithFallback(entity, redisCriteria, mongoCriteria, useCache, pageSize).getRecords();
            boolean countRecords =  functionCall.getConfig("count", BooleanType.VALUE).orElse(false);
            Long count=!(dontMatchBlank && foundEmptyValuedPredicates) && countRecords  ? entityRepo.countWithFallback(entity, redisCriteria, mongoCriteria, useCache):null;

            log.debug("Results of syncari lookup for syncariEntityId {} searching with predicate {} and found records with ids {} using inputRecord Id {} "
                    ,syncariEntityDefId, predicates,
                    search.isEmpty() ? "" : search.stream().map(EntityData::getId).collect(Collectors.toList()), incomingRecord.getId());

            Object result = null;
            if (!search.isEmpty()) {
                result = search.get(0);
                if (findAll) {
                    updateRecordsWithIdMapping(context, entity, search);
                    context.set("allPreviousLookupRecords", search);
                    context.set("All Lookup Records From " + context.getCurrentNode().getName(), search);
                } else {
                    updateRecordsWithIdMapping(context, entity, List.of(search.get(0)));
                }
            }
            return new FunctionResult(input, ObjectType.VALUE, result, count);
        } catch (Exception e) {
            log.error("Error in advancedLookUpSyncariRecord", e);
        }
        return new FunctionResult(input, ObjectType.VALUE, null);
    }

    private boolean getDontMatchBlankFlag(FunctionCall functionCall, GraphContext context) {
        final String dontMatchBlank = "dontMatchBlank";
        var flag = functionCall.getConfig(dontMatchBlank);
        final boolean finalValue = flag != null && flag instanceof Boolean ? (Boolean) flag : false;
        context.recordNodeInputs(dontMatchBlank, finalValue);
        return finalValue;
    }

    @Function
    public FunctionResult lookUpExternalRecord(Object input, FunctionCall functionCall, GraphContext context) {
        String synapseId = getConfig("synapseId", functionCall, context);
        String query = getConfig("query", functionCall, context);
        String positionalParams = getConfig("positionalParams", functionCall, context);
        List params = new ArrayList<>();
        if (!StringUtils.isBlank(positionalParams)) {
            params = Arrays.asList(positionalParams.split(","));
            params = (List) params.stream().map(p -> tokenHelper.resolveTokensObject(context, p.toString())).collect(Collectors.toList());
        }
        try {
            Connector connector = connectorService.find(synapseId).get();
            DataService dataService = dataServiceFactory.getDataService(connector.getMetadata());
			ConnectorInfo connectorInfo = transformer.toConnectorInfo(connector);
			List<EntityData> search = dataService
					.search(new SearchRequest().setConnector(connectorInfo).setQuery(query).setParams(params).setStorage(gcsFileManager));

            // escape key values with period, so that tokens using this key will work correctly
            search.stream().forEach(entity -> {
                var originalValues = entity.getValues();
                Map<String, Object> newValues = new HashMap<>();
                originalValues.forEach((k,v) -> newValues.put(k.replaceAll("\\.", "\\\\."), v));
                entity.setValues(newValues);
            });

			log.debug("Results of lookup external for searching with condition {} using and found records with ids {}",
					query,
					search.isEmpty() ? "" : search.stream().map(EntityData::getId).collect(Collectors.toList()));

			context.put("Records from " + context.getCurrentNode().getName(), search);
    		return new FunctionResult(input, ObjectType.VALUE, search);
    	} catch (Exception e) {
    		log.error("Error in lookUpExternalRecord {}", ExceptionUtils.getStackTrace(e));
    		throw new SyncariValidationException(e.getMessage());
    	}
    }

    protected List<LookupCriteriaVisitor.Sort> toSortList(FunctionCall functionCall, String defaultSortField, String defaultSortOrder, GraphContext context) {
        final String sortFieldsConfig = "sortFields";
        List<Map<String, Map<String, String>>> sortFields = (List<Map<String, Map<String, String>>>) functionCall.getConfig()
                .getOrDefault(sortFieldsConfig, List.of());
        context.recordNodeInputs(sortFieldsConfig, sortFields);
        List<LookupCriteriaVisitor.Sort> sorts = sortFields.stream().map(s -> new LookupCriteriaVisitor
                        .Sort(s.get("sortField").get("value"), s.get("sortDirection").get("value")))
                .collect(Collectors.toList());
        return sorts.isEmpty() ? List.of(new LookupCriteriaVisitor.Sort(defaultSortField, defaultSortOrder)) : sorts;
    }

    /**
     * Links the incoming record to an existing syncari record, by creating an idmapping.
     * Behavior
     * If a syncari record is found for linking:
     *    If the record is not already connected to another external record for the same source entity as this,
     *    links the incoming external record to the existing syncari record.
     *    If the incoming external record was connected to some other syncari record, that is disconnected, and
     *    reconnected to the syncari record found here.
     *    Does nothing in all other cases
     * @param input
     * @param functionCall
     * @param context
     * @return
     */
    @Function
    public Object advancedAttachRecord(Object input, FunctionCall functionCall, GraphContext context) {
        EntityData incomingRecord = (EntityData) input;
        Optional<EntityData> existingRecord = Optional.ofNullable((EntityData) context.get("existing"));
        Map<String, Object> predicates = getConfig("attachPredicate", functionCall, context);
        try {
            EntityDefinition syncariEntity = context.cache(context.getGraph().getTargetId(), () -> schemaService.getEntity(context.getGraph().getTargetId()));
            EntityDefinition externalEntity = context.cache(incomingRecord.getConnectorId() + "_" + incomingRecord.getName(), () -> schemaService.getEntity(incomingRecord.getConnectorId(), incomingRecord.getName()));
            List<LookupCriteriaVisitor.Sort> sorts = toSortList(functionCall, "lastModified", "desc", context);
            Expression expression = new PredicateParser(StringUtils.EMPTY).fromMap(predicates);

            Optional<LookupCriteriaVisitor> mongoCriteria = Optional.of(new LookupCriteriaVisitor(context, expression, tokenHelper,
                    syncariEntity.getIdToAttributes(), sorts, (key) -> entityRepo.hasCaseInsensitiveIndexOnField(syncariEntity, key)));
            boolean foundEmptyValuedPredicates = mongoCriteria.map(c -> c.foundEmptyValuedPredicates()).orElse(true);

            // search to be performed only when the record has changed
/*            boolean shouldEvaluate = true;
            if (mongoCriteria.isPresent() && context.getCurrentNode() != null) {
                // calculate hash for incoming record and validate against existing
                String incomingHash = DigestUtils.md5Hex(mongoCriteria.get().createCriteria().toString());
                String existingHash = "";
                Map<String, AttachRecordData> attachRecordDataMap = new HashMap<>();
                if (existingRecord.isPresent()) {
                    attachRecordDataMap = existingRecord.get().getAttachRecordData();
                    AttachRecordData attachData = attachRecordDataMap.get(context.getCurrentNode().getId());
                    if (attachData != null) {
                        existingHash = attachData.getHashValue();
                    }
                }
                shouldEvaluate = !StringUtils.equals(incomingHash, existingHash);
                // set attachData

                // clear unused AttachRecordData (for which the nodeIds don't exists)
                var nodeIds = context.getGraph().getNodes().stream().map(n -> n.getId()).collect(Collectors.toSet());
                Iterator<String> iterator = attachRecordDataMap.keySet().iterator();
                while(iterator.hasNext()){
                    String key = iterator.next();
                    if(!nodeIds.contains(key)){
                        attachRecordDataMap.remove(key);
                    }
                }

                AttachRecordData attachData = new AttachRecordData();
                attachData.setNodeId(context.getCurrentNode().getId());
                attachData.setBatchId(context.getCurrentBatch().getCurrentBatchId());
                attachData.setPipelineId(context.getGraph().getId());
                attachData.setSyncariEntityId(syncariEntity.getId());
                attachData.setFields(mongoCriteria.get().getPredicateFields());
                attachData.setHashValue(incomingHash);
                attachRecordDataMap.put(context.getCurrentNode().getId(), attachData);
                incomingRecord.setAttachRecordData(attachRecordDataMap);
                incomingRecord.addValue("attachRecordData", attachRecordDataMap);
            }
            // if attach record evaluation is not needed then return back the incoming record as is
            if (!shouldEvaluate) {
                log.debug("Skipping db evaluation as the hash matches for incoming attachCriteria in node {} for source record id {}", context.getCurrentNode().getId(), incomingRecord.getId());
                return incomingRecord;
            }*/

            List<EntityData> search = foundEmptyValuedPredicates? List.of(): entityRepo.search(syncariEntity, mongoCriteria, 1).getRecords();
            EntityData result = search.isEmpty() ? null : search.get(0);
            String oldSyncariIdToBeDeleted = incomingRecord.getSyncariEntityId();
            if(result !=null) {
                Optional<IdMapping> existing = idMappingRepo.findBySyncariId(syncariEntity.getApiName(),result.getId());
                Optional<IdMapping> old = idMappingRepo.findByExternalId(syncariEntity.getApiName(),incomingRecord.getConnectorId(),
                        externalEntity.getId(),incomingRecord.getId());

                existing.ifPresentOrElse(e -> {
                    Optional<IdMapping.Mapping> existingMapping = e.findMapping(incomingRecord.getConnectorId(), externalEntity.getId(),incomingRecord.getId());
                    if(existingMapping.isPresent()) return ;//do nothing, correct mapping already exists
                    Boolean connectedToAnotherRecord = e.getMapping(incomingRecord.getConnectorId(), externalEntity.getId()).map(em -> !em.getEntityId().equals(incomingRecord.getId())).orElse(false);
                    if(connectedToAnotherRecord) return;//We found a matching syncari record, but its already connected to
                    // another external record of the same entity/
                    //so we skip the linking, because its a duplicate and needs to be managed by dedupe/merge, if enabled.

                    old.ifPresentOrElse(oldMapping->{   /// --- Attaching record on UPDATE case ---------
                        //there is an existing id mapping for incoming record, but its not the same as the result syncari id
                        if(!oldMapping.getSyncariId().equals(result.getId())){
                            //so we remove that old mapping...
                            oldMapping.removeMapping(incomingRecord.getConnectorId(), externalEntity.getId(), incomingRecord.getId());
                            if(!oldMapping.hasConnectedMappings()){
                                entityRepoService.deleteRecord(oldMapping.getEntityName(),oldMapping.getSyncariId(),false);
                            }else {
                                idMappingRepo.save(oldMapping);
                                // remove external Id from entity repo
                                disconnectExt(oldMapping, syncariEntity, externalEntity);
                            }
                            log.info("Removed an old id mapping between Syncari Record {}({}) and external record {}({})",
                                    oldMapping.getSyncariId(), syncariEntity.getApiName(), incomingRecord.getId(), incomingRecord.getName());
                            //...and add the new mapping
                            addNewIdMapping(incomingRecord, syncariEntity, externalEntity, e);
                              /*
                                Modify the textContext to update the changedSyncariId
                             */
                            // for records attached, set the external id in Syncari
                            connectExt(e, syncariEntity, externalEntity);
                            modifyTestContext(oldSyncariIdToBeDeleted,context,incomingRecord.getSyncariEntityId());
                        }
                    },()->{ /// ----- Attaching Record on CREATE case --------
                        //no existing idmapping present, nor present in result's id mapping. So add new mapping. Thiss may result in reconnecting an exiting, disconnected id mapping
                        //.. for example, when the deleted record is restored in the end system
                        addNewIdMapping(incomingRecord, syncariEntity, externalEntity, e);
                        // for records attached, set the external id in Syncari
                        connectExt(e, syncariEntity, externalEntity);
                        modifyTestContext(oldSyncariIdToBeDeleted,context,incomingRecord.getSyncariEntityId());
                    });

                }, () -> {
                    log.warn("Found a matching syncari record with id {}, but it has no idMapping. Creating a new id mapping {}{}",
                            result.getId(),incomingRecord.getId(), incomingRecord.getName());
                    IdMapping idMapping = new IdMapping().setEntityName(syncariEntity.getApiName())
                            .setSyncariId(result.getSyncariEntityId());
                    idMapping.addMapping(incomingRecord.getConnectorId(), incomingRecord.getId(), externalEntity.getId());
                    idMappingRepo.save(idMapping);
                    log.info("Attached external record {}({}) to syncari record {}({})", incomingRecord.getId(), externalEntity.getApiName(),
                            idMapping.getSyncariId(), syncariEntity.getApiName());
                    incomingRecord.setSyncariEntityId(idMapping.getSyncariId());
                    modifyTestContext(oldSyncariIdToBeDeleted,context,incomingRecord.getSyncariEntityId());
                    incomingRecord.setNew(false);
                    // for records attached, set the external id in Syncari
                    connectExt(idMapping, syncariEntity, externalEntity);
                });
                modifyTempVarContext(oldSyncariIdToBeDeleted, context, incomingRecord.getSyncariEntityId());
                // update syncari record in context as the incoming record is being attached to a different syncari record
                context.updateSyncariRecord(result);
            }else if (!foundEmptyValuedPredicates){
                //See if there was another attach record result matching the same criteria in this batch.
                //If two synapses are connected and we get two records
                //we have not seen yet in the same batch, and these two are supposed to be the same,
                //we will end up creating two different id-mappings (because the syncari record is saved in ExecuteFieldPipeline - too late)
                BsonDocument searchCriteria = mongoCriteria.map(r -> r.createCriteria()).orElse(new BsonDocument()).toBsonDocument(BsonDocument.class, MongoClient.getDefaultCodecRegistry());
                Optional<AttachRecordResult> cachedResult = retrieveAttachRecordResult(searchCriteria, context);
                cachedResult.ifPresent(c->{
                    if(c.shouldAttach(incomingRecord)){
                        incomingRecord.setSyncariEntityId(c.getSyncariRecordId());
                        modifyTestContext(oldSyncariIdToBeDeleted,context,incomingRecord.getSyncariEntityId());
                        log.info("Attached to syncari record {}({}), for input record {}({}), because we found a cached attach record result"
                                ,c.getSyncariRecordId(),syncariEntity.getApiName(), incomingRecord.getId(),incomingRecord.getName(), incomingRecord);

                    }
                });
                //record resulting syncari id against the search criteria. This will be used in the current batch,
                //If another incoming record from another synapse has the same search criteria, and is not in syncari already
                recordSearchResult(searchCriteria, incomingRecord,context);
                modifyTempVarContext(oldSyncariIdToBeDeleted, context, incomingRecord.getSyncariEntityId());
            }
        } catch (Exception e) {
            log.error("Error in advancedAttachRecord", e);
        }

        return input;
    }

    private void connectExt(IdMapping mapping, EntityDefinition syncariEntity, EntityDefinition externalEntity) {
        if(mapping.getMapping(externalEntity.getId()).isPresent()) {
            List<EntityData> recordsToBeUpdated = new ArrayList<>();
            EntityData d = new EntityData(syncariEntity.getApiName()).setSyncariEntityId(mapping.getSyncariId());
            entityRepoService.connectExternalId(syncariEntity, d, externalEntity.getId(), Optional.empty(), mapping.getMapping(externalEntity.getId()).get().getEntityId());
            recordsToBeUpdated.add(d);
            entityRepo.updateValues(syncariEntity, recordsToBeUpdated);
        }
    }

    private void disconnectExt(IdMapping mapping, EntityDefinition syncariEntity, EntityDefinition externalEntity) {
        EntityData d = new EntityData(syncariEntity.getApiName()).setSyncariEntityId(mapping.getSyncariId());
        entityRepoService.disconnectExternalId(syncariEntity, d, externalEntity.getId(), Optional.empty(), Optional.empty());
        entityRepo.updateValues(syncariEntity, List.of(d));
    }

    private void modifyTempVarContext(String oldSyncariIdToBeDeleted, GraphContext context, String updatedSyncariEntityId) {
        List<String> tempVariablesKeysTobeChanged = context.getTempVariables().keySet().stream().filter(k -> k.contains(oldSyncariIdToBeDeleted)).collect(Collectors.toList());
        if (CollectionUtils.isNotEmpty(tempVariablesKeysTobeChanged)){
            Map<String, Object> allTempVariables = context.getTempVariables();
            tempVariablesKeysTobeChanged.forEach(key -> {
                if (key.contains(oldSyncariIdToBeDeleted)){
                    Object val = allTempVariables.get(key);
                    String keyTobeAdded = key.replaceFirst(oldSyncariIdToBeDeleted, updatedSyncariEntityId);
                    allTempVariables.remove(key);
                    allTempVariables.put(keyTobeAdded, val);
                }
            });
        }
    }

    private void modifyTestContext(String oldSyncariIdToBeDeleted, GraphContext context, String updatedSyncariEntityId) {
        if(context.getTestContext().getDataSnapshot().containsKey(oldSyncariIdToBeDeleted)){
            Map<String, NodeData> existingVisitedNodes = context.getTestContext().getDataSnapshot().get(oldSyncariIdToBeDeleted);
            context.getTestContext().getDataSnapshot().put(updatedSyncariEntityId,existingVisitedNodes);
            context.getTestContext().getDataSnapshot().remove(oldSyncariIdToBeDeleted);
        }
    }
    @Function
    public Object updateSyncariRecordsOnField(Object input, FunctionCall functionCall, GraphContext context) {
        return updateSyncariRecords(input, functionCall, context);
    }

    @Function
    public Object updateSyncariRecords(Object input, FunctionCall functionCall, GraphContext context) {
        Map<String, Object> predicates = getConfig("predicate", functionCall, context);
        String syncariEntityDefId = getConfig("syncariEntityDefId", functionCall, context);

        EntityDefinition syncariEntity = context.cache(syncariEntityDefId, () -> schemaService.getEntity(syncariEntityDefId));
        List<LookupCriteriaVisitor.Sort> sorts = toSortList(functionCall, "_id", "asc", context);

        Expression expression = new PredicateParser(StringUtils.EMPTY).fromMap(predicates);

        Optional<Criteria> redisCriteria = Optional.of(new RedisLookupCriteriaVisitor(context, expression, tokenHelper, syncariEntity, List.of()));

        Optional<Criteria> mongoCriteria = Optional.of(new LookupCriteriaVisitor(context, expression, tokenHelper,
                syncariEntity.getIdToAttributes(), sorts, (key) -> entityRepo.hasCaseInsensitiveIndexOnField(syncariEntity, key)));

        boolean useCache = entityRepo.useCache(syncariEntity) && !context.isTestMode() && !context.isSimulationMode() ?
                context.cache(syncariEntity.getApiName() + "_index_status",
                () -> redisUtils.indexStatus(syncariEntity.getApiName())) : false;

        PageCursor cursor = new PageCursor(null, PageDirection.next, UPDATE_RECORDS_PAGE_SIZE);
        Page<EntityData> search;
        int updated=0;
        int searched=0;
        Connector syncariConnector = connectorService.getSyncariConnector();
        EntityData entity = (EntityData) context.get("record"); // current record being processed in the pipeline
        List<EntityData> recordsToRequeue = new ArrayList<>();
        while(!(search = entityRepo.searchWithFallback(syncariEntity, redisCriteria, mongoCriteria, useCache, cursor)).getRecords().isEmpty()){
            List<TransactionLog> transactionLogs = new ArrayList<>();
            List<EntityData> updatedRecords = new ArrayList<>();
            search.getRecords().stream().forEach(record -> {
                context.put("current", record);
                Set<String> attachPredicateFields = new HashSet<>();

                // get all attachRecordData and see which syncariEntityId matches
                List<AttachRecordData> matchingAttachData = record.getAttachRecordData().values().stream()
                        .filter(a -> a.getSyncariEntityId().equals(syncariEntity.getId()))
                        .collect(Collectors.toList());
                log.debug("Found {} matching attach record data. {}", matchingAttachData.size(), matchingAttachData);
                matchingAttachData.forEach(a -> {
                    attachPredicateFields.addAll(a.getFields());
                });

                List<Update> updates = toUpdates(syncariEntity, context, functionCall);
                TransactionLog transactionLog = new TransactionLog().setOperation(Operation.update)
                        .setEntityName(syncariEntity.getApiName())
                        .setEntityId(syncariEntity.getId())
                        .setOccurredAt(System.currentTimeMillis())
                        .setSyncariId(record.getSyncariEntityId())
                        .addSource(syncariConnector.getId(), syncariConnector.getName(), syncariEntity.getId(), entity != null ? entity.getSyncariEntityId() : "", System.currentTimeMillis())
                        .setAdditionalInfo(Map.of("notes", String.format("Updated by pipeline %s", context.getGraph().getName()), "graphId", context.getGraph().getId()));
                boolean shouldRequeue = false;
                for(Update update: updates){
                    Object newValue = update.applyTo(record.getValue(update.getApiName()));
                    if(record.hasChanges(update.getApiName(),newValue)) {
                        transactionLog.addChange(new FieldChange().setFieldId(update.getAttributeId()).setApiName(update.getApiName())
                                .setOldValue(record.getValue(update.getApiName())).setNewValue(newValue));
                        log.debug("UpdateSyncariRecord: OldValue=[{}], NewValue=[{}]", record.getValue(update.getApiName()), newValue);
                        record.addValue(update.getApiName(), newValue);
                    }

                    // check if updated field belongs to attachRecord predicate
                    if (attachPredicateFields.contains(update.getApiName())) {
                        log.info("Syncari field {} is updated and belongs to attach record predicate", update.getApiName());
                        shouldRequeue = true;
                    }
                }
                if (transactionLog.hasChanges()) {
                    transactionLogs.add(transactionLog);
                    updatedRecords.add(record);
                }

                if(shouldRequeue){
                    // empty the attachRecordData for this record
                    // Be aggressive and cleanup all attachRecordData for now
                    log.debug("Requeuing syncariId {} for source processing in syncariEntity {}", record.getId(), syncariEntity.getApiName());
                    record.setAttachRecordData(new HashMap<>());
                    record.addValue("attachRecordData", new HashMap<>());
                    recordsToRequeue.add(record);
                }

                context.remove("current");
            });

            if (!updatedRecords.isEmpty()) {
                entityRepo.updateValues(syncariEntity, updatedRecords);
                var savedTransactions = transactionLogService.log(transactionLogs);
                entityRepoService.updateLastTransactionId(syncariEntity, savedTransactions, updatedRecords);
                final Set<String> mutatedRecordIds = context.cache("mutatedRecordIds_"+syncariEntity.getApiName(), () -> new HashSet<>());
                mutatedRecordIds.addAll(updatedRecords.stream().map(u->u.getSyncariEntityId()).collect(Collectors.toSet()));
            }

            // requeue record
            if(!recordsToRequeue.isEmpty()){
                //create requeue requests
                Map<String, List<String>> requeuedRecordMap = new HashMap<>();
                List<RequeueRequest> requeueRequests = new ArrayList<>();
                Optional<MappingGraph> graph = graphService.retrieveApprovedEntityGraph(syncariEntity.getId());
                // skip requeuing if published graph for corresponding syncari entty does not exist
                if(graph.isPresent()) {
                    List<IdMapping> idMappings = idMappingService.findBySyncariIds(syncariEntity.getApiName(), recordsToRequeue.stream().map(r -> r.getId()).collect(Collectors.toSet()));
                    idMappings.forEach(idMapping -> {
                        idMapping.getMappings().forEach(m -> {
                            // requeue only if the connected records do not have sink entity
                            // As the syncari record update would run through destination side and come back as an update
                            // If we requeue record while there is a sink then there is a possibility that next sync cycle the source will read non-updated record
                            // and syncari update will not run through destination side pipeline
                            if (!graph.get().isSink(m.getEntityDefinitionId())){
                                RequeueRequest requeueRequest = new RequeueRequest().setGraphId(graph.get().getId())
                                        .setEntityDefinitionId(m.getEntityDefinitionId())
                                        .setRecordId(m.getEntityId())
                                        .setRecordType(RequeueRequest.RecordType.SOURCE)
                                        .setRetryTimeLimit(ZonedDateTime.now().plusDays(7)) // set high retry limit to make sure record is processed by other pipeline
                                        .setEmailAddresses(List.of())
                                        .setRequeueReason(String.format("UpdateSyncariRecord on syncariId %s from pipeline %s has updated one (or more) attach criteria fields", idMapping.getSyncariId(), graph.get().getName()));
                                var recordList = requeuedRecordMap.getOrDefault(m.getEntityDefinitionId(), new ArrayList<>());
                                recordList.add(m.getEntityId());
                                requeuedRecordMap.put(m.getEntityDefinitionId(), recordList);

                                requeueRequests.add(requeueRequest);
                            }
                        });
                    });
                    log.info("Requeuing Records from UpdateSyncariRecord nodeId: {}. RecordMap: {}", context.getCurrentNode().getId(), requeuedRecordMap);
                    requeueService.requeue(requeueRequests);
                }
            }
            searched += search.getRecords().size();
            if(searched > UPDATE_SYNCARI_RECORD_SEARCH_ALERT_SIZE) {
                MappingNode currentNode = context.getCurrentNode();
                String name = currentNode != null ? currentNode.getName(): "Currentnode not found";
                log.error("UpdateSyncariRecords: searched more than {} records in node - {}", UPDATE_SYNCARI_RECORD_SEARCH_ALERT_SIZE, name);
            }
            updated+=updatedRecords.size();
            log.debug("UpdateSyncariRecords:Found {} records and updating {} records, total records {}", search.getRecords().size(), updatedRecords.size(), updated);
            cursor =  new PageCursor(search.getPageInfo().getEnd(), PageDirection.next, UPDATE_RECORDS_PAGE_SIZE);
        }
        context.put(syncariEntity.getApiName() + "_recordsUpdated", updated);
        return input;
    }

    @Function
    public Object insertRecordOnField(Object input, FunctionCall functionCall, GraphContext context) {
        return insertRecord(input, functionCall, context);
    }

    @Function
    public Object insertRecord(Object input, FunctionCall functionCall, GraphContext context) {
        String syncariEntityDefId = getConfig("syncariEntityDefId", functionCall, context);
        EntityDefinition syncariEntity = context.cache(syncariEntityDefId, () -> schemaService.getEntity(syncariEntityDefId));
        List<Update> updates = toChangeSet(syncariEntity, context, functionCall);
        long createdAt = Instant.now().toEpochMilli();
        String id = ObjectId.get().toHexString();
        Connector syncariConnector = connectorService.getSyncariConnector();
        EntityData entity = (EntityData) context.get("record");
        EntityData record = new EntityData(syncariEntity.getApiName()).setCreatedAt(createdAt).setLastModified(createdAt).setNew(true).setId(id).setSyncariEntityId(id);
        TransactionLog transactionLog = new TransactionLog().setOperation(Operation.create)
                .setEntityName(syncariEntity.getApiName())
                .setEntityId(syncariEntity.getId())
                .setOccurredAt(System.currentTimeMillis())
                .setSyncariId(record.getSyncariEntityId())
                .addSource(syncariConnector.getId(), syncariConnector.getName(), syncariEntityDefId, entity != null ? entity.getSyncariEntityId() : "", System.currentTimeMillis())
                .setAdditionalInfo(Map.of("notes", String.format("Updated by pipeline %s", context.getGraph().getName()), "graphId", context.getGraph().getId()));
        updates.forEach(update->{
            transactionLog.addChange(new FieldChange().setFieldId(update.getAttributeId()).setApiName(update.getApiName())
                    .setOldValue(record.getValue(update.getApiName())).setNewValue(update.getNewValue()));
            record.addValue(update.getApiName(),update.getNewValue());
        });
        EntityData saved=record;
        if (!record.getValues().isEmpty()) {
            saved = entityRepo.save(syncariEntity, record);
            var savedTxnLog = transactionLogService.log(transactionLog);
            // save the correct txn log id
            entityRepoService.updateLastTransactionId(syncariEntity, List.of(savedTxnLog), List.of(saved));
        }
        log.debug("insertyncariRecords:", saved);
        context.put("Record from "+context.getCurrentNode().getName(),saved);
        return input;
    }
    private List<Update> toUpdates(EntityDefinition syncariEntity, GraphContext context,FunctionCall functionCall) {
        List<Map<String, Map<String, String>>> sortFields = (List<Map<String, Map<String, String>>>) functionCall.getConfig()
                .getOrDefault("updateFields", List.of());
        Boolean rejectEmpty = getConfigOrDefault("rejectEmpty", functionCall, true, context);
        List<Update> updates = new ArrayList<>();
        for (Map<String, Map<String, String>> s : sortFields) {
            String attributeId = s.get("updateField").get("value");
            String newValue = s.get("newValue").get("value");
            String operation = s.getOrDefault("operation",Map.of()).getOrDefault("value","replace").toLowerCase();
            AttributeDefinition attribute = syncariEntity.getAttribute(attributeId);
            final Object resolvedValue = tokenHelper.resolveTokensObject(context, newValue);
            final Object typedValue = syncariEntity.getAttribute(attributeId).convert(resolvedValue);
            //Guard against bad values sent
            if(rejectEmpty && typedValue == null) {
                log.debug("Ignoring update to attributeId {} since input is empty and rejectEmpty is {}", attributeId, rejectEmpty);
                continue;
            }
            final String supportedOperation = attribute.isMultiValueField() && VALID_MULTIVALUED_FIELD_OPERATIONS.contains(operation)
                    ? operation : !operation.isEmpty() ? operation : "replace";
            updates.add(new Update(attributeId, attribute.getApiName(), typedValue, supportedOperation));
        }
        return updates;
    }
    private List<Update> toChangeSet(EntityDefinition syncariEntity, GraphContext context,FunctionCall functionCall) {
        List<Map<String, Map<String, String>>> sortFields = (List<Map<String, Map<String, String>>>) functionCall.getConfig()
                .getOrDefault("insertFields", List.of());
        List<Update> updates = new ArrayList<>();
        for (Map<String, Map<String, String>> s : sortFields) {
            String attributeId = s.get("updateField").get("value");
            String newValue = s.get("newValue").get("value");
            AttributeDefinition attribute = syncariEntity.getAttribute(attributeId);
            updates.add(new Update(attributeId, attribute.getApiName(),syncariEntity.getAttribute(attributeId).convert(tokenHelper.resolveTokensObject(context,newValue)),"replace"));
        }
        return updates;
    }

    private Optional<AttachRecordResult> retrieveAttachRecordResult(BsonDocument searchCriteria, GraphContext context) {
        Map<BsonDocument, AttachRecordResult> cachedResults = context.cachedOrDefault("_advancedAttachRecordResults",Map.of());
        return Optional.ofNullable(cachedResults.get(searchCriteria));
    }

    private void recordSearchResult(BsonDocument searchCriteria, EntityData incoming, GraphContext context) {
        Map<BsonDocument, AttachRecordResult> cachedResults =  context.cachedOrDefault("_advancedAttachRecordResults",new HashMap<>());

        EntityData incomingCopy = new EntityData().setSyncariEntityId(incoming.getSyncariEntityId()).setId(incoming.getId())
                .setName(incoming.getName()).setConnectorId(incoming.getConnectorId());

        AttachRecordResult incomingResult = cachedResults.getOrDefault(searchCriteria,
                new AttachRecordResult().setSyncariRecordId(incomingCopy.getSyncariEntityId()).addExternalRecord(incomingCopy));
        cachedResults.put(searchCriteria, incomingResult);
        context.cache("_advancedAttachRecordResults",cachedResults);
    }

    private void addNewIdMapping(EntityData incomingRecord, EntityDefinition syncariEntity, EntityDefinition externalEntity, IdMapping idMapping) {
        idMapping.addMapping(incomingRecord.getConnectorId(), incomingRecord.getId(), externalEntity.getId());
        idMappingRepo.save(idMapping);
        log.info("Attached external record {}({}) to syncari record {}({})", incomingRecord.getId(), externalEntity.getApiName(),
                idMapping.getSyncariId(), syncariEntity.getApiName());
        incomingRecord.setSyncariEntityId(idMapping.getSyncariId());
    }

    //This delegate is needed to handle failing conditions in cascades
    //The name "filter" collides with a jtwig tag, the functioncall#compile replaces filter with filterFunction
    @Function
    public Object filterFunction(Object input, FunctionCall functionCall, GraphContext context) {
        log.debug("DEBUG_FILTER: Predicate config: {}", functionCall.getConfig().get("predicate"));
        
        if(featureService.isEnabled(Features.LHSFilterChange, true)) {
            Object result = functionCall.evaluateFilter(context, tokenHelper, Optional.of(schemaService));
            log.debug("DEBUG_FILTER: Result: {} (passes: {})", result, !(result instanceof FilterFailedResult));
            return result;
        } else {
            Object result = functionCall.evaluateFilter(context, tokenHelper, Optional.empty());
            log.debug("DEBUG_FILTER: Result: {} (passes: {})", result, !(result instanceof FilterFailedResult));
            return result;
        }
    }

    @Function
    public Object caseBranch(Object input, FunctionCall functionCall, GraphContext context) {
        MappingGraph graph = context.getGraph();
        MappingNode currNode = context.getCurrentNode();

        boolean result = functionCall.evaluateCaseBranch(currNode, graph, CaseBranchFunction.getConfiguredCaseValue(functionCall.getConfig()), context);
        if (result){
            return new FunctionResult(input, ObjectType.VALUE);
        }
        return new FilterFailedResult(input);
    }

    @Function
    public Object caseFunction(Object input, FunctionCall functionCall, GraphContext context) {
        Map<String, Object> result;
        String nodeName = context.getCurrentNode().getName();
        result = functionCall.evaluateCase(context, tokenHelper);
        context.put(CaseFunction.getKeyForLabel(nodeName), result.get(CaseFunction.CASE_LABEL));
        boolean isMultivalued = (boolean) result.get(CaseFunction.IS_MULTIVALUED);
        boolean isToken = (boolean) result.get(CaseFunction.VALUE_HAS_TOKEN);
        Object value = result.get(CaseFunction.CASE_VALUE);
        AbstractDataType dt = (AbstractDataType) DatatypeFactory.getDatatype((String) result.get(CaseFunction.DATA_TYPE));
        Object caseValue = isMultivalued ? dt.convertMultiValuedInput(value, !isToken) : dt.convert(value);
        context.put(CaseFunction.getKeyForCaseValue(nodeName), caseValue);
        return new FunctionResult(input, ObjectType.VALUE);
    }

    @Function
    public FunctionResult advancedLookUpSyncariRecordOnField(Object input, FunctionCall functionCall, GraphContext context) {
        String syncariEntityDefId = getConfig("syncariEntityDefId", functionCall, context);
        Object findAllValue = getConfigOrDefault("findAll", functionCall, false, context);
        boolean dontMatchBlank = getDontMatchBlankFlag(functionCall, context);
        long startTime = System.currentTimeMillis();
        Boolean findAll = false;
        if (findAllValue instanceof String) {
            try {
                findAll = Boolean.parseBoolean(findAllValue.toString());
            } catch (Exception e) {
            }
        } else {
            findAll = Boolean.valueOf(getConfigOrDefault("findAll", functionCall, false, context));
        }
        Map<String, Object> predicates = getConfig("predicate", functionCall, context);
        try {
            int pageSize = findAll ? 1000 : 1;
            EntityDefinition entity = context.cache(syncariEntityDefId, () -> schemaService.getEntity(syncariEntityDefId));
            Expression expression = MapUtils.isEmpty(predicates) ? Expression.notEmpty(Expression.var("lastModified")) :
                    new PredicateParser(StringUtils.EMPTY).fromMap(predicates);

            boolean useCache = entityRepo.useCache(entity) && !context.isTestMode() && !context.isSimulationMode() ?
                    context.cache(entity.getApiName() + "_index_status",
                    () -> redisUtils.indexStatus(entity.getApiName())) : false;

            List<LookupCriteriaVisitor.Sort> sorts = toSortList(functionCall, "lastModified", "desc", context);

            Optional<Criteria> redisCriteria = Optional.of(new RedisLookupCriteriaVisitor(context, expression, tokenHelper, entity, sorts));
            Optional<Criteria> mongoCriteria = Optional.of(new LookupCriteriaVisitor(context, expression, tokenHelper,
                    entity.getIdToAttributes(),sorts, (key) -> entityRepo.hasCaseInsensitiveIndexOnField(entity, key)));
            boolean foundEmptyValuedPredicates = mongoCriteria.map(c->((LookupCriteriaVisitor)c).foundEmptyValuedPredicates()).orElse(true);

            List<EntityData> search = (dontMatchBlank && foundEmptyValuedPredicates)? List.of(): entityRepo.searchWithFallback(entity, redisCriteria, mongoCriteria, useCache, pageSize).getRecords();
            boolean countRecords =  functionCall.getConfig("count", BooleanType.VALUE).orElse(false);
            Long count=!(dontMatchBlank && foundEmptyValuedPredicates) && countRecords ? entityRepo.countWithFallback(entity, redisCriteria, mongoCriteria, useCache):null;

            log.debug("Result of syncari lookup(Node {}) Found ids {}. Took {} ms"
                    ,context.getCurrentNode() != null ? context.getCurrentNode().getId() : "",
                    search.isEmpty() ? "" : search.stream().map(EntityData::getId).collect(Collectors.toList()), System.currentTimeMillis() - startTime);

            log.debug("Results of lookup syncari for syncariEntityId {} searching with predicate {} and found records with ids {}"
                    ,syncariEntityDefId, predicates, 
                    search.isEmpty() ? "" : search.stream().map(EntityData::getId).collect(Collectors.toList()));

            Object result = search.isEmpty() ? null : search.get(0);

            if(findAll) {
                updateRecordsWithIdMapping(context, entity, search);
                context.set("allPreviousLookupRecords", search);
                context.set("All Lookup Records From " + context.getCurrentNode().getName(), search);
            }else if (!search.isEmpty()){
                updateRecordsWithIdMapping(context, entity, List.of(search.get(0)));
            }else{
                //nothing found to update with idmappings
            }

            return new FunctionResult(input, ObjectType.VALUE, result,count);
        } catch (Exception e) {
            log.error("Error in advancedLookUpSyncariRecordOnField", e);
        }
        return new FunctionResult(input, ObjectType.VALUE, null);
    }

    private void updateRecordsWithIdMapping(GraphContext context, EntityDefinition entity, List<EntityData> search) {
        final Map<String, IdMapping> idMappings = idMappingRepo.findBySyncariIds(entity.getApiName(), search.stream()
                .map(e -> e.getSyncariEntityId()).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(r->r.getSyncariId(), r->r));
        search.forEach(record-> {
            final IdMapping idMapping = idMappings.get(record.getSyncariEntityId());
            if(idMapping!=null){
                idMapping.getConnectedMappings().forEach(mapping->{
                    EntityDefinition externalEntityDef = context.cache(mapping.getEntityDefinitionId(),() -> schemaService.getEntity(mapping.getEntityDefinitionId()));
                    Connector connector = context.cache(mapping.getConnectorId(),() -> connectorService.get(mapping.getConnectorId()));
                    record.addExternalRecordId(connector.getName(),externalEntityDef.getApiName(), mapping.getEntityId());
                });
            }
        });
    }

    @Function
    public Object attachRecord(Object input, FunctionCall functionCall, GraphContext context) {
        EntityData externalEntity = (EntityData) input;

        String syncariEntityDefId = getConfig("syncariEntityDefId", functionCall, context);
        String selectedExternalEntityDefId = getConfig("externalEntityDefId", functionCall, context);
        EntityDefinition syncariEntity = schemaService.getEntity(syncariEntityDefId);
        EntityDefinition selectedExternalEntity =
                selectedExternalEntityDefId == null ?
                        schemaService.getEntity(externalEntity.getConnectorId(), externalEntity.getName())
                        : schemaService.getEntity(selectedExternalEntityDefId);
        String inputFieldId = getConfig("inputFieldId", functionCall, context);
        String lookupValueKey = "field_" + inputFieldId;
        Object lookupValue = context.get(lookupValueKey);

        if (lookupValue == null || StringUtils.isBlank(lookupValue.toString())) {
            log.info("Lookup value for input field {} is null ", inputFieldId);
            return input;
        }

        FunctionResult syncariRecordObject = lookUpSyncariRecord(input, functionCall, context);
        if (syncariRecordObject.getLookupResult() != null) {
            EntityData syncariRecord = (EntityData) syncariRecordObject.getLookupResult();
            externalEntity.setSyncariEntityId(syncariRecord.getId());
        }
        String resolvedExternalRecordId;
        //If the input record is the same type as selected external record type, we need to map the id of the input to a syncari id
        if(isSameEntityDef(selectedExternalEntity, externalEntity)){
            resolvedExternalRecordId = externalEntity.getId();
        }else{
            //else, the selected value is the id of th external record (when the input record actually has an externalId, pointing to the id of a record in a second system)
            resolvedExternalRecordId = lookupValue.toString();
        }

        Optional<IdMapping> existing = idMappingRepo.findByExternalId(syncariEntity.getApiName(),
                selectedExternalEntity.getConnectorId(),
                selectedExternalEntity.getId(),
                resolvedExternalRecordId);
        //We may be attaching idMapping for an external record we haven't seen before.
        //Let's try and get it from external system., if not present in our external record repo
        Optional<EntityData> externalRecord = fetchExternalRecord(selectedExternalEntity, resolvedExternalRecordId);

        //No existing mapping, or found a dangling mapping from a previous incomplete sync cycle

        if(existing.isEmpty()) {

            //remove dangling mapping
            IdMapping mapping = new IdMapping();
            mapping.setEntityName(syncariEntity.getApiName());
            mapping.setSyncariId(externalEntity.getSyncariEntityId());
            mapping.addMapping(selectedExternalEntity.getConnectorId(), resolvedExternalRecordId, selectedExternalEntity.getId());
            idMappingRepo.upsert(List.of(mapping));
            log.info("Adding id mapping between syncari record {}({}) and external record {}({}:{}) and connectorId {}, via input record {}({})", externalEntity.getSyncariEntityId(),syncariEntity.getApiName(),
                    resolvedExternalRecordId, selectedExternalEntity.getApiName(),selectedExternalEntity.getId(),externalEntity.getConnectorId(), externalEntity.getName(),externalEntity.getId());
        }else{
            log.info("Updating id mapping  between external record {}({}:{}) and syncari record {}({}:{})",resolvedExternalRecordId,selectedExternalEntity.getApiName(),selectedExternalEntity.getId(),
                    existing.get().getSyncariId(),syncariEntity.getApiName());
            externalEntity.setSyncariEntityId(existing.get().getSyncariId());
        }
        //We fetched a record from external system. Save it as part of current batch
        externalRecord.ifPresent(record->{
            record.setSyncariEntityId(externalEntity.getSyncariEntityId());
            context.addConnectedRecord(selectedExternalEntity,record);
        });

        return input;
    }

    @Function
    public Object findValue(Object input, FunctionCall functionCall, GraphContext context) {
        String fieldName = getConfig("fieldName", functionCall, context);
        if(StringUtils.isBlank(fieldName)){
            log.warn("Required config fieldName missing on node {} in graph {}",context.getCurrentNode().getName(),context.getGraph().getName());
            return null;
        }
        return tokenHelper.resolveTokensObject(context, fieldName);
    }

    @Function
    public Object setFields(Object input, FunctionCall functionCall, GraphContext context)  {
        Boolean rejectEmpty = getConfigOrDefault("rejectEmpty", functionCall, true, context);
        EntityData entityData = new EntityData("__transient__");
        List<Map<String, Map<String, String>>> fieldValuePairs = (List<Map<String, Map<String, String>>>) functionCall.getConfig()
                .getOrDefault("setFields", List.of());

        EntityDefinition destinationEntityDefinition = null;
        for (Map<String, Map<String, String>> s : fieldValuePairs) {
            String fieldId = s.get("setField").get("value");
            if(destinationEntityDefinition==null) {
                AttributeDefinition field = context.cache(fieldId, () -> schemaService.getAttribute(fieldId));
                destinationEntityDefinition = context.cache(field.getEntityId(), () -> schemaService.getEntity(field.getEntityId()));
            }
            AttributeDefinition attribute = destinationEntityDefinition.getAttribute(fieldId);
            String fieldName = attribute.getApiName();
            String newValue = s.get("fieldValue").get("value");
            Object value = tokenHelper.resolveTokensObject(context, newValue);
            boolean isBlank = value == null || StringUtils.isBlank(value.toString());
            if (!(isBlank && rejectEmpty)) {
                entityData.addValue(fieldName, attribute.convert(value));
                if ("syncariid".equalsIgnoreCase(fieldName)) {
                    entityData.setSyncariEntityId(value.toString());
                }
            }
        }
        return entityData;
    }

    @Function
    public Object lookupDataset(Object input, FunctionCall functionCall, GraphContext context) {
        String datasetId = getConfig("datasetId", functionCall, context);
        Long limitValue = IntegerType.VALUE.convert(getConfig("limit", functionCall, context));
        int limit = limitValue == null ? 1000 : Math.min(1000, limitValue.intValue());
        //lookup dataset
        final Optional<Dataset> dataset = datasetService.findDataset(datasetId);
        dataset.ifPresent(ds -> {
            //find its variables
            final Map<String, Variable> variablesMap = ds.getVariablesMap();
            final Map<String, VariableValue> resolvedVariablesMap = new HashMap<>();

            if (variablesMap != null) {
                variablesMap.forEach((name, variable) -> {
                Datatype type = DatatypeFactory.getDatatype(variable.getDatatype());
                final Object configValue = variable.isMultiValueField() ?
                        resolveMultivaluedConfig(name, type, functionCall, context) :
                        type.convert(tokenHelper.resolveTokensObject(context, getConfig(name, functionCall, context)));
                if (configValue != null) {
                    resolvedVariablesMap.put(name, new VariableValue().setDefaultValue(configValue));
                } else {
                    resolvedVariablesMap.put(name, new VariableValue().setDefaultValue(variable.getVariableValue().getDefaultValue()));
                }
                });
            }
            //execute the dataset with these variables/defaults
            try {
                final Map<String, Object> datasetResults = datasetService.readDataWithPagination(ds, resolvedVariablesMap, limit, 0l);
                //update context with results
                final List<Map<String, Object>> records = (List<Map<String, Object>>) datasetResults.getOrDefault("data", List.of());
                context.addResult(records);
            }catch (SyncariValidationException exception){
                log.info("SyncariValidationException occurred {}", ExceptionUtils.getStackTrace(exception));
                if ((StringUtils.isNotEmpty(exception.getMessage())) && (!exception.getMessage().contains(i18n("dataset_no_data")))){
                    throw exception;
                }
            }
        });
        return input;
    }

    private <T> List<T> resolveMultivaluedConfig(String name, Datatype type, FunctionCall functionCall, GraphContext context) {
        final Object configValue = getConfig(name, functionCall, context);
        if (configValue == null) {
            return List.of();
        }
        if (List.class.isInstance(configValue)) {
            return (List<T>) List.class.cast(configValue).stream()
                    .map(c -> c == null ? null : type.convert(tokenHelper.resolveTokensObject(context, c.toString())))
                    .collect(Collectors.toList());
        } else {
            return (List<T>) List.of(type.convert(tokenHelper.resolveTokensObject(context, configValue.toString())));
        }
    }

    @Function
    public Object lookupDatasetOnField(Object input, FunctionCall functionCall, GraphContext context) {
        return lookupDataset(input, functionCall, context);
    }

    private Optional<EntityData> fetchExternalRecord(EntityDefinition selectedExternalEntity, String resolvedExternalRecordId) {
        Optional<StagedBatchRecord> record = stagedBatchRecordRepo.findFirstByExternalEntityDefinitionIdAndExternalRecordId(selectedExternalEntity.getId(), resolvedExternalRecordId);
        if (record.isEmpty()) {
            return Optional.of(new EntityData().setId(resolvedExternalRecordId).setName(selectedExternalEntity.getApiName()).setConnectorId(selectedExternalEntity.getConnectorId()));
        }
        return Optional.empty();
    }

    private boolean isSameEntityDef(EntityDefinition selectedExternalEntity, EntityData externalEntity) {
        return selectedExternalEntity.getConnectorId().equals(externalEntity.getConnectorId()) && selectedExternalEntity.getApiName().equals(externalEntity.getName());
    }

}

@Data
@Accessors(chain = true)
class AttachRecordResult {
    String syncariRecordId;
    Set<EntityData> connectedRecords = new HashSet<>();

    public AttachRecordResult addExternalRecord(EntityData entityData) {
        connectedRecords.add(entityData);
        return this;
    }

    /**
     * We should attach incoming record only if it is not a duplicate (same synapse, same entitydef, different id)
     *
     * @param incoming
     * @return true if if incoming is not a duplicate (same synapse, same entitydef, matches attach criteria, but different id)
     */
    public boolean shouldAttach(EntityData incoming) {
        return !hasRecordFromSameSynapseAndEntity(incoming);
    }

    private boolean hasRecordFromSameSynapseAndEntity(EntityData incoming) {
        //same synapse & entitydef, but different external record id.
        return connectedRecords.stream().filter(c -> !c.getId().equals(incoming.getId())
                && c.getName().equalsIgnoreCase(incoming.getName())
                && c.getConnectorId().equalsIgnoreCase(incoming.getConnectorId()))
                .findFirst().isPresent();
    }
}
@Getter
@AllArgsConstructor
class Update{
    private final String attributeId;
    private final String apiName;
    private final Object newValue;
    private final String operation;
    private static BiFunction<Object,Object,Object> defaultOperation = (oldValue, newValue) -> newValue;

    private static BiFunction<Object,Object,Object> prefix = (oldValue, newValue) ->
        Objects.requireNonNullElse(newValue,"").toString() + Objects.requireNonNullElse(oldValue,"").toString();

    private static BiFunction<Object,Object,Object> suffix = (oldValue, newValue) ->
            Objects.requireNonNullElse(oldValue,"").toString() + Objects.requireNonNullElse(newValue,"").toString();

    private static BiFunction<Object,Object,Object> prepend = (oldValue, newValue) ->  addObjectToList(oldValue, newValue, (list,value)->list.add(0,value));

    private static BiFunction<Object,Object,Object> append = (oldValue, newValue) ->  addObjectToList(oldValue, newValue, (list,value)->list.add(value));

    private static BiFunction<Object,Object,Object> remove = (oldValue, newValue) ->  {
        if(oldValue == null) {
            return null;
        }
        if(List.class.isAssignableFrom(oldValue.getClass())){
            //copy,so you don't mutate
            final List oldValueAsList = new ArrayList(List.class.cast(oldValue));
            if(List.class.isAssignableFrom(newValue.getClass())){
                List.class.cast(newValue).forEach(n -> oldValueAsList.remove(n));
            }
            oldValueAsList.remove(newValue);
            return oldValueAsList;
        }
        return  oldValue;

    };

    private static Object addObjectToList(Object oldValue, Object newValue, BiConsumer<List<Object>,Object> adder) {
        if(oldValue == null){
            if(newValue !=null){
                if(List.class.isAssignableFrom(newValue.getClass())){
                    return newValue;
                }
                return new ArrayList(List.of(newValue));
            }else{
                return oldValue;
            }
        }
        if(List.class.isAssignableFrom(oldValue.getClass())){
            //Copy, so you don't mutate original value
            final List oldValueAsList = new ArrayList(List.class.cast(oldValue));
            if(newValue!=null) {
                if(List.class.isAssignableFrom(newValue.getClass())){
                    List.class.cast(newValue).forEach(n -> {
                        addIfAbsent(n, oldValueAsList, adder);
                    });
                }else{
                    addIfAbsent(newValue, oldValueAsList, adder);
                }
                return oldValueAsList;
            }
        }
        return  oldValue;
    }

    private static void addIfAbsent(Object valueToAdd, List targetList, BiConsumer<List<Object>, Object> adder) {
        if (!targetList.contains(valueToAdd)) {
            adder.accept(targetList, valueToAdd);
        }
    }

    private static Map<String, BiFunction<Object,Object,Object>> operations = Map.of(
            "replace", defaultOperation,
            "prefix" , prefix,
            "suffix" , suffix,
            "prepend" , prepend,
            "append" , append,
            "remove" , remove
    );

    public Object applyTo(Object oldValue){
        return operations.getOrDefault(operation,defaultOperation).apply(oldValue, newValue);
    }

}
