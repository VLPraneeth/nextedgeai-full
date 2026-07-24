package com.syncari.karibu.rest.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.Connector;
import com.syncari.core.model.EntityDefinition;
import com.syncari.restutils.transformers.QuickStartTransformer;
import com.syncari.api.rest.controllers.data.quickstart.v2.QSPipelineConfigDTO;
import com.syncari.api.rest.controllers.data.quickstart.v2.QuickStartDTO;
import com.syncari.core.event.EventTypes;
import com.syncari.core.model.JobQueue;
import com.syncari.core.model.util.JobQueueStatus;
import com.syncari.core.quickstart.v2.*;
import com.syncari.core.quickstart.v2.QuickStartInstall.Status;
import com.syncari.karibu.rest.config.KaribuConstants;
import com.syncari.karibu.rest.controllers.data.QuickstartRunTO;
import com.syncari.karibu.rest.exceptions.BadRequestException;
import com.syncari.karibu.rest.exceptions.NotFoundException;
import com.syncari.karibu.rest.request.QuickStartCreateRequest;
import com.syncari.karibu.rest.request.QuickStartRunRequest;
import com.syncari.karibu.rest.response.ErrorType;
import com.syncari.karibu.rest.response.ValidListResponse;
import com.syncari.karibu.rest.response.ValidResponse;
import com.syncari.karibu.rest.util.JobQueueUtils;
import com.syncari.karibu.rest.util.QuickStartUtils;
import com.syncari.karibu.rest.util.ResponseUtils;
import com.syncari.restutils.utils.ApiUtils;
import com.syncari.utils.KeyValue;
import com.syncari.utils.file.FileUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.util.*;

import static com.syncari.core.security.Permissions.READ_STUDIO;
import static com.syncari.core.security.Permissions.WRITE_STUDIO;
import static com.syncari.utils.I18n.i18n;

@Slf4j
@RestController
@RequestMapping(value = "/api/v1/quickstart")

public class QuickStartController {
    @Autowired
    QuickStartV2Service service;

    @Autowired
    ResponseUtils responseUtils;

	@Autowired
	JobQueueUtils jobQueueUtils;

	@Autowired
	QuickStartUtils quickstartUtils;

	@Autowired
	QuickStartTransformer qsTransformer;

	@Autowired
	ApiUtils apiUtils;

	@Autowired
	ObjectMapper mapper;

	@Autowired
	FileUtil fileUtil;

	List installStrategies = Arrays.asList("replace", "merge");
	List autoArranges = Arrays.asList("true", "false");
	List quickStartTypes = Arrays.asList("MarketPlace", "Shared");


	@Secured(READ_STUDIO)
	@RequestMapping(method = RequestMethod.GET)
	public ResponseEntity<?> listQuickStarts(@RequestParam(value = "type") String quickStartType,
											 @RequestParam(value = "cursorToken", required = false) String cursorToken,
											 @RequestParam(value = "limit", required = false, defaultValue = KaribuConstants.MAX_LIMIT_STRING) Integer limit) {

		List<QuickStartDTO> quickStartDTOS = new ArrayList<>();

		// validate quick start type
		if (!quickStartTypes.contains(quickStartType))
			throw new BadRequestException(i18n("qs_invalid_type", quickStartType, quickStartTypes.toString()));

		if (limit == null)
			limit = KaribuConstants.MAX_LIMIT;

		// get quick start id from cursor
		String quickStartId = null;
		if (cursorToken != null)
			quickStartId = apiUtils.decodeCursor(cursorToken);

		try {
			if(quickStartType.equals("MarketPlace"))
				quickStartDTOS = qsTransformer.toQuickStartDTOs(service.getMarketplaceQuickStartByCursor(quickStartId, limit+1));

			if(quickStartType.equals("Shared"))
				quickStartDTOS = qsTransformer.toQuickStartDTOs(service.getSharedQuickStart(quickStartId, limit+1));

			// prepare and return response
			ValidListResponse validListResponse = responseUtils.convertDTOToResponse(quickstartUtils.getQuickStartListResponse(quickStartDTOS, quickStartType), limit);
			return ResponseEntity.status(HttpStatus.OK).body(validListResponse);
		} catch(Exception e) {
			e.printStackTrace();
			ValidResponse response = responseUtils.populateErrorResponse(e.getMessage());
			return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
		}
	}

	@Secured(WRITE_STUDIO)
	@RequestMapping(method = RequestMethod.POST, consumes = { MediaType.MULTIPART_FORM_DATA_VALUE, MediaType.APPLICATION_JSON_VALUE })
	public ResponseEntity<?> createQuickStart(@NotBlank @RequestHeader(value = "clientRequestId") String requestClientId,
											  @RequestPart QuickStartCreateRequest request, @RequestPart MultipartFile icon) {
		try {
			// all the quickstart coming from syncari APIs should not be published to global library
			request.setPublishToQuickStartLibrary("dontPublish");

			if (null != icon)
				request.setIcon(icon);

			List<String> errors = quickstartUtils.validateQuickStartCreateRequest(request);
			if (!errors.isEmpty()) {
				log.info(String.format("QuickStart create request errors: %s", errors));
				ValidResponse response = responseUtils.populateErrorResponse(new ErrorType(i18n("qs_error_create_request"), errors));
				return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
			}

			QSPipelineConfigDTO qsPipelineConfigDTO = quickstartUtils.getQsPipelineConfigDTO(request.getEntities());

			var quickStartDTO = new QuickStartDTO()
					.setId(null)
					.setDisplayName(request.getDisplayName())
					.setDescription(request.getDescription() != null ? request.getDescription() : "")
					.setPostInstallationInstruction(request.getPostInstallationInstruction() != null ? request.getPostInstallationInstruction() : "")
					.setPipelines(qsPipelineConfigDTO)
					.setTags(request.getTags() != null ? request.getTags() : null)
					.setShareWithInstances(request.getShareWithInstances() != null ?
							request.getShareWithInstances() : null)
					.setShareWithOrg(request.isShareWithOrg())
					.setPublishToQuickStartLibrary(request.getPublishToQuickStartLibrary());

			QuickStart quickStart = service.saveQuickStartDraft(
					qsTransformer.toQuickStart(quickStartDTO),
					quickStartDTO.getShareWithInstances(),
					quickStartDTO.getPublishToQuickStartLibrary(),
					quickStartDTO.isShareWithOrg(),
					request.getIcon() != null ? request.getIcon().getInputStream() : null,
					request.getIcon() != null ? fileUtil.sanitizeFileName(request.getIcon().getOriginalFilename()) : null
			);

			// prepare and return response
			ValidResponse validResponse = responseUtils.convertDTOToResponse(quickstartUtils.getQuickStartResponse(qsTransformer.toQuickStartDTO(quickStart), null));
			return ResponseEntity.status(HttpStatus.OK).body(validResponse);

		} catch (Exception e) {
			e.printStackTrace();
			ValidResponse response = responseUtils.populateErrorResponse(e.getMessage());
			return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
		}
	}

	@Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.POST, value = "/{qsId}/install")
    public ResponseEntity<?> install(
            @NotBlank @RequestHeader(value = "clientRequestId") String requestClientId, @PathVariable String qsId) {
        try {
        	KeyValue keyValue = service.getMarketplaceQuickStartConfig(qsId);
        	List<ErrorType> errors = getErrors(keyValue);
        	if(!errors.isEmpty()) {
        		ValidResponse response = responseUtils.populateErrorResponse(errors);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        	}
        	KeyValue inputs = new KeyValue("id", qsId);
			service.getInstallDynamicStepUpdate(2, inputs);
        	QuickStartInstall install = service.installQuickStart(qsId);
			ValidResponse response = responseUtils
					.convertDTOToResponse(Map.of("qsInstallId", install.getId()));
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
        } catch (Exception e) {
        	log.error(ExceptionUtils.getStackTrace(e));
            if (StringUtils.contains(e.getMessage(), "not found")) {
                throw new NotFoundException(QuickStart.class, "quickStartId", qsId);
            } else {
                ValidResponse response = responseUtils.populateErrorResponse(e.getMessage());
                return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
            }
        }
    }
    
    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.POST, value = "/{qsId}/install/{qsInstallId}/cancel")
    public ResponseEntity<?> cancel(
    		@NotBlank @RequestHeader(value = "clientRequestId") String requestClientId, @PathVariable String qsId) {
    	try {
    		service.cancelQuickStartInstall(qsId);
    		ValidResponse response = new ValidResponse();
    		return ResponseEntity.status(HttpStatus.OK).body(response);
    	} catch (Exception e) {
    		log.error(ExceptionUtils.getStackTrace(e));
    		if (StringUtils.contains(e.getMessage(), "not found")) {
    			throw new NotFoundException(QuickStart.class, "quickStartId", qsId);
    		} else {
    			ValidResponse response = responseUtils.populateErrorResponse(e.getMessage());
    			return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    		}
    	}
    }
    
    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.GET, value = "/{qsId}/install/{qsInstallId}")
    public ResponseEntity<?> getStatus(
    		@NotBlank @RequestHeader(value = "clientRequestId") String requestClientId, @PathVariable String qsInstallId) {
    	try {
    		Optional<QuickStartInstall> qs = service.findQuickStartInstall(qsInstallId);
    		
			Status status = qs.get().getStatus();
			Map<String, Object> resp = new HashMap<>();
			resp.put("qsInstallId", qsInstallId);
			resp.put("qsInstallStatus", status);
			if (status == Status.ERROR) {
				resp.put("errorDetail", qs.get().getErrorMsg());
			}
			ValidResponse response = responseUtils.convertDTOToResponse(resp);
            return ResponseEntity.status(HttpStatus.OK).body(response);
    	} catch (Exception e) {
    		log.error(ExceptionUtils.getStackTrace(e));
    		if (StringUtils.contains(e.getMessage(), "not found")) {
    			throw new NotFoundException(QuickStartInstall.class, "quickstartId", qsInstallId);
    		} else {
    			ValidResponse response = responseUtils.populateErrorResponse(e.getMessage());
    			return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    		}
    	}
    }

	@Secured(WRITE_STUDIO)
	@RequestMapping(method = RequestMethod.POST, value = "/{qsId}/run")
	public ResponseEntity<?> runQuickstart(@NotBlank @RequestHeader(value = "clientRequestId") String requestClientId,
										   @PathVariable String qsId, @Valid @RequestBody QuickStartRunRequest quickstartRunRequest) {
		try {

			log.info(String.format("QuickStart run request body: %s", quickstartRunRequest));
			String renderType;
			int synapseCount = 0;
			String synapseName;

			// validate install strategy
			if (!installStrategies.contains(quickstartRunRequest.getInstallStrategy().toString()))
				throw new BadRequestException(i18n("qs_invalid_install_strategy", quickstartRunRequest.getInstallStrategy().toString(),
						installStrategies.toString()));

			// validate autoArrange
			if (!autoArranges.contains(quickstartRunRequest.getAutoArrange().toString()))
				throw new BadRequestException(i18n("qs_invalid_auto_arrange", quickstartRunRequest.getAutoArrange().toString(),
						autoArranges.toString()));

			// cancel any existing quickstarts
			try {
				service.cancelQuickStartInstall(qsId);
			} catch (Exception e) {
				// ignore if there is no quickstart to cancel
			}

			// Put the quickstart inprogress
			KeyValue keyValueQSConfig = service.getMarketplaceQuickStartConfig(qsId);

			// check for unsupported conflicts
			List<String> errorTypes = quickstartUtils.getUnsupportedConflictErrors(keyValueQSConfig);
			if (!errorTypes.isEmpty()) {
				log.info(String.format("QuickStart run errorTypes: %s", errorTypes));
				ValidResponse response = responseUtils.populateErrorResponse(new ErrorType(i18n("qs_unsupported_conflicts"), errorTypes));
				return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
			}

			// get quick start to pull out the required synapses to validate qsSynapseName. Values to be used in verifySynapseStep
			QuickStart qs = service.findQuickStartForInstall(qsId);
			List<String> qsSynapses = quickstartUtils.getQsSynapses(qs);

			// get steps. this is possibly no longer needed now that we have qsSynapses instead of steps
			List<String> steps = quickstartUtils.getQuickStartSteps(keyValueQSConfig);
			log.info(String.format("QuickStart run steps: %s", steps));

			// check for match or resolve steps have matching synapse
			List<String> synapseListErrors = quickstartUtils.verifySynapses(quickstartRunRequest, qsSynapses);
			if (!synapseListErrors.isEmpty()) {
				log.info(String.format("QuickStart run synapseListErrors: %s", synapseListErrors));
				ValidResponse response = responseUtils.populateErrorResponse(new ErrorType(i18n("qs_error_matching_synapses"), synapseListErrors));
				return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
			}

			// convert request to new api inputs
			KeyValue keyValueInput = quickstartUtils.convertQuickstartRunRequest(qsId, quickstartRunRequest);

			// call step 2 to get things started
			keyValueInput.set("stepNumber", "2");
			QSAuthoringConfig step2Response = service.getInstallDynamicStepUpdate(2, keyValueInput);

			// get render type for input into step 3
			List<String> renderTypesStep2 = quickstartUtils.getRenderTypes(step2Response);

			// if render types are empty throw error
			if (renderTypesStep2.isEmpty())
				throw new RuntimeException(i18n("qs_run_error", qsId));

			renderType = renderTypesStep2.get(0);

			// get synapse mapping based on core response and the api request
			List<QuickstartRunTO> synapseMatchesTOS = quickstartUtils.getSynapseMappings(quickstartRunRequest, step2Response,
					service.findQuickStartForInstall(qsId));
			log.debug(String.format("QuickStart run synapseMatchesTOS: %s", synapseMatchesTOS));

			// check for errors where synpase id is null
			List<String> requestSynapseErrors = quickstartUtils.getRequestErrors(synapseMatchesTOS, true);

			// if errors return error response
			if (!requestSynapseErrors.isEmpty()) {
				log.info(String.format("QuickStart run requestSynapseErrors: %s", requestSynapseErrors));
				ValidResponse response = responseUtils.populateErrorResponse(new ErrorType(i18n("qs_error_matching_field"),
						requestSynapseErrors));
					return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
			}

			// add synapse mapping to the key value input needed for calls to the service layer
			synapseCount = synapseMatchesTOS.size();
			if (synapseCount > 0)
				keyValueInput.set("matchsynapses", quickstartUtils.getMatchSynapse(synapseMatchesTOS));

			// if all checks out run the quickstart as is
			if (renderType.equals("quickStartInstallReview")) {
				QuickStartInstall qsInstall = service.installQuickStart(qsId);
				ValidResponse response = responseUtils.convertDTOToResponse(jobQueueUtils.getJobQueueResponse(getJobQueue(qsInstall, qsId)));
				return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
			}

			// if need to map call step 3
			KeyValue keyValueInput3 = keyValueInput;
			keyValueInput3.set("stepNumber", 3);
			QSAuthoringConfig step3Response = service.getInstallDynamicStepUpdate(3, keyValueInput3);

			int i = 0;
			int stepNumber = 4;
			QSAuthoringConfig stepResponse = step3Response;

			// loop through subsequent mapping calls
			while ((renderType.equals("quickStartInstallErrorResolution") || renderType.equals("schemaMatcher"))
					&& i < synapseCount) {

				synapseName = quickstartUtils.getSynapseName(stepResponse);

				List<QuickstartRunTO> qsRunTOS = quickstartUtils.getUpdateFieldMappings(quickstartRunRequest, stepResponse,
						synapseMatchesTOS, synapseName);
				log.debug(String.format("QuickStart run qsRunTOS: %s", qsRunTOS));

				List<String> requestErrors = quickstartUtils.getRequestErrors(qsRunTOS, false);
				if (!requestErrors.isEmpty()) {
					log.info(String.format("QuickStart run requestErrors: %s", requestErrors));
					ValidResponse response = responseUtils.populateErrorResponse(new ErrorType(i18n("qs_request_errors"),
							requestErrors));
					return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
				}

				// call step 4
				KeyValue keyValueInput4 = quickstartUtils.convertQuickstartRunRequest(qsId, quickstartRunRequest);
				keyValueInput4.set("matchsynapses", quickstartUtils.getMatchSynapse(synapseMatchesTOS));

				keyValueInput4.set("stepNumber", stepNumber);
				keyValueInput4.set(StringUtils.join("matchSynapseEntityAndField",
								quickstartUtils.getQsSynapseName(synapseMatchesTOS, synapseName)),
						quickstartUtils.getRequestFieldMapping(qsRunTOS, stepResponse, synapseName));
				QSAuthoringConfig step4Response = service.getInstallDynamicStepUpdate(stepNumber, keyValueInput4);
				stepResponse = step4Response;

				// get render type for next step
				List<String> renderTypesStep4 = quickstartUtils.getRenderTypes(stepResponse);
				renderType = renderTypesStep4.get(0);

				i = i + 1;
				stepNumber = stepNumber + 1;

			}

			// log all unresolved dependencies left after mapping th eones came in request
			logUnresolvedDependencies(qsId);

			// check if all checks out run the quickstart as is
			if (renderType.equals("quickStartInstallReview")) {
				QuickStartInstall qsInstall = service.installQuickStart(qsId);
				ValidResponse response = responseUtils.convertDTOToResponse(jobQueueUtils.getJobQueueResponse(getJobQueue(qsInstall, qsId)));
				return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
			} else {
				throw new RuntimeException(i18n("qs_run_error", qsId));
			}

		} catch (BadRequestException bre) {
			throw new BadRequestException(bre.getMessage());
		} catch (Exception e) {
			log.error(ExceptionUtils.getStackTrace(e));
			if (StringUtils.contains(e.getMessage(), "No running install found")) {
				throw new NotFoundException(QuickStartInstall.class, "quickstartId",  qsId);
			} else {
				ValidResponse response = responseUtils.populateErrorResponse(e.getMessage());
				return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
			}
		}
	}



	private List<ErrorType> getErrors(KeyValue keyValue) {
		List<KeyValue> configs = ((KeyValue)keyValue.get("config")).get("configuration");
		List<ErrorType> errorResponse = new ArrayList<>();
		Map<String, String> errorMap = Map.of("create_synapse", "Missing required synapse", "service_credentials",
				"Missing service creds", "reference_data", "Missing required reference dataset", "select_matches",
				"There are conflict resources","create_entities", "Missing %s entity mapping or its attributes, please fix that manually");
		configs.forEach(c -> {
			if(!c.containsKey("resolutionData")) return;
			String type = ((KeyValue)c.get("resolutionData")).get("type");
			if("quickStartInstallErrorResolution".equalsIgnoreCase(c.get("renderType")) && 
					errorMap.containsKey(type)) {
				if (CollectionUtils.isNotEmpty((List<KeyValue>)((KeyValue)c.get("resolutionData")).get("matches"))){
					Optional<KeyValue> firstMatch = ((List<KeyValue>)((KeyValue)c.get("resolutionData")).get("matches")).stream().findFirst();
					if (firstMatch.get().get("label") != null){
						errorResponse.add(new ErrorType(String.format(errorMap.get(type),firstMatch.get().get("label").toString())));
					}
				}else{
					errorResponse.add(new ErrorType(errorMap.get(type)));
				}
			}
		});
		return errorResponse;
	}

	private JobQueue getJobQueue(QuickStartInstall qsInstall, String quickstartId) {
		Map<String, Object> jobDetails = new HashMap<>();
		jobDetails.put("quickstartId", quickstartId);

		JobQueue jobQueue = new JobQueue();
		jobQueue.setJobDetails(jobDetails);
		jobQueue.setJobType(EventTypes.INSTALL_QUICK_START);
		jobQueue.setStatus(JobQueueStatus.queued);
		jobQueue.setId(qsInstall.getJobQueueId());

		return jobQueue;
	}

	private void logUnresolvedDependencies(String qsId){
		var foundQsInstall = service.findQuickStartInstallByQuickStartId(qsId, QuickStartInstall.Status.INPROGRESS);
		foundQsInstall.ifPresent(qsInstall -> {
			PipelineQSConfig pipelineQSConfig = (PipelineQSConfig) qsInstall.getQuickStart().getConfiguration().get(0);
			pipelineQSConfig.findConnectorDependency(false).forEach(conn -> {
				Connector srcConn = (Connector) conn.getSourceValue();
				if(!conn.isResolved()){
					log.info("Unresolved Synapse: {}", srcConn.getName());
				} else {
					pipelineQSConfig.findAllEntityOfConnectorDependencies(srcConn.getId()).forEach(entityDep -> {
						EntityDefinition srcEntity = (EntityDefinition) entityDep.getSourceValue();
						if(!entityDep.isResolved()){
							log.info("Unresolved Entity: {}:{}", srcConn.getName(), srcEntity.getApiName());
						} else {
							pipelineQSConfig.findUnresolvedAttributeDependencyOfEntity(srcEntity.getId()).forEach(attDep -> {
								AttributeDefinition srcAttr = (AttributeDefinition) attDep.getSourceValue();
								log.info("Unresolved Field: {}:{}:{}", srcConn.getName(), srcEntity.getApiName(), srcAttr.getApiName());
							});
						}
					});
				}
			});
		});

	}
}
