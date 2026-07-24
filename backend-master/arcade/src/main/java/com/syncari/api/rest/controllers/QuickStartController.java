package com.syncari.api.rest.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.restutils.utils.ImageUtil;
import com.syncari.restutils.transformers.QuickStartTransformer;
import com.syncari.api.rest.controllers.data.quickstart.v2.AsyncProcessingConfirmationDTO;
import com.syncari.api.rest.controllers.data.quickstart.v2.QuickStartHistory;
import com.syncari.api.rest.controllers.data.quickstart.v2.QuickStartMetadataDTO;
import com.syncari.api.rest.controllers.data.quickstart.v2.QSPipelineConfigDTO;
import com.syncari.api.rest.controllers.data.quickstart.v2.QuickStartDTO;
import com.syncari.core.GlobalConstants;
import com.syncari.core.SyncariContext;
import com.syncari.core.draft.DraftStatus;
import com.syncari.core.model.Instance;
import com.syncari.core.model.QuickStartRun;
import com.syncari.core.quickstart.QuickStartRunService;
import com.syncari.core.quickstart.v2.QSAuthoringConfig;
import com.syncari.core.quickstart.v2.QSAuthoringSeed;
import com.syncari.core.quickstart.v2.QuickStartV2Service;
import com.syncari.core.quickstart.dedupe.DedupeQuickStartService;
import com.syncari.core.quickstart.unify.UnifyQuickStartService;
import com.syncari.core.schema.PipelineStatus;
import com.syncari.core.service.UserService;
import com.syncari.utils.KeyValue;
import com.syncari.utils.file.FileUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.syncari.core.security.Permissions.*;

@Slf4j
@RestController
@RequestMapping(value = "/api/v1/quickstart")
public class QuickStartController {

    @Autowired
    QuickStartRunService qsRunService;

    @Autowired
    QuickStartTransformer qsTransformer;

    @Autowired
    UnifyQuickStartService unifyQsService;

    @Autowired
    DedupeQuickStartService dedupeQsService;

    @Autowired
    QuickStartV2Service qsV2Service;

    @Autowired
    ObjectMapper mapper;

    @Autowired
    UserService userService;

    @Autowired
    ImageUtil imageUtil;

    @Autowired
    FileUtil fileUtil;

    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.POST)
    public QuickStartDTO saveQuickStart(@RequestParam(value = "id", required = false) String id,
                                                  @RequestParam("displayName") String displayName,
                                                  @RequestParam(value = "description", required = false) String description,
                                                  @RequestParam(value = "postInstallationInstruction", required = false) String postInstallationInstruction,
                                                  @RequestParam("pipelines") String pipelines,
                                                  @RequestParam(name = "icon", required = false) MultipartFile file,
                                                  @RequestParam(value = "tags", required = false) String tags,
                                                  @RequestParam(value = "shareWithInstances", required = false) String shareWithInstances,
                                                  @RequestParam(value = "shareWithOrg", required = false, defaultValue = "false") boolean shareWithOrg,
                                                  @RequestParam("publishToQuickStartLibrary") String publishToQuickStartLibrary
                                                  ) throws IOException {

        QSPipelineConfigDTO qsPipelineConfigDTO = mapper.readValue(pipelines, QSPipelineConfigDTO.class);
        var quickStartDTO = new QuickStartDTO()
                .setId(id)
                .setDisplayName(displayName)
                .setDescription(description != null ? description : "")
                .setPostInstallationInstruction(postInstallationInstruction != null ? postInstallationInstruction : "" )
                .setPipelines(qsPipelineConfigDTO)
                .setTags(tags != null ? Arrays.asList(mapper.readValue(tags, String[].class)) : null)
                .setShareWithInstances(shareWithInstances != null ? Arrays.asList(mapper.readValue(shareWithInstances, String[].class)) : null)
                .setShareWithOrg(shareWithOrg)
                .setPublishToQuickStartLibrary(publishToQuickStartLibrary);

        imageUtil.validateFile(file);
        return qsTransformer.toQuickStartDTO(qsV2Service.saveQuickStartDraft(
                        qsTransformer.toQuickStart(quickStartDTO),
                        quickStartDTO.getShareWithInstances(),
                        quickStartDTO.getPublishToQuickStartLibrary(),
                        quickStartDTO.isShareWithOrg(),
                        file != null ? file.getInputStream() : null,
                        file != null ? fileUtil.sanitizeFileName(file.getOriginalFilename()) : null
                )
        );
    }

    @RequestMapping(method = RequestMethod.GET, value = "/icon/{quickStartId}/{status}")
    public ResponseEntity<StreamingResponseBody> getQuickStartIcon(@PathVariable("quickStartId") String quickStartId, @PathVariable("status") String status) {
        HttpHeaders headers = new HttpHeaders();
        String iconPath = null;
        if (status.equalsIgnoreCase(DraftStatus.NEW.name()) ||
                status.equalsIgnoreCase(PipelineStatus.PUBLISHED_WITH_DRAFT.name())) {
            var draftQuickStart = qsV2Service.findDraft(quickStartId);
            if (draftQuickStart.isPresent()) {
                iconPath = draftQuickStart.get().getIconPath();
            }
        } else if (status.equalsIgnoreCase(DraftStatus.APPROVED.name())) {
            var approvedQuickStart = qsV2Service.findApproved(quickStartId);
            if (approvedQuickStart.isPresent()) {
                iconPath = approvedQuickStart.get().getIconPath();
            } else {
                // Check if its a marketplace quick start
                var sharedQs = qsV2Service.getMarketplaceQuickStartById(quickStartId);
                if (sharedQs != null && sharedQs.getIconPath() != null) {
                    iconPath = sharedQs.getIconPath();
                }
            }
        }
        if (iconPath == null) {
            iconPath = QuickStartV2Service.QUICK_START_DEFAULT_ICON;
        }

        var extensionParts = iconPath.split("\\.");
        var extension = extensionParts.length > 0 ? extensionParts[extensionParts.length - 1] : "png";
        var mediaType = GlobalConstants.PHOTO_MEDIA_TYPE_MAP.getOrDefault(extension.toLowerCase(), MediaType.IMAGE_PNG);
        headers.setContentType(mediaType);
        var iconStream = qsV2Service.getQuickStartIcon(iconPath);
        StreamingResponseBody stream = outputStream -> iconStream.transferTo(outputStream);

        return new ResponseEntity<>(stream, headers, HttpStatus.OK);
    }

    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.POST, value = "/legacy/execute/{quickStartName}")
    public AsyncProcessingConfirmationDTO execute(@PathVariable("quickStartName") String quickStartName, @RequestBody Map<String, Object> inputs) {
        QuickStartRun run = qsRunService.initiate(qsTransformer.toQuickStartConfig(quickStartName, inputs));
        return qsTransformer.toQuickStartRunResponse(run);
    }

    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.POST, value = "/legacy/{quickStartName}/dynamicSteps/{stepNumber}")
    public KeyValue getDynamicStepsUpdate(@PathVariable("quickStartName") String quickStartName, @PathVariable("stepNumber") String stepNumber, @RequestBody KeyValue inputs) {
        // TODO: QuickStartRunService does not seem to be the right name but adding it there for lack for QuickStartService
        return qsRunService.getDynamicStepsUpdate(qsTransformer.toQuickStartConfig(quickStartName, inputs), Integer.parseInt(stepNumber), inputs);
    }

    @Secured(READ_STUDIO)
    @RequestMapping(method = RequestMethod.GET, value = "/legacy/list")
    public List<QuickStartMetadataDTO> list() {
        // TODO: Make it dynamic, i.e. all implementing classes of QuickStartService
        return qsTransformer.toQuickStartMetadataDTOList(List.of(unifyQsService.getMetadata(), dedupeQsService.getMetadata()));
    }

    @Secured(READ_STUDIO)
    @RequestMapping(method = RequestMethod.GET, value = "/legacy/history/{quickStartName}")
    public QuickStartHistory history(@PathVariable("quickStartName") String quickStartName) {
        List<QuickStartRun> runs = qsRunService.getHistoryByType(quickStartName);
        return qsTransformer.toQuickStartHistory(quickStartName, runs);
    }

    @Secured(READ_STUDIO)
    @RequestMapping(method = RequestMethod.GET, value = "/author/config")
    public QSAuthoringConfig getAuthoringConfig() {
        // TODO: DTO
        return qsV2Service.getConfig();
    }

    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.POST, value = "/author/dynamicStep/{stepNumber}")
    public QSAuthoringConfig getDynamicStepsUpdate(@PathVariable("stepNumber") String stepNumber, @RequestBody KeyValue inputs) {
        // TODO: QuickStartRunService does not seem to be the right name but adding it there for lack for QuickStartService
        return qsV2Service.getDynamicStepUpdate(Integer.parseInt(stepNumber), inputs);
    }

    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.GET, value = "/author/list")
    public List<KeyValue> getDynamicStepsUpdate() {
        return qsV2Service.list();
    }

    @Secured(QUICKSTART_SHARE)
    @RequestMapping(method = RequestMethod.GET, value = "/share/instances")
    public List<Instance> listInstancesToShare() {
        return userService.listInstancesWithPermission(SyncariContext.getUser(), QUICKSTART_SHARE).stream().filter(instance ->
                !SyncariContext.getInstance().getName().equals(instance.getName())).collect(Collectors.toList());
    }

    @Secured(READ_STUDIO)
    @RequestMapping(method = RequestMethod.GET, value = "/author/{quickStartId}/draft")
    public QuickStartDTO getQuickStartDraft(@PathVariable String quickStartId) {
        return qsTransformer.toQuickStartDTO(qsV2Service.findDraft(quickStartId).get());
    }

    @Secured(READ_STUDIO)
    @RequestMapping(method = RequestMethod.GET, value = "/author/{quickStartId}/approved")
    public QuickStartDTO getQuickStartApproved(@PathVariable String quickStartId) {
        return qsTransformer.toQuickStartDTO(qsV2Service.findApproved(quickStartId).get());
    }

    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.DELETE, value = "/author/{quickStartId}")
    public void deleteQuickStartDraft(@PathVariable String quickStartId) {
        qsV2Service.deleteQuickStart(quickStartId);
    }

    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.POST, value = "/author/createDraft/{quickStartId}")
    public QuickStartDTO createQuickStartDraft(@PathVariable String quickStartId) {
        return qsTransformer.toQuickStartDTO(qsV2Service.createDraft(quickStartId));
    }

    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.POST, value = "/author/discardDraft/{quickStartId}")
    public void discardQuickStartDraft(@PathVariable String quickStartId) {
        qsV2Service.discardDraft(quickStartId);
    }

    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.POST, value = "/author/approveDraft/{quickStartId}")
    public void approveQuickStartDraft(@PathVariable String quickStartId) {
        qsV2Service.approveDraft(quickStartId);
    }

    @Secured(READ_STUDIO)
    @RequestMapping(method = RequestMethod.GET, value = "/install/marketplace")
    public List<QuickStartDTO> getMarketPlaceQuickStarts() {
        return qsTransformer.toQuickStartDTOs(qsV2Service.getMarketplaceQuickStart(), true);
    }

    @Secured(READ_STUDIO)
    @RequestMapping(method = RequestMethod.GET, value = "/install/{quickStartId}")
    public KeyValue getMarketPlaceQuickStart(@PathVariable String quickStartId) {
        return qsV2Service.getMarketplaceQuickStartConfig(quickStartId);
    }

    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.POST, value = "/install/{quickStartId}/cancel")
    public void cancelQuickStartInstall(@PathVariable String quickStartId) {
        qsV2Service.cancelQuickStartInstall(quickStartId);
    }

    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.POST, value = "/install/dynamicStep/{stepNumber}")
    public QSAuthoringConfig getInstallDynamicStepsUpdate(@PathVariable("stepNumber") String stepNumber, @RequestBody KeyValue inputs) {
        return qsV2Service.getInstallDynamicStepUpdate(Integer.parseInt(stepNumber), inputs);
    }

    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.POST, value = "/install/{quickStartId}")
    public void installQuickStart(@PathVariable("quickStartId") String quickStartId) {
        qsV2Service.installQuickStart(quickStartId);
    }

    @Secured(READ_STUDIO)
    @RequestMapping(method = RequestMethod.GET, value = "/install/shared")
    public List<QuickStartDTO> getSharedQuickStarts() {
        return qsTransformer.toQuickStartDTOs(qsV2Service.getSharedQuickStart());
    }

    @Secured(READ_STUDIO)
    @RequestMapping(method = RequestMethod.GET, value = "/author/instances")
    public List<KeyValue> getAuthorAvailableInstances() {
        return qsV2Service.getSharedInstances();
    }

    @Secured(WRITE_STUDIO)
    @RequestMapping(method = RequestMethod.POST, value = "/author/{quickStartId}/publish")
    public QuickStartDTO publishQuickStart(@PathVariable("quickStartId") String quickStartId, @RequestBody KeyValue publishRequest ) {
        return qsTransformer.toQuickStartDTO(qsV2Service.publishQuickStart(
                quickStartId, publishRequest.get("instances"),
                publishRequest.get("publishToLibrary").toString().equalsIgnoreCase(QSAuthoringSeed.PulishOption.publish.name()),
                publishRequest.get("shareToOrg"))
        );
    }
}
