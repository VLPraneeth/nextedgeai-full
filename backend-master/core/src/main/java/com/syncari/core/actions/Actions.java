package com.syncari.core.actions;

import com.google.common.primitives.Ints;
import com.sforce.soap.partner.Error;
import com.sforce.soap.partner.SaveResult;
import com.syncari.connector.CampaignMember;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.Constants;
import com.syncari.connector.EntityData;
import com.syncari.connector.data.*;
import com.syncari.connector.service.HubspotService;
import com.syncari.connector.service.MarketoService;
import com.syncari.connector.service.SalesforceService;
import com.syncari.connector.zendesk.ZendeskService;
import com.syncari.core.DataTransformer;
import com.syncari.core.actions.http.HTTPAction;
import com.syncari.core.datatype.BooleanType;
import com.syncari.core.datatype.DateType;
import com.syncari.core.datatype.DatetimeType;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.file.GCSFileManager;
import com.syncari.core.model.*;
import com.syncari.core.model.IdMapping.Mapping;
import com.syncari.core.model.misc.NodeStatusMetric;
import com.syncari.core.model.misc.NotificationType;
import com.syncari.core.model.pagination.Page;
import com.syncari.core.model.pagination.PageCursor;
import com.syncari.core.model.pagination.PageDirection;
import com.syncari.core.model.util.Type;
import com.syncari.core.pipeline.BatchActionContext;
import com.syncari.core.pipeline.GraphContext;
import com.syncari.core.pipeline.NodeError;
import com.syncari.core.pipeline.expression.Expression;
import com.syncari.core.pipeline.expression.PredicateParser;
import com.syncari.core.repositories.customer.EntityRepo;
import com.syncari.core.repositories.customer.IdMappingRepo;
import com.syncari.core.service.*;
import com.syncari.core.service.messagingservice.MSTeamsService;
import com.syncari.core.service.messagingservice.SlackService;
import com.syncari.core.token.TokenHelper;
import com.syncari.core.utils.Criteria;
import com.syncari.core.utils.LookupCriteriaVisitor;
import com.syncari.core.utils.RedisLookupCriteriaVisitor;
import com.syncari.core.utils.RedisUtils;
import com.syncari.utils.CollectionUtils;
import com.syncari.utils.Timers;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

@Slf4j
@Component
public class Actions extends ActionsBase {

    private static final String ADDTOCAMP = "addtocamp";
    public static final int DELETE_RECORDS_PAGE_SIZE = 100;
    private static final int DELETE_SYNCARI_RECORD_SEARCH_ALERT_SIZE = 10000;
    private ActionFactory actionFactory;

    private DefaultAction exportSyncariRecords;

    EmailService emailService;
    protected ZendeskService zendeskService;
    SalesforceService salesforceService;
    private NotificationService notificationService;
    ConnectorService connectorService;
    private DataTransformer transformer;
    SchemaService schemaService;
    protected SlackService slackService;
    private IdMappingRepo idMappingRepo;
    TokenHelper tokenHelper;
    private RequeueService requeueService;
    MarketoService marketoService;
    HubspotService hubspotService;
    DataServiceFactory dataServiceFactory;
    EntityRepoService entityRepoService;
    private DefaultAction insertSyncariRecordAction;
    private DefaultAction updateRecordsAction;

    private DefaultAction respondToWebhookAction;
    protected MSTeamsService msTeamsService;
    private PipelineNodeAuditService pipelineNodeAuditService;

    @Autowired
    private Map<String, CustomAction> customActionMap;

    @Autowired
    private GCSFileManager gcsFileManager;

    @Autowired
    EntityRepo entityRepo;

    @Autowired
    RedisUtils redisUtils;

    @Autowired
    HTTPAction httpAction;

    private static Map<String, Method> actionMap = new HashMap<>();
    private static Set<String> batchableActions = new HashSet<>();
    static {
        Method[] declaredMethods = Actions.class.getDeclaredMethods();
        List<Method> actionList = Arrays.asList(declaredMethods);
        for (Method action : actionList) {
            if (action.isAnnotationPresent(Action.class)) {
                actionMap.put(action.getName(), action);
                if (action.getAnnotation(Action.class).isBatchable()) {
                    batchableActions.add(action.getName());
                }
            }
        }
    }

    public Actions() {
    }

    public boolean isBatchedAction(String apiName) {
        return batchableActions.contains(apiName);
    }

    public boolean isValidAction(String apiName, GenericActionConfig actionConfig) {
        return actionMap.containsKey(apiName) || actionConfig.getType() == Type.CUSTOM;
    }


    public ActionResult dispatch(String actionName, GenericActionConfig actionConfig, GraphContext context) {
        boolean success = false;
        try {
            if (actionMap.containsKey(actionName)) {
                final Object result = actionMap.get(actionName).invoke(this, actionConfig, context);
                if (ActionResult.class.isInstance(result)) {
                    return ActionResult.class.cast(result);
                }
                return new ActionResult(true);
            } else {
                // custom action
                if (actionConfig.getType() == Type.CUSTOM) {

                    // Once we implement other types of custom actions make sure to include type as well to know the executor to use
                    /*CustomAction customAction =  customActionMap.get(actionName);
                    if (customAction != null) {
                        return customAction.execute(actionConfig, context);
                    } else {
                        log.error("Invalid Custom Action Found : {}" + actionName);
                        throw new RuntimeException("Invalid Custom Action " + actionName);
                    }*/
                    // since we have all custom action of Type HTTPAction we can use directly
                    return httpAction.execute(actionConfig, context);

                } else {
                    log.error("Invalid Action of type {} and name {}", actionConfig.getType(), actionName);
                    throw new RuntimeException(String.format("Invalid Action of type %s and name %s", actionConfig.getType(), actionName));
                }
            }
        } catch (Exception e) {
        	log.debug(ExceptionUtils.getStackTrace(e));
            Throwable exception = e;
        	String errorMsg = e.getMessage();
            if(e instanceof InvocationTargetException) {
            	Throwable targetException = ((InvocationTargetException)e).getTargetException();
            	if(targetException != null) {
                    exception = targetException;
            		errorMsg = targetException.getMessage();
                }
            }
            log.error("Failed to execute Action {}, Action Type {}. Error is {}", actionName, actionConfig.getType(), errorMsg);
            NodeError nodeError = new NodeError().setError(errorMsg).setErrorDetails(ExceptionUtils.getStackTrace(e))
                    .setNodeId(context.getCurrentNode().getId()).setNodeName(context.getCurrentNode().getApiName());
            if (context.getGraph() != null) {
                nodeError.setGraphName(context.getGraph().getName()).setGraphId(context.getGraph().getId())
                        .setScope(context.getGraph().getScope()).setTargetId(context.getGraph().getTargetId());
            }
            context.addError(context.getCurrentSyncariId(), nodeError);
            return new ActionResult(ActionResult.NO_RESULTS, false, exception);
        } finally {
            context.getNodeStatusMetrics().compute(context.getCurrentNode().getId(), (k, v) -> {
                if (v != null) {
                    v.setRecordCount(v.getRecordCount() + 1);
                } else {
                    v = new NodeStatusMetric(1);
                }
                return v;
            });
        }
    }


    @Autowired
    public Actions(@Qualifier("defaultEmailService") EmailService emailService, ZendeskService zendeskService, SalesforceService salesforceService,
                   ConnectorService connectorService, DataTransformer transformer, SchemaService schemaService,
                   SlackService slackService, IdMappingRepo idMappingRepo, MarketoService marketoService,
                   HubspotService hubspotService, DataServiceFactory dataServiceFactory, NotificationService notificationService,
                   TokenHelper tokenHelper, RequeueService requeueService, EntityRepoService entityRepoService,
                   @Qualifier("defaultActions") ActionFactory actionFactory, MSTeamsService msTeamsService, PipelineNodeAuditService pipelineNodeAuditService) {
        this.emailService = emailService;
        this.zendeskService = zendeskService;
        this.salesforceService = salesforceService;
        this.connectorService = connectorService;
        this.transformer = transformer;
        this.schemaService = schemaService;
        this.slackService = slackService;
        this.idMappingRepo = idMappingRepo;
        this.marketoService = marketoService;
        this.hubspotService = hubspotService;
        this.dataServiceFactory = dataServiceFactory;
        this.notificationService = notificationService;
        this.tokenHelper = tokenHelper;
        this.requeueService = requeueService;
        this.entityRepoService = entityRepoService;
        this.insertSyncariRecordAction = actionFactory.insertSyncariRecord();
        this.respondToWebhookAction = actionFactory.respondToWebhook();
        this.updateRecordsAction = actionFactory.updateRecords();
        this.exportSyncariRecords = actionFactory.exportRecords();
        this.msTeamsService = msTeamsService;
        this.pipelineNodeAuditService = pipelineNodeAuditService;
        this.actionFactory = actionFactory;
    }


    @Action
    public void sendEmail(GenericActionConfig configuration, GraphContext context) {
        var toList = (List<String>) configuration.get("recipients");
        var subject = configuration.get("subject").toString();
        var emailBody = "";
        if(configuration.containsKey("body")) {
            try {
                emailBody = new String(Base64.getDecoder().decode(configuration.get("body").toString()));
            } catch (Exception e) {
                emailBody = configuration.get("body").toString();
            }
        }
        if (toList.isEmpty()) {
            log.error("No recipients found.Skipping send email action for graph ", context.getGraph().getName());
            return;
        }
        String resolvedHtml = tokenHelper.resolveTokens(context, emailBody);
        String resolvedSubject = tokenHelper.resolveTokens(context, subject);
        List<String> resolvedToList = toList.stream().map(e -> tokenHelper.resolveTokens(context, e)).collect(Collectors.toList());
        emailService.sendHtml(resolvedToList, resolvedSubject, resolvedHtml);
    }

    @Action(isBatchable = true)
    public void insertSyncariRecord(GenericActionConfig actionConfig, GraphContext context) {
        insertSyncariRecordAction.execute(actionConfig, context);
    }

    @Action
    public void respondToWebhook(GenericActionConfig actionConfig, GraphContext context) {
        respondToWebhookAction.execute(actionConfig, context);
    }

    @Action
    public void updateMultipleSyncariRecords(GenericActionConfig actionConfig, GraphContext context) {
        updateRecordsAction.execute(actionConfig, context);
    }

    @Action
    public void createZendeskTicket(GenericActionConfig config, GraphContext context) {
        List<String> fields = List.of("subject", "description", "type", "priority");
        Map<String, Object> params = new HashMap<>();
        params.putAll(config.getConfigMap());
        params.put("subject", tokenHelper.resolveTokens(context, params.get("subject").toString()));
        params.put("description", tokenHelper.resolveTokens(context, params.get("zendeskDescription").toString()));
        SyncRequest request = createRequest(context, params, fields, Constants.TICKET);
        SyncResponse syncResponse = zendeskService.create(request);
        if (!syncResponse.isSuccess()) {
            throw new RuntimeException("Create Zendesk task failed " + syncResponse.toString());
        }
        log.info("Created Zendesk task {}", syncResponse);

    }

    @Action
    public void sendSlackMessage(GenericActionConfig params, GraphContext context) {
        String message = params.getOrDefault("message", "").toString();
        String block = params.getOrDefault("block", "").toString();
        String thread = params.getOrDefault("thread", "").toString();
        String channel = params.get("channel").toString();
        String serviceId = params.get("serviceId").toString();
        String resolvedMsg = tokenHelper.resolveTokens(context, message);
        String resolvedChannel = tokenHelper.resolveTokens(context, channel);
        String resolvedBlock = tokenHelper.resolveTokens(context, block);
        slackService.sendMessage(resolvedBlock, thread, resolvedMsg, resolvedChannel, serviceId);
    }

    @Action
    public void createMSTeamsChannel(GenericActionConfig params, GraphContext context) {
        String teamId = params.get("teamId").toString();
        String channel = params.get("channel").toString();
        String serviceId = params.get("serviceId").toString();
        String resolvedChannel = tokenHelper.resolveTokens(context, channel);
        msTeamsService.createChannel(teamId, resolvedChannel, serviceId);
    }

    @Action
    public void sendMSTeamsMessage(GenericActionConfig params, GraphContext context) {
        String teamId = params.get("teamId").toString();
        String message = params.getOrDefault("message", "").toString();
        String channel = params.get("channel").toString();
        String serviceId = params.get("serviceId").toString();
        String resolvedMsg = tokenHelper.resolveTokens(context, message);
        String resolvedChannel = tokenHelper.resolveTokens(context, channel);
        msTeamsService.sendMessage(teamId, resolvedChannel, resolvedMsg, serviceId);
    }

    @Action(isBatchable = true)
    public void addToSfdcCampaign(GenericActionConfig params, GraphContext context) {
        String synapseId = params.get("synapseId").toString();
        String synapseEntity = params.getOrDefault("entity", "").toString();
        EntityData entity = (EntityData) context.get("record");
        String leadId = params.getOrDefault("leadId", "").toString();
        String campaignId = params.get("campaignId").toString();
        String status = params.get("status").toString();
        String resolvedLeadId = tokenHelper.resolveTokens(context, leadId);
        String resolvedCampaignId = tokenHelper.resolveTokens(context, campaignId);
        String resolvedStatus = tokenHelper.resolveTokens(context, status);
        ConnectorInfo connectorInfo = transformer.toConnectorInfo(connectorService.find(synapseId).get());
        BatchActionContext batchActionContext = context.getBatchActionContext();
        //Run actions on collected batch of lead ids
        if(batchActionContext.shouldRunActions()){
            List<Object> collectedParams = batchActionContext.get(context.getCurrentNode().getId());
			if (collectedParams != null && !collectedParams.isEmpty()) {
				List<CampaignMember> request = collectedParams.stream().map(p -> (CampaignMember)p).collect(Collectors.toList());
				EntityDefinition leadOrContact = getEntityDef(synapseId, synapseEntity, request.get(0).getObjectType(),
						resolvedCampaignId, context);
                SaveResult[] results = salesforceService.addToCampaign(leadOrContact.getApiName(), request, connectorInfo);
                for(SaveResult r : results){
                    for(Error e :r.getErrors()){
        				String error = String.join("|", Arrays.asList(e.getExtendedErrorDetails()).stream()
        						.map(ex -> ex.getExtendedErrorCode().toString()).collect(Collectors.toList()));
        				notificationService.broadcast("Add to Salesforce Campaign failed for " + entity.getName(), error,
        						NotificationType.ERROR);
                    }
                }
            }
        }else{
            //Collect mode. Keep adding leads to batchaction context
        	EntityDefinition leadOrContact = getEntityDef(synapseId, synapseEntity, entity.getName(), resolvedCampaignId, context);
        	if(StringUtils.isBlank(resolvedCampaignId) || entity == null || (StringUtils.isBlank(entity.getId()) 
        			&& StringUtils.isBlank(resolvedLeadId))) {
        		log.warn("Empty data for addToSfdcCampaign campId:{}, entity: {} , Context {}", resolvedCampaignId, entity, context.entrySet());
        		return;
        	}
        	String campaignMemberId = resolvedLeadId;
        	//No campaign member id set in the config, so infer campaign member id from the current record
            if(StringUtils.isBlank(campaignMemberId)) {
            	resolvedLeadId = entity.getId();
            
	            Optional<IdMapping> external = idMappingRepo.findExistingMapping(entity.getName(), resolvedLeadId, synapseId,
	                    leadOrContact.getId());
	            String externalEntityDefinitionId = leadOrContact.getId();
	            if(external.isEmpty()) {
	                log.warn("No id mapping found for syncari entity {}, id: {}", entity.getName(), resolvedLeadId);
	                return;
	            }
	            //Cases when a merge has happened on this contact/lead, there will be
	            //multiple entries for the same syanpse+entitydefintion. But losers will have a different syncariId
	            //Historically, mappings didn't have a syncariId, so use that as a fallback for old data
	            Optional<Mapping> entityMapping = external.get().getMapping(synapseId, externalEntityDefinitionId ,entity.getSyncariEntityId())
	                    .or(()->external.get().getMapping(synapseId, externalEntityDefinitionId));
	
	            if(entityMapping.isEmpty()) {
	                log.warn("No id mapping found for syncari entity {}, id: {}", entity.getName(), entity.getId());
	                return;
	            }
	            campaignMemberId = entityMapping.get().getEntityId();
            }
			CampaignMember member = new CampaignMember().setObjectId(campaignMemberId).setCampaignId(resolvedCampaignId)
					.setObjectType(leadOrContact.getApiName())
					.setStatus(resolvedStatus);
            batchActionContext.updateBatchContext(context.getCurrentNode(), member);
        }
    }

	private EntityDefinition getEntityDef(String synapseId, String synapseEntity, String entity,
			String resolvedCampaignId, GraphContext context) {
		EntityDefinition leadOrContact = null;
		String entityName = synapseEntity;
		if(!StringUtils.isBlank(synapseEntity)) {
			entityName = synapseEntity;
		} else if("Contact".equalsIgnoreCase(entity)) {
		    entityName = "Contact";
		} else if("Lead".equalsIgnoreCase(entity)) {
		    entityName = "Lead";
		} else {
		    log.warn("Trying to add unsupported object to campaign {}. Expected lead/contact but found {}", resolvedCampaignId, entity);
		    return null;
		}
		String key = ADDTOCAMP + synapseId + entityName;
		if(context.containsKey(key) && context.get(key) != null) {
			leadOrContact = (EntityDefinition) context.get(key);
		} else {
			leadOrContact = schemaService.getEntity(synapseId, entityName);
			context.cache(key, leadOrContact);
		}
		return leadOrContact;
	}

    @Action
    public void createSalesforceTask(GenericActionConfig config, GraphContext context) {
        List<String> fields = List.of("OwnerId", "Priority", "Status", "Subject", "ActivityDate");
        String syncariOwnerId = config.get("OwnerId").toString();
        String synapseId = config.get("synapseId").toString();
        EntityDefinition sfdcTask = schemaService.getEntity(synapseId, "Task");
        Optional<IdMapping> external = idMappingRepo.findExistingMapping("user", syncariOwnerId, synapseId, sfdcTask.getId());
        IdMapping externalOwnerId = external.orElseThrow(() -> new SyncariValidationException("Unable to find salesforce user id for syncari id {}", syncariOwnerId));
        //Safe to do get on optional, mapping is guaranteed to exist because of above query
        Map<String, Object> params = new HashMap<>();
        params.putAll(config.getConfigMap());
        params.put("OwnerId", externalOwnerId.getMapping(synapseId, sfdcTask.getId()).get().getEntityId());
        Date activityDate = DateType.VALUE.convert(params.get("ActivityDate").toString());
        params.put("ActivityDate", activityDate);
        SyncRequest request = createRequest(context, params, fields, Constants.TASK);
        SyncResponse syncResponse = salesforceService.create(request);
        if (!syncResponse.isSuccess()) {
            notificationService.broadcast("Create Salesforce Task failed", String.join(".", syncResponse.getErrors()), NotificationType.ERROR);
        }
        log.info("Created SFDC task {}", syncResponse);
    }

    @Action(isBatchable = true)
    public void convertSalesforceLead(GenericActionConfig params, GraphContext context) {
        Timers timer = new Timers(log);
        BatchActionContext batchActionContext = context.getBatchActionContext();
        Boolean param = batchActionContext.retrieveParam(context.getCurrentNode());
        boolean captureResults = params.get("captureResults") == null ? true : (boolean) params.get("captureResults");
        if (param == null) batchActionContext.storeParam(context.getCurrentNode(), captureResults);
        String synapseId = params.get("synapseId").toString();

        captureResults = batchActionContext.retrieveParam(context.getCurrentNode());
        if (captureResults && !batchActionContext.shouldRunActions()) {
            ConvertRequest request = getConvertRequest(params, context, timer, true);
			ConvertResponse response = timer.time("convertSalesforceLeadAction::convertLead",
					() -> salesforceService.convertLead(request));
            log.info("Convert SFDC lead response {}",response);
            timer.logInfo();
            if(response.getData()!=null) {
                if(!response.getData().isEmpty()) {
                    context.put(context.getCurrentNode().getApiName(), response.getData().get(0));
                }
                response.getData().forEach(result -> {
                    if (!result.isSuccess()) {
						notificationService.broadcast(
								"Convert Salesforce Lead failed for lead " + result.getLeadId(),
								result.getError(), NotificationType.ERROR);
                        throw new RuntimeException("Salesforce lead conversion failed for " + result.getLeadId() + " with error: " +result.getError());
                    }
                });
            }
        } else {
        	if(batchActionContext.shouldRunActions()){
        		List<Object> collectedParams = batchActionContext.get(context.getCurrentNode().getId());
        		Set<String> leadIds = new HashSet<>();
        		Set<String> ownerIds = new HashSet<>();
				ConvertRequest convertRequest = null;
				if (collectedParams != null && !collectedParams.isEmpty()) {
					for (Object p : collectedParams) {
						ConvertRequest req = (ConvertRequest) p;
						if (convertRequest == null) {
							convertRequest = req;
						} else {
							convertRequest.getData().addAll(req.getData());
						}
						req.getData().forEach(l -> {
							leadIds.add(l.getLeadId());
							ownerIds.add(l.getOwnerId());
						});
					}
					Map<String, String> syncariToExtOwnerId = getExternalUserIds(ownerIds, context, synapseId);
					Map<String, String> syncariToExtLeadId = getExternalLeadIds(leadIds, context, synapseId);
					convertRequest.getData().forEach(l -> {
						l.setOwnerId(syncariToExtOwnerId.getOrDefault(l.getOwnerId(), l.getOwnerId()));
						l.setLeadId(syncariToExtLeadId.getOrDefault(l.getLeadId(), l.getLeadId()));
					});
					final ConvertRequest newReq = convertRequest;
					ConvertResponse response = timer.time("convertSalesforceLeadAction::convertLead",
							() -> salesforceService.convertLead(newReq));
					log.info("Convert SFDC lead response {}", response);
					timer.logInfo();
                    List<String> failedLeads = new ArrayList<>();
					if (response.getData() != null) {
						response.getData().forEach(result -> {
							if (!result.isSuccess()) {
								notificationService.broadcast("Convert Salesforce Lead failed for leads " + result.getLeadId(),
										result.getError(), NotificationType.ERROR);
                                failedLeads.add(result.getLeadId());
							}
						});
					}
                    if (failedLeads.size()>0){
                        throw new RuntimeException("Salesforce lead conversion failed for leads " + String.join( ", ", failedLeads));
                    }
				}
        	} else {
        		// collect
                ConvertRequest request = getConvertRequest(params, context, timer, false);
                batchActionContext.updateBatchContext(context.getCurrentNode(), request);
                log.info("Collecting convertSalesforceLead parameters with LeadId {}", request.getData().get(0).getLeadId());
        	}
        }
    }
    
	private Map<String, String> getExternalUserIds(Set<String> ownerIds, GraphContext context, String synapseId) {
		Map<String, String> syncariToExternalId = new HashMap<>();
		if (!ownerIds.isEmpty()) {
			EntityDefinition sfdcOwner = context.cache(synapseId + "_User",
					() -> schemaService.getEntity(synapseId, "User"));
			List<IdMapping> ownerMappings = idMappingRepo.findBySyncariIds("user", ownerIds);
			ownerMappings.forEach(m -> syncariToExternalId.put(m.getSyncariId(),
					m.getMapping(synapseId, sfdcOwner.getId()).map(r -> r.getEntityId()).orElse(m.getSyncariId())));
		}
		return syncariToExternalId;
	}
	
	private Map<String, String> getExternalLeadIds(Set<String> leadIds, GraphContext context, String synapseId) {
		Map<String, String> syncariToExternalId = new HashMap<>();
		if (!leadIds.isEmpty()) {
			EntityDefinition sfdcLead = context.cache(synapseId + "_Lead",
					() -> schemaService.getEntity(synapseId, "Lead"));
			List<IdMapping> ownerMappings = idMappingRepo.findBySyncariIds("lead", leadIds);
			ownerMappings.forEach(m -> syncariToExternalId.put(m.getSyncariId(),
					m.getMapping(synapseId, sfdcLead.getId()).map(r -> r.getEntityId()).orElse(m.getSyncariId())));
		}
		return syncariToExternalId;
	}

    @Action(isBatchable = true)
    public void addToMarketoList(GenericActionConfig params, GraphContext context) {
        var entityName = context.getCurrentBatch().getSyncariEntityName();
        BatchActionContext batchActionContext = context.getBatchActionContext();
        //Run actions on collected batch of lead ids
        if (batchActionContext.shouldRunActions()) {
            List<Object> collectedParams = batchActionContext.get(context.getCurrentNode().getId());
            Map<String, Set<String>> listToLeadsMap = new HashMap<>();
            if (collectedParams != null && !collectedParams.isEmpty()) {
                log.info("Found {} Add To Marketo List batch items", collectedParams.size());
                Map<String, Object> firstParam = (Map<String, Object>) collectedParams.get(0);
                String synapseId = firstParam.get("synapseId").toString();
                Connector synapse = connectorService.find(synapseId).get();
                ConnectorInfo connectorInfo = transformer.toConnectorInfo(synapse);
                collectedParams.forEach(p -> {
                    Map<String, Object> currentParam = (Map<String, Object>) p;
                    String listId = currentParam.get("listId").toString();
                    String leadId = currentParam.get("leadId").toString();
                    if(!StringUtils.isBlank(listId) && !StringUtils.isBlank(leadId)) {
                        var leads = listToLeadsMap.getOrDefault(listId, new HashSet<>());
                        leads.add(leadId);
                        listToLeadsMap.put(listId, leads);
                    }
                });
                if(!listToLeadsMap.isEmpty()){
                    long totalLeadsProcessed = 0;
                    // iterate over each entry of map and add leads to corresponding lists
                    for(Map.Entry<String, Set<String>> listLeads: listToLeadsMap.entrySet()){
                        String listId = listLeads.getKey();
                        Set<String> leadIds = listLeads.getValue();
                        List<Integer> marketoLeadIds = getMarketoLeadIds(entityName, synapseId, leadIds);
                        log.info("Adding leads {} to Marketo List {}, syncari leads {}", marketoLeadIds,listId, leadIds);
                        if(!marketoLeadIds.isEmpty()){
                            long results = marketoService.addToList(listId, marketoLeadIds, connectorInfo);
                            log.info("Added {} out of {} leads to Marketo List {}", results, marketoLeadIds, listId);
                            totalLeadsProcessed += results;
                        }
                    }
                    log.info("Total Leads Processed: {}", totalLeadsProcessed);
                }
            }
        }else{
            //Collect mode. Keep adding leads to batchaction context
            String listId = tokenHelper.resolveTokens(context, params.get("listId").toString());
            String leadId = tokenHelper.resolveTokens(context, params.get("leadId").toString());
            var updatedParams = new HashMap<>(params.getConfigMap());
            updatedParams.put("leadId",leadId);
            updatedParams.put("listId",listId);
            batchActionContext.updateBatchContext(context.getCurrentNode(),updatedParams);
            log.info("Collecting Add To Marketo List parameters with ListId {}, LeadId {}", listId,leadId);
        }
    }

    @Action(isBatchable = true)
    public void addToMarketoProgram(GenericActionConfig params, GraphContext context) {
        var entityName = context.getCurrentBatch().getSyncariEntityName();
        BatchActionContext batchActionContext = context.getBatchActionContext();
        //Run actions on collected batch of lead ids
        if (batchActionContext.shouldRunActions()) {
            List<Object> collectedParams = batchActionContext.get(context.getCurrentNode().getId());
            Map<String, Map<String, Set<String>>> programToLeadsWithStatusMap = new HashMap<>();
            if (collectedParams != null && !collectedParams.isEmpty()) {
                log.info("Found {} Add To Marketo Program batch items", collectedParams.size());
                Map<String, Object> firstParam = (Map<String, Object>) collectedParams.get(0);
                String synapseId = firstParam.get("synapseId").toString();
                Connector synapse = connectorService.find(synapseId).get();
                ConnectorInfo connectorInfo = transformer.toConnectorInfo(synapse);
                collectedParams.forEach(p -> {
                    Map<String, Object> currentParam = (Map<String, Object>) p;
                    String programId = currentParam.get("programId").toString();
                    String leadId = currentParam.getOrDefault("leadId", "").toString();
                    String programStatus = currentParam.getOrDefault("programStatus", "").toString();
                    if(!StringUtils.isBlank(programId) && !StringUtils.isBlank(leadId)) {
                        Map<String, Set<String>> membersWithProgramStatus = programToLeadsWithStatusMap.getOrDefault(programId, new HashMap<String, Set<String>>());
                        Set<String> leadIds = membersWithProgramStatus.getOrDefault(programStatus, new HashSet<>());
                        leadIds.add(leadId);
                        membersWithProgramStatus.put(programStatus, leadIds);
                        programToLeadsWithStatusMap.put(programId, membersWithProgramStatus);
                    }
                });

                if(!programToLeadsWithStatusMap.isEmpty()){
                    long totalLeadsProcessed = 0;
                    // iterate over each entry of map and add leads to corresponding programs with given status
                    for(Map.Entry<String, Map<String, Set<String>>> programLeads: programToLeadsWithStatusMap.entrySet()) {
                        String programId = programLeads.getKey();
                        for (Map.Entry<String, Set<String>> membersByStatus : programLeads.getValue().entrySet()) {
                            String programStatus = membersByStatus.getKey();
                            Set<String> leadIds = membersByStatus.getValue();
                            List<Integer> marketoLeadIds = getMarketoLeadIds(entityName, synapseId, leadIds);
                            log.info("Adding leads {} to Marketo Program {}, syncari leads {}", marketoLeadIds,programId, leadIds);
                            if(!marketoLeadIds.isEmpty()){
                                long results = marketoService.addToProgram(programId, marketoLeadIds, programStatus, connectorInfo);
                                log.info("Added {} out of {} leads to Marketo Program {} with program status {}", results, marketoLeadIds, programId, programStatus);
                                totalLeadsProcessed += results;
                            }
                        }
                    }
                    log.info("Total Leads Processed: {}", totalLeadsProcessed);
                }
            }
        }else{
            //Collect mode. Keep adding leads to batchaction context
            String programId = tokenHelper.resolveTokens(context, Objects.toString(params.get("programId"), ""));
            String leadId = tokenHelper.resolveTokens(context, Objects.toString(params.get("leadId"), ""));
            String programStatus = tokenHelper.resolveTokens(context, Objects.toString(params.get("programStatus"), ""));
            if(!StringUtils.isBlank(programId) && !StringUtils.isBlank(leadId)) {
                var updatedParams = new HashMap<>(params.getConfigMap());
                updatedParams.put("leadId", leadId);
                updatedParams.put("programId", programId);
                updatedParams.put("programStatus", programStatus);
                batchActionContext.updateBatchContext(context.getCurrentNode(), updatedParams);
                log.info("Collecting Add To Marketo Program parameters with ProgramId {}, LeadId {}", programId, leadId);
            }
        }
    }

    @Action(isBatchable = true)
    public void addToHubspotList(GenericActionConfig params, GraphContext context) {
        BatchActionContext batchActionContext = context.getBatchActionContext();
        //Run actions on collected batch of contact ids
        if (batchActionContext.shouldRunActions()) {
            List<Object> collectedParams = batchActionContext.get(context.getCurrentNode().getId());
            if (collectedParams != null && !collectedParams.isEmpty()) {
                log.info("Found {} Add To Hubspot List batch items", collectedParams.size());
                Map<String, Object> firstParam = (Map<String, Object>) collectedParams.get(0);
                String synapseId = firstParam.get("synapseId").toString();
                String valueType = firstParam.get("valueType").toString();
                Connector synapse = connectorService.find(synapseId).get();
                ConnectorInfo connectorInfo = transformer.toConnectorInfo(synapse);
                boolean isEmail = "email".equalsIgnoreCase(valueType);
                Map<String, List> data = new HashMap<>();
                collectedParams.forEach(p -> {
                    Map<String, Object> currentParam = (Map<String, Object>) p;
                    String listId = currentParam.get("listId").toString();
                    String value = currentParam.get("value").toString();
                    data.putIfAbsent(listId, new ArrayList<>());
                    data.get(listId).add(isEmail ? value : Integer.parseInt(value));
                });
                if(!data.isEmpty()){
                	for (Entry<String, List> entry : data.entrySet()) {
                		String listId = entry.getKey();
                		if(entry.getValue().isEmpty()) continue;
						Map results = hubspotService.addToList(connectorInfo, Integer.parseInt(listId),
								isEmail ? List.of() : entry.getValue(), isEmail ? entry.getValue() : List.of());
                		log.info("Add to list {} result {} using {}", listId, results, valueType);
					}
                }
            }
        }else{
            //Collect mode. Keep adding values to batchaction context
            String listId = tokenHelper.resolveTokens(context, params.get("listId").toString());
            String value = tokenHelper.resolveTokens(context, params.get("value").toString());
            String valueType = tokenHelper.resolveTokens(context, params.get("valueType").toString());
            var updatedParams = new HashMap<>(params.getConfigMap());
            updatedParams.put("listId",listId);
            updatedParams.put("value",value);
            updatedParams.put("valueType",valueType);
            batchActionContext.updateBatchContext(context.getCurrentNode(),updatedParams);
            log.info("Collecting Add To Hubspot List parameters with ListId {}, valueType {}", listId, valueType);
        }
    }

    @Action(isBatchable = true)
    public void createExternalRecord(GenericActionConfig params, GraphContext context) {
        BatchActionContext batchActionContext = context.getBatchActionContext();
        final boolean batchModeParamSet = params.containsKey("batchMode");
        //If batchMode param is set, use the set value, otherwise default to true, for backward compatibility
        boolean batchMode = batchModeParamSet ? BooleanType.VALUE.convert(params.get("batchMode")) : true;
        //if not batch mode, and not being called to finalize batch actions, create an record
        if (!batchMode && !batchActionContext.shouldRunActions()) {
            String entityId = params.get("entityId").toString();
            EntityDefinition entityDef = context.cache(entityId, () -> schemaService.getEntity(entityId));
            EntityData record = createExternalRecordObject(params, context);
            final String synapseId = entityDef.getConnectorId();
            Connector synapse = context.cache(synapseId, () -> connectorService.find(synapseId).get());
            ConnectorInfo connectorInfo = transformer.toConnectorInfo(synapse);
            SyncRequest request = new SyncRequest().Builder(connectorInfo, transformer.toEntitySchema(entityDef, synapse));
            request.addData(synapseId, record);
            request.setBatchMode(false);
            SyncResponse response = dataServiceFactory.getDataService(synapse.getMetadata()).create(request);
            final List<Result> results = response.getResults();
            final Result result = results.isEmpty() ? new Result(false, null) : results.get(0);
            context.put("Result From " + context.getCurrentNode().getName(), result);
            if(!response.isSuccess()) {
                log.error("response - {}", response);
                String msg = String.format("Create failed for synapse %s record %s", synapseId, !response.getErrors().isEmpty() ? response.getErrors() : "");
                throw new RuntimeException(msg);
            }
            return;
        }
        if (batchMode && batchActionContext.shouldRunActions()) {
            List<Object> collectedParams = batchActionContext.get(context.getCurrentNode().getId());
            if (collectedParams != null && !collectedParams.isEmpty()) {
                log.info("Found {} records to create externally", collectedParams.size());
                String entityId = params.get("entityId").toString();
                EntityDefinition entityDef = context.cache(entityId, () -> schemaService.getEntity(entityId));
                Map<String, Object> firstParam = (Map<String, Object>) collectedParams.get(0);
                String synapseId = firstParam.get("synapseId").toString();
                Connector synapse = connectorService.find(synapseId).get();
                ConnectorInfo connectorInfo = transformer.toConnectorInfo(synapse);
                SyncRequest request = new SyncRequest().Builder(connectorInfo, transformer.toEntitySchema(entityDef, synapse));
    			collectedParams.forEach(p -> {
    				Map<String, Object> currentParam = (Map<String, Object>) p;
    				EntityData ed = (EntityData) currentParam.get("record");
    				request.addData(synapseId, ed);
                    log.debug("Record to be created - {}", ed);
    			});
    			request.setBatchMode(true);
    			SyncResponse response = dataServiceFactory.getDataService(synapse.getMetadata()).create(request);
                log.debug("createExternalRecord response - {}", response);
    		}
        } else {
            //Collect mode. Keep adding values to batchaction context
            EntityData record = createExternalRecordObject(params, context);

            var updatedParams = new HashMap<>(params.getConfigMap());
            updatedParams.put("record", record);
            batchActionContext.updateBatchContext(context.getCurrentNode(), updatedParams);
            log.info("Collecting createexternal record {}", record);
        }
    }

    protected EntityData createExternalRecordObject(GenericActionConfig params, GraphContext context) {
        String entityId = params.get("entityId").toString();
        EntityDefinition entityDef = context.cache(entityId, () -> schemaService.getEntity(entityId));
        List<Map<String, Map<String, String>>> sortFields = (List<Map<String, Map<String, String>>>) params.get("insertFields");
        long createdAt = Instant.now().toEpochMilli();
        EntityData record = new EntityData(entityDef.getApiName()).setCreatedAt(createdAt).setLastModified(createdAt).setNew(true).setSyncariEntityId(ObjectId.get().toHexString());
        for (Map<String, Map<String, String>> s : sortFields) {
            String attributeId = s.get("updateField").get("value");
            String newValue = s.get("newValue").get("value");
            AttributeDefinition attribute = entityDef.getAttribute(attributeId);
            record.addValue(attribute.getApiName(), attribute.convert(tokenHelper.resolveTokensObject(context, newValue)));
        }
        return record;
    }

    @Action
    public void updateExternalRecord(GenericActionConfig params, GraphContext context) {
        String entityId = params.get("entityId").toString();
        String synapseId = params.get("synapseId").toString();
        String recordId = tokenHelper.resolveTokens(context, params.get("recordId").toString());
        EntityDefinition entityDef = context.cache(entityId, () -> schemaService.getEntity(entityId));
        List<Map<String, Map<String, String>>> sortFields = (List<Map<String, Map<String, String>>>) params.get("updateFields");
        long updatedAt = Instant.now().toEpochMilli();
        EntityData record = new EntityData(entityDef.getApiName()).setLastModified(updatedAt)
                .setId(recordId)
                .setNew(false).setSyncariEntityId(ObjectId.get().toHexString());
        for (Map<String, Map<String, String>> s : sortFields) {
            String token = s.get("updateField").get("value");
            String attributeId = token;

            // Resolve tokens in updateField if present
            if(TokenHelper.hasTokens(attributeId)){
                String resolvedValue = tokenHelper.resolveTokens(context, attributeId);
                if(resolvedValue != null){
                    attributeId = resolvedValue;
                }
            }

            String newValue = s.get("newValue").get("value");
            AttributeDefinition attribute = entityDef.getAttribute(attributeId);

            // If getAttribute returns null, it might be an API name instead of ID
            // Try to find by API name by iterating through attributes
            if(attribute == null && !StringUtils.isBlank(attributeId)) {
                final String finalAttributeId = attributeId;
                attribute = entityDef.getAttributes().stream()
                    .filter(a -> finalAttributeId.equals(a.getApiName()))
                    .findFirst()
                    .orElse(null);
            }

            if(attribute != null) {
                record.addValue(attribute.getApiName(), attribute.convert(tokenHelper.resolveTokensObject(context, newValue)));
            } else {
                var msg = String.format("Attribute not found for id/apiName: %s -> %s in entity: %s", token, attributeId, entityDef.getApiName());
                log.warn(msg);
                throw new RuntimeException(msg);
            }
        }

        var updatedParams = new HashMap<>(params.getConfigMap());
        updatedParams.put("record", record);
        Connector synapse = connectorService.find(synapseId).get();
        ConnectorInfo connectorInfo = transformer.toConnectorInfo(synapse);
        SyncRequest request = new SyncRequest().Builder(connectorInfo, transformer.toEntitySchema(entityDef, synapse));
        request.addData(synapseId, record);
        log.debug("Record to be updated - {}", record);
        SyncResponse response = dataServiceFactory.getDataService(synapse.getMetadata()).update(request);
        log.debug("updateExternalRecord response - {}", response);
        context.put(String.format("Result From %s", context.getCurrentNode().getName()), response);
        if(!response.isSuccess()) {
            log.error("response - {}", response);
            String msg = String.format("Update failed for synapse %s record %s %s", synapseId, recordId, !response.getErrors().isEmpty() ? response.getErrors() : "");
            throw new RuntimeException(msg);
        }
    }

    @Action
    public void deleteExternalRecord(GenericActionConfig params, GraphContext context) {
        String entityId = params.get("entityId").toString();
        String synapseId = params.get("synapseId").toString();
        String recordId = tokenHelper.resolveTokens(context, params.get("recordId").toString());
        EntityDefinition entityDef = context.cache(entityId, () -> schemaService.getEntity(entityId));
        long updatedAt = Instant.now().toEpochMilli();
        EntityData record = new EntityData(entityDef.getApiName()).setLastModified(updatedAt)
                .setId(recordId)
                .setNew(false).setSyncariEntityId(ObjectId.get().toHexString());

        Connector synapse = connectorService.find(synapseId).get();
        ConnectorInfo connectorInfo = transformer.toConnectorInfo(synapse);
        SyncRequest request = new SyncRequest().Builder(connectorInfo, transformer.toEntitySchema(entityDef, synapse));
        request.addData(synapseId, record);
        log.debug("Record to be deleted - {}", record);
        SyncResponse response = dataServiceFactory.getDataService(synapse.getMetadata()).delete(request);
        log.debug("deleteExternalRecord response - {}", response);
        context.put(String.format("Action Result From %s", context.getCurrentNode().getName()), response);
        if(!response.isSuccess()) {
            log.error("response - {}", response);
            String msg = String.format("Delete failed for synapse %s record %s %s", synapseId, recordId, !response.getErrors().isEmpty() ? response.getErrors() : "");
            throw new RuntimeException(msg);
        }
    }
    
    protected List<Integer> getMarketoLeadIds(String syncariEntityName, String synapseId, Set<String> leadIds){
        if(!leadIds.isEmpty() && ObjectId.isValid(leadIds.stream().findFirst().get())){
            // if its hexString id, means these are syncari ids for lead - find equivalent marketo leadIds and return
            EntityDefinition marketoLead = schemaService.getEntity(synapseId, "lead");
            List<IdMapping> leadMappings = idMappingRepo.findBySyncariIds(syncariEntityName, leadIds);
            return leadMappings.stream().flatMap(ids ->
                    ids.getMapping(synapseId, marketoLead.getId()).stream().map(marketoId -> Integer.valueOf(marketoId.getEntityId()))
            ).collect(Collectors.toList());
        } else {
            // else the ids are marketo lead ids - return with converted int values
            return leadIds.stream().map(id -> Ints.tryParse(id)).filter(Objects::nonNull).collect(Collectors.toList());
        }

    }

    @Action
    public void removeFromMarketoList(GenericActionConfig params, GraphContext context) {
        var entityName = context.getCurrentBatch().getSyncariEntityName();
        String synapseId = params.get("synapseId").toString();
        Connector synapse = connectorService.find(synapseId).get();
        ConnectorInfo connectorInfo = transformer.toConnectorInfo(synapse);
        String listId = tokenHelper.resolveTokens(context, params.get("listId").toString());
        String leadId = tokenHelper.resolveTokens(context, params.get("leadId").toString());
        List<Integer> marketoLeadId = getMarketoLeadIds(entityName, synapseId, Set.of(leadId));
        if (!marketoLeadId.isEmpty()) {
            long results = marketoService.removeFromList(listId, marketoLeadId, connectorInfo);
            log.info("Removed {} out of {} leads Marketo list {} ", results, leadId, listId);
        };
    }

    @Action(isBatchable = true)
    public void requeueRecord(GenericActionConfig params, GraphContext context) {
        BatchActionContext batchActionContext = context.getBatchActionContext();
        boolean processExpiredRecord = Optional.ofNullable(params.get("processExpiredRecord"))
                .map(o -> BooleanType.VALUE.convert(o))
                .orElse(false);

        //if processExpiredRecord is set and
        if (processExpiredRecord && !batchActionContext.shouldRunActions()) {
            final StagedBatchRecord stagedBatchRecord = context.getStagedBatchRecord();
            //we only support this on source side right now
            if (stagedBatchRecord != null && stagedBatchRecord.expiredRecordMarkedForProcessing()) {
                //this is the last time the record is running thru pipeline after expiry
                //let it run thru the pipeline
                context.addResult(Map.of(
                        "expiredRecord", stagedBatchRecord.getEntityData(),
                        "expiredAt", stagedBatchRecord.getRequeueRequest().getRetryTimeLimit(),
                        "enteredAt", ZonedDateTime.ofInstant(
                                stagedBatchRecord.getRequeueRequest().getCreatedAt().toInstant(), ZoneId.of("UTC"))
                ));
                return;
            }
        }
        //Run actions on collected batch of records
        if (batchActionContext.shouldRunActions()) {
            List<Object> collectedParams = batchActionContext.getOrDefault(context.getCurrentNode().getId(), List.of());
            requeueAndClearContext(context, batchActionContext, collectedParams);
        } else {
            if (context.getStagedBatchRecord() != null) {
                batchActionContext.updateBatchContext(context.getCurrentNode(), requeueSourceRecord(context.getStagedBatchRecord(), params.getConfigMap(), context, processExpiredRecord));
            } else if (context.getSyncariRecord() != null) {
                //do we support this at all?
                batchActionContext.updateBatchContext(context.getCurrentNode(), requeueSyncariRecord(context.getSyncariRecord(), params.getConfigMap(), context));
            }else{
                log.warn("Neither syncari record nor stagedbatch record found while requeuing in graph {}, node {}",context.getGraph().getName(),context.getCurrentNode().getName());
            }
            //See if we have collected >=100 records so far. If so, persist them and clear the batchcontext
            List<Object> collectedParams = batchActionContext.getOrDefault(context.getCurrentNode().getId(),List.of());
            if(collectedParams.size() >= 100){
                requeueAndClearContext(context, batchActionContext, collectedParams);
            }
        }
    }

    @Action()
    public void createSalesforceFile(GenericActionConfig params, GraphContext context) {
        String synapseId = params.get("synapseId").toString();
        String fileLink = tokenHelper.resolveTokens(context, params.getOrDefault("fileLink", "").toString());
        String fileName = tokenHelper.resolveTokens(context, params.getOrDefault("name", "").toString());
        String parentId = tokenHelper.resolveTokens(context, params.getOrDefault("recordId", "").toString());
        final ConnectorInfo connector = transformer.toConnectorInfo(context.cache(synapseId, () -> connectorService.get(synapseId)));

        if (StringUtils.isEmpty(fileName)) {
            fileName = fileLink.substring(fileLink.lastIndexOf("/") + 1);
        }

        SyncRequest request = new SyncRequest();
        EntitySchema contentDocumentSchema = salesforceService.describe(new DescribeRequest(connector, "ContentDocument")).get();

        EntityData documentEntity = new EntityData(contentDocumentSchema.getApiName())
                .setConnectorId(connector.getId())
                .setSyncariEntityId(UUID.randomUUID().toString())
                .setValues(new HashMap<>(Map.of(
                        "Title", fileName,
                        EntityData.SYNCARI_FILE_LINK_FIELD_NAME, fileLink
                )));

        request.setConnector(connector)
                .setEntitySchema(contentDocumentSchema).setEntitySchemaWithMappedFields(contentDocumentSchema).setData(Map.of(connector.getId(), List.of(documentEntity)))
                .setStorage(gcsFileManager);

        SyncResponse createResponse = salesforceService.create(request);

        if (!createResponse.isSuccess()) {
            createResponse.getErrors().forEach(err -> notificationService.broadcast("Failed to create Salesforce File ", err, NotificationType.ERROR));
            throw new RuntimeException("Failed to create Salesforce File ");
        }else{
            if (StringUtils.isNotEmpty(parentId)){
                createResponse.getResults().stream().findFirst().ifPresentOrElse(res -> {
                    String contentDocumentId = res.getId(); // this is the contentdocumentid
                    SyncRequest requestContentLink = new SyncRequest();
                    EntitySchema contentDocumentLinkSchema = salesforceService.describe(new DescribeRequest(connector, "ContentDocumentLink")).get();
                    EntityData documentLinkEntity = new EntityData(contentDocumentLinkSchema.getApiName())
                            .setConnectorId(connector.getId())
                            .setSyncariEntityId(UUID.randomUUID().toString())
                            .setValues(new HashMap<>(Map.of(
                                    "ContentDocumentId", contentDocumentId,
                                    "LinkedEntityId", parentId
                            )));
                    requestContentLink.setConnector(connector)
                            .setEntitySchema(contentDocumentLinkSchema).setEntitySchemaWithMappedFields(contentDocumentLinkSchema).setData(Map.of(connector.getId(), List.of(documentLinkEntity)))
                            .setStorage(gcsFileManager);
                    log.info("Parent id {} linked to content document {}", parentId, contentDocumentId);
                    SyncResponse createContentDocLinkResponse = salesforceService.create(requestContentLink);
                    if (!createContentDocLinkResponse.isSuccess()) {
                        createContentDocLinkResponse.getErrors().forEach(err -> notificationService.broadcast("Failed to create Salesforce File ContentLinkDocument for content document " + contentDocumentId, err, NotificationType.ERROR));
                        throw new RuntimeException("Failed to create Salesforce File ContentLinkDocument");
                    } else {
                        if(!createContentDocLinkResponse.getResults().isEmpty()) {
                            context.put("Result From " + context.getCurrentNode().getName(), createContentDocLinkResponse.getResults().get(0));
                        }
                    }
                },() -> log.error("Content Version create response is successful by first is not found for parentId {}",parentId));
            }
        }
    }

    @Action()
    public void deleteSyncariRecords(GenericActionConfig params, GraphContext context) {
        Map<String, Object> predicates = (Map<String, Object>) params.get("predicate");
        String syncariEntityDefId = (String) params.get("syncariEntityDefId");
        EntityDefinition syncariEntity = context.cache(syncariEntityDefId, () -> schemaService.getEntity(syncariEntityDefId));
        boolean deleteInEnd = (boolean) params.get("deleteInEnd");
        deleteRecords(syncariEntity, predicates, deleteInEnd, context);
    }

    @Action
    public ActionResult exportSyncariRecords(GenericActionConfig params, GraphContext context) {
        return this.exportSyncariRecords.execute(params, context);
    }

    @Action
    public ActionResult createSFTPFile(GenericActionConfig params, GraphContext context) {
        return this.actionFactory.createFile().execute(params, context);
    }

    private void deleteRecords(EntityDefinition syncariEntity, Map<String, Object> predicates, boolean deleteInEnd, GraphContext context) {
        Expression expression = new PredicateParser(StringUtils.EMPTY).fromMap(predicates);
        log.debug("DeleteSyncariRecords: Expression - {}", expression);

        Optional<Criteria> redisCriteria = Optional.of(new RedisLookupCriteriaVisitor(context, expression, tokenHelper, syncariEntity, List.of()));

        Optional<Criteria> mongoCriteria = Optional.of(new LookupCriteriaVisitor(context, expression, tokenHelper,
                syncariEntity.getIdToAttributes(), List.of(), (key) -> entityRepo.hasCaseInsensitiveIndexOnField(syncariEntity, key)));

        boolean useCache = entityRepo.useCache(syncariEntity) && !context.isTestMode() && !context.isSimulationMode() ?
                context.cache(syncariEntity.getApiName() + "_index_status",
                        () -> redisUtils.indexStatus(syncariEntity.getApiName())) : false;

        PageCursor cursor = new PageCursor(null, PageDirection.next, DELETE_RECORDS_PAGE_SIZE);
        Page<EntityData> search;
        int searched=0;
        while(!(search = entityRepo.searchWithFallback(syncariEntity, redisCriteria, mongoCriteria, useCache, cursor)).getRecords().isEmpty()){
            entityRepoService.deleteRecords(syncariEntity.getApiName(), search.getRecords(), deleteInEnd);
            searched += search.getRecords().size();
            if(searched > DELETE_SYNCARI_RECORD_SEARCH_ALERT_SIZE) {
                MappingNode currentNode = context.getCurrentNode();
                String name = currentNode != null ? currentNode.getName(): "Currentnode not found";
                log.error("DeleteSyncariRecords: searched more than {} records in node - {}", DELETE_SYNCARI_RECORD_SEARCH_ALERT_SIZE, name);
            }
            log.info("DeleteSyncariRecords:Found {} records and deleting {} records, total records {}", search.getRecords().size(), search.getRecords().size(), searched);
            cursor =  new PageCursor(search.getPageInfo().getEnd(), PageDirection.next, DELETE_RECORDS_PAGE_SIZE);
        }
        context.put(syncariEntity.getApiName() + "_recordsDeleted", searched);
    }

    private ConvertRequest getConvertRequest(GenericActionConfig params, GraphContext context, Timers timer, boolean resolveIdMapping) {
        EntityData entity = (EntityData) context.get("record");
        String ownerId = params.get("ownerId") == null ? null : tokenHelper.resolveTokens(context, params.get("ownerId").toString());
        String leadId = params.get("leadId") == null ? null : tokenHelper.resolveTokens(context, params.get("leadId").toString());
        String contactId = params.get("contactId") == null ? null : tokenHelper.resolveTokens(context, params.get("contactId").toString());
        String accountId = params.get("accountId") == null ? null : tokenHelper.resolveTokens(context, params.get("accountId").toString());
        String opportunityId = params.get("opportunityId") == null ? null : tokenHelper.resolveTokens(context, params.get("opportunityId").toString());
        boolean skipOpportunity = params.get("skipOpportunity") == null ? false : (boolean) params.get("skipOpportunity");
        String convertedStatus = params.get("convertedStatus") == null ? null : tokenHelper.resolveTokens(context, params.get("convertedStatus").toString());
        ;
        String synapseId = params.get("synapseId").toString();
        EntityDefinition sfdcLead = context.cache(synapseId + "_Lead", () -> schemaService.getEntity(synapseId, "Lead"));
        String syncariEntityId = StringUtils.isBlank(leadId) ? entity.getSyncariEntityId() : leadId;
        
        String externalOwnerId = ownerId;
        String externalLeadId = leadId;
        if(resolveIdMapping) {
        	Optional<IdMapping> external = timer.time("convertSalesforceLeadAction::findLeadIdMapping", ()->idMappingRepo.findBySyncariId("lead", syncariEntityId));
        	
        	if(!StringUtils.isBlank(ownerId)) {
        		EntityDefinition sfdcOwner = context.cache(synapseId+"_User", ()->schemaService.getEntity(synapseId, "User"));
        		Optional<IdMapping> ownerMapping = timer.time("convertSalesforceLeadAction::findUserIdMapping", ()->idMappingRepo.findBySyncariId("user", ownerId));
        		externalOwnerId =ownerMapping.flatMap(e -> e.getMapping(synapseId, sfdcOwner.getId()).map(r -> r.getEntityId()))
        				.orElse(ownerId);
        	}
        	if(external.isEmpty()) {
        		log.warn("convertSalesforceLead: Did not find idMapping for lead with syncari id {}. Using incoming id {} directly ", syncariEntityId, entity.getId());
        	}
        	//if idMapping is present, use it to extract sfdc id. otherwise assume the incoming id is sfdc id
        	externalLeadId = external
        			.flatMap(e -> e.getMapping(synapseId, sfdcLead.getId()).map(r -> r.getEntityId()))
        			.orElse(StringUtils.isBlank(leadId) ? entity.getId() : leadId);
        } else {
        	externalLeadId = syncariEntityId;
        }

        ConvertRequest request = new ConvertRequest();
        request.getData()
                .add(new ConvertData().setAccountId(StringUtils.isBlank(accountId)? null : accountId)
                        .setContactId(StringUtils.isBlank(contactId)?null:contactId).setOpportunityId(StringUtils.isBlank(opportunityId)?null:opportunityId)
                        .setConvertedStatus(convertedStatus)
                        .setOwnerId(externalOwnerId)
                        .setLeadId(externalLeadId));
        request.setDoNotCreateOpportunity(skipOpportunity);
        request.setConnector(transformer.toConnectorInfo(context.cache(synapseId,()-> connectorService.get(synapseId))));
        return request;
    }

    private void requeueAndClearContext(GraphContext context, BatchActionContext batchActionContext, List<Object> collectedParams) {
        if (collectedParams != null && !collectedParams.isEmpty()) {
            requeueService.requeue(collectedParams.stream().map(c -> RequeueRequest.class.cast(c)).collect(Collectors.toList()));
            batchActionContext.clearBatchContext(context.getCurrentNode());
        }
    }

    private RequeueRequest requeueSyncariRecord(EntityData syncariRecord, Map<String, Object> params, GraphContext context) {
        return createRequeueRequest(params, context, context.getSyncariEntity().getId(), syncariRecord.getId(), false)
                .setRecordType(RequeueRequest.RecordType.SYNCARI);

    }

    private RequeueRequest requeueSourceRecord(StagedBatchRecord record, Map<String, Object> params, GraphContext context
            , boolean processExpiredRecord) {
        //mark this as deleted for this sync cycle
        record.setDeleted(true);
        return createRequeueRequest(params, context, record.getExternalEntityDefinitionId(), record.getExternalRecordId(), processExpiredRecord)
                .setRecordType(RequeueRequest.RecordType.SOURCE);
    }

    private RequeueRequest createRequeueRequest(Map<String, Object> params, GraphContext context,
                                                String entityDefinitionId, String recordId, boolean processExpiredRecord) {
        ZonedDateTime retryTimeLimit = DatetimeType.VALUE.convert(params.get("retryTimeLimit"));
        return new RequeueRequest().setGraphId(context.getGraph().getId())
                .setEntityDefinitionId(entityDefinitionId)
                .setRecordId(recordId)
                .setRetryTimeLimit(retryTimeLimit == null ? ZonedDateTime.now().plusDays(1) : retryTimeLimit)
                .setProcessExpiredRecord(processExpiredRecord)
                .setEmailAddresses(CollectionUtils.toList(params.get("emailAddresses")));
    }

    private SyncRequest createRequest(GraphContext context, Map<String, Object> params, List<String> fields, String entityName) {
        SyncRequest request = new SyncRequest();
        String synapseId = params.get("synapseId").toString();
        Connector synapse = connectorService.find(synapseId).get();
        request.setConnector(transformer.toConnectorInfo(synapse));
        EntitySchema entitySchema = transformer.toEntitySchema(schemaService.getEntity(synapseId, entityName), synapse);
        request.setEntitySchema(entitySchema);
        EntityData entityData = new EntityData(entitySchema.getApiName());
        fields.forEach(f -> entityData.addValue(f, params.get(f) ==null ? null: params.get(f)));
        request.setData(Map.of(synapseId, List.of(entityData)));
        return request;
    }

}
