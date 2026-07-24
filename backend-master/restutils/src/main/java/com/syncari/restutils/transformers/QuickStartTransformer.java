package com.syncari.restutils.transformers;

import com.syncari.api.rest.controllers.data.quickstart.v2.*;
import com.syncari.core.model.Tag;
import com.syncari.core.model.User;
import com.syncari.core.model.misc.Sharable;
import com.syncari.core.model.misc.Taggable;
import com.syncari.core.model.misc.sharable.SharableCoreAttributeNodeConfig;
import com.syncari.core.model.misc.sharable.SharableCoreEntityNodeConfig;
import com.syncari.core.model.misc.sharable.SharableGraph;
import com.syncari.core.model.util.SharableGraphTransformer;
import com.syncari.core.quickstart.QuickStartFactory;
import com.syncari.core.quickstart.QuickStartMetadata;
import com.syncari.core.quickstart.dedupe.DedupeQuickStartConfig;
import com.syncari.core.quickstart.QuickStartConfig;
import com.syncari.core.quickstart.QuickStartConstants;
import com.syncari.core.model.QuickStartRun;
import com.syncari.core.quickstart.dedupe.DedupeQuickStartService;
import com.syncari.core.quickstart.unify.UnifyQuickStartConfig;
import com.syncari.core.quickstart.v2.*;
import com.syncari.core.repositories.syncari.SharedItemRepo;
import com.syncari.core.service.ConnectorService;
import com.syncari.core.service.MappingGraphService;
import com.syncari.core.service.UserService;
import com.syncari.utils.I18n;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class QuickStartTransformer {

    @Autowired
    UserService userService;

    @Autowired
    QuickStartFactory factory;

    @Autowired
    DedupeQuickStartService dedupeQuickStartService;

    @Autowired
    ConnectorService connService;

    @Autowired
    MappingGraphService mappingGraphService;

    @Autowired
    QuickStartV2Service quickStartV2Service;

    @Autowired
    SharedItemRepo sharedItemRepo;

    @Autowired
    SharableGraphTransformer sharableGraphTransformer;

    public AsyncProcessingConfirmationDTO toQuickStartRunResponse(QuickStartRun run){
        AsyncProcessingConfirmationDTO response = new AsyncProcessingConfirmationDTO();

        if(QuickStartRun.Status.ERROR.equals(run.getStatus())){
            // failure
            response.setType(AsyncProcessingConfirmationDTO.Type.ERROR);
            response.setMessage(I18n.i18n("quick_start_run_failure_header"));
            response.setDescription(run.getErrorMsg());

        }else{
            response.setType(AsyncProcessingConfirmationDTO.Type.INFO);
            response.setMessage(I18n.i18n("quick_start_run_success_header"));
            response.setDescription(I18n.i18n("quick_start_run_success_description"));
        }

        return response;
    }

    public QuickStartConfig toQuickStartConfig(String quickStartName, Map<String, Object> inputs){

        switch (quickStartName){
            case QuickStartConstants.UNIFY:
                UnifyQuickStartConfig unifyConfig = new UnifyQuickStartConfig();
                unifyConfig.setName(quickStartName);
                unifyConfig.setSyncariEntityId(inputs.get("syncariEntity").toString());
                List<Map> synapseUnificationSettings = (List<Map>) inputs.get("synapseUnificationSetting");
                synapseUnificationSettings.forEach(setting -> {
                    String synapseId = setting.get("synapse").toString();
                    String entityId = setting.get("entity").toString();
                    List<String> attributeIds = (List<String>) setting.get("unificationField");
                    unifyConfig.addSynapseUnificationConfig(synapseId, entityId, attributeIds);
                });
                return unifyConfig;

            case QuickStartConstants.DEDUPE: // TODO
                DedupeQuickStartConfig dedupConfig = new DedupeQuickStartConfig();
                dedupConfig.setConfig(inputs);
                return dedupConfig;

            case QuickStartConstants.DFI: // TODO
                return null;

            default:
                throw new RuntimeException(String.format("Unknown QuickStart with name %s", quickStartName));
        }
    }

    public QuickStartHistory toQuickStartHistory(String qsName, List<QuickStartRun> qsRuns){
        QuickStartHistory history = new QuickStartHistory();
        history.setDisplayName(I18n.i18n(qsName));
        history.setName(qsName);
        var runs = qsRuns.stream().map(run -> toQuickStartRunDTO(run)).collect(Collectors.toList());
        history.setRuns(runs);
        return history;

    }

    public QuickStartRunDTO toQuickStartRunDTO(QuickStartRun qsRun){

        QuickStartRunDTO runDto = new QuickStartRunDTO();
        runDto.setDetails(qsRun.getRunDetail());
        runDto.setExecutedAt(qsRun.getExecutedAt());
        Optional<User> user = userService.findUserById(qsRun.getExecutedBy());
        if(user.isPresent()){
            runDto.setExecutedByName(user.get().getFirstName() + user.get().getLastName());
        } else {
            runDto.setExecutedByName("Unknown");
        }
        runDto.setExecutedBy(qsRun.getExecutedBy());
        runDto.setInputs(List.of()); // TODO: populate from config
        runDto.setQsType(qsRun.getQsType());
        runDto.setStatus(QuickStartRunDTO.Status.valueOf(qsRun.getStatus().name()));

        return runDto;
    }

    public List<QuickStartMetadataDTO> toQuickStartMetadataDTOList(List<QuickStartMetadata> metadataList){
        return metadataList.stream().map(metadata -> toQuickStartMetadataDTO(metadata)).collect(Collectors.toList());
    }

    public QuickStartMetadataDTO toQuickStartMetadataDTO(QuickStartMetadata metadata) {
        QuickStartMetadataDTO qsMetadata = new QuickStartMetadataDTO()
                .setName(metadata.getName())
                .setDisplayName(metadata.getDisplayName())
                .setDescription(metadata.getDescription())
                .setHelpSummary(metadata.getHelpSummary())
                .setIconPath(metadata.getIconPath())
                .setHelpLink(metadata.getHelpLink())
                .setConfiguration(metadata.getConfiguration())
                .setRenderer(metadata.getRenderer())
                .setRequirementsText(metadata.getRequirementsText());
        return qsMetadata;
    }

    public QuickStartDTO toQuickStartDTO(QuickStart quickStart) {
        return toQuickStartDTO(quickStart, false);
    }
    public QuickStartDTO toQuickStartDTO(QuickStart quickStart, Boolean skipConfig) {
        return toQuickStartDTO(quickStart, null, skipConfig);
    }

    public QuickStartDTO toQuickStartDTO(QuickStart quickStart, List<String> shareWithInstances) {
        return toQuickStartDTO(quickStart, shareWithInstances, false);
    }
    public QuickStartDTO toQuickStartDTO(QuickStart quickStart, List<String> shareWithInstances, Boolean skipConfig) {
        var tagsDTO = new ArrayList<String>();
        var tags = quickStart.getTags();
        if (tags != null) {
            quickStart.getTags().forEach(tag -> {
                tagsDTO.add(tag.getName());
            });
        }
        var qsDTO = new QuickStartDTO()
                .setDisplayName(quickStart.getDisplayName())
                .setDescription(quickStart.getDescription())
                .setTags(tagsDTO)
                .setId(quickStart.getId())
                .setStatus(quickStart.getDraftStatus().name())
                .setIconPath(String.format(QSAuthoringSeed.ICON_PATH_URL, quickStart.getId()))
                .setPostInstallationInstruction(quickStart.getPostInstallationInstruction());
        if (!skipConfig) {
            var entityPipelines = new ArrayList<QSEntityPipelineDTO>();
            var qsConfigs = quickStart.getConfiguration();
            var pipelines = new QSPipelineConfigDTO();
            qsConfigs.forEach(qsConfig -> {
                var config = (PipelineQSConfig)qsConfig;
                pipelines.setFieldsOnly(config.isFieldsOnly());
                config.getPipelines().forEach(p -> {
                    if (!config.isFieldsOnly()) {
                        var sharableNode = p.getEntityGraph().getCoreNode();
                        var fgDTOs = new ArrayList<QSFieldPipelineDTO>();
                        p.getFieldGraphs().forEach(fg -> {
                            var fgCoreNode = fg.getCoreNode();
                            var attribDef = ((SharableCoreAttributeNodeConfig)fgCoreNode.getConfiguration()).getAttributeDefinition();
                            fgDTOs.add(new QSFieldPipelineDTO()
                                    .setApiName(fgCoreNode.getApiName())
                                    .setId(attribDef.getId())
                                    .setDisplayName(fgCoreNode.getName())
                                    .setDatatype(attribDef.getDataType().getName()));
                        });
                        entityPipelines.add(new QSEntityPipelineDTO()
                                .setDisplayName(sharableNode.getName())
                                .setId(((SharableCoreEntityNodeConfig)sharableNode.getConfiguration()).getEntityDefinition().getId())
                                .setApiName(sharableNode.getApiName())
                                .setFields(fgDTOs));
                    }
                });
            });
            pipelines.setEntities(entityPipelines);
            qsDTO.setPipelines(pipelines);
        }
        var tagsInput = quickStart.getTags();
        var stringTags = new ArrayList<String>();
        if (tagsInput != null) {
            tagsInput.forEach(tag -> {
                stringTags.add(tag.getName());
            });
        }
        qsDTO.setTags(stringTags);
        qsDTO.setRequiredSynapses(quickStart.getRequiredSynapses());

        // Populate the sharing instances
        sharedItemRepo.findSharedItemBySourceIdAndItemType(qsDTO.getId(), Sharable.QUICK_START).ifPresent((sharedItem -> {
            ArrayList<String> instanceIds = new ArrayList<>(sharedItem.getSharingInstances().keySet());
            qsDTO.setShareWithInstances(instanceIds);
            var publishedToMarketplace = sharedItem.isPublishedToMarketplace();
            qsDTO.setPublishToQuickStartLibrary( publishedToMarketplace ? QSAuthoringSeed.PulishOption.publish.name() : QSAuthoringSeed.PulishOption.dontPublish.name());
        }));

        // Note: We only care for 2 status for now, none or in progress
        // TODO: This is very slow... Need to do performance improvement on this ...
        var qsInstall = quickStartV2Service.findQuickStartInstallByQuickStartId(quickStart.getId(), QuickStartInstall.Status.INPROGRESS);
        qsDTO.setInstallStatus(qsInstall.isPresent() ? QuickStartInstall.Status.INPROGRESS.name() : null);

        return qsDTO;
    }

    public List<QuickStartDTO> toQuickStartDTOs(List<QuickStart> quickStarts) {
        return toQuickStartDTOs(quickStarts, false);
    }
    public List<QuickStartDTO> toQuickStartDTOs(List<QuickStart> quickStarts, Boolean skipConfig) {
        var quickStartDTOs = new ArrayList<QuickStartDTO>();
        quickStarts.forEach((quickStart) -> {
            quickStartDTOs.add(toQuickStartDTO(quickStart, skipConfig));
        });
        return quickStartDTOs;
    }

    public QuickStart toQuickStart(QuickStartDTO quickStartDTO) {
        var quickStart = new QuickStart();
        var pipelineQSConfig = new PipelineQSConfig();
        var pipelinesDTO = quickStartDTO.getPipelines();
        pipelineQSConfig.setFieldsOnly(pipelinesDTO.isFieldsOnly());

        // Transform to Pipeline quick start configuration
        var entities  = pipelinesDTO.getEntities();
        var pipelines = new ArrayList<Pipeline>();
        entities.forEach(entity -> {
            var fields = entity.getFields();
            var pipeline = new Pipeline();
            var fieldGraphs = new ArrayList<SharableGraph>();
            fields.stream().forEach(field -> {
                mappingGraphService.retrieveAttributeGraph(field.getId()).ifPresent( g -> fieldGraphs.add(sharableGraphTransformer.toSharableGraph(g)));
            });
            pipeline.setFieldGraphs(fieldGraphs);
            mappingGraphService.retrieveEntityGraph(entity.getId()).ifPresent(g -> pipeline.setEntityGraph(sharableGraphTransformer.toSharableGraph(g)));
            pipelines.add(pipeline);
        });
        pipelineQSConfig.setPipelines(pipelines);

        var tags = new ArrayList<Tag>();
        if (quickStartDTO.getTags() != null) {
            quickStartDTO.getTags().forEach(tag -> {
                tags.add(new Tag(tag, true, Taggable.quickStart, null));
            });
        }

        quickStart.setId(quickStartDTO.getId());
        quickStart.setDisplayName(quickStartDTO.getDisplayName())
                .setTags(tags)
                .setDescription(quickStartDTO.getDescription())
                .setPostInstallationInstruction(quickStartDTO.getPostInstallationInstruction())
                .setConfiguration(List.of(pipelineQSConfig));

        quickStart.setRequiredSynapses(quickStartDTO.getRequiredSynapses());

        return quickStart;
    }

    public QuickStartRestDTO toQuickStartRestDTO(QuickStart quickStart) {
        var tagsDTO = new ArrayList<String>();
        var tags = quickStart.getTags();
        if (tags != null) {
            quickStart.getTags().forEach(tag -> {
                tagsDTO.add(tag.getName());
            });
        }
        var qsDTO = new QuickStartRestDTO()
                .setDisplayName(quickStart.getDisplayName())
                .setDescription(quickStart.getDescription())
                .setTags(tagsDTO)
                .setId(quickStart.getId())
                .setStatus(quickStart.getDraftStatus().name())
                .setIconPath(String.format(QSAuthoringSeed.ICON_PATH_URL, quickStart.getId()))
                .setPostInstallationInstruction(quickStart.getPostInstallationInstruction());

        var tagsInput = quickStart.getTags();
        var stringTags = new ArrayList<String>();
        if (tagsInput != null) {
            tagsInput.forEach(tag -> {
                stringTags.add(tag.getName());
            });
        }
        qsDTO.setTags(stringTags);
        qsDTO.setRequiredSynapses(quickStart.getRequiredSynapses());
        return qsDTO;
    }

    public List<QuickStartRestDTO> toQuickStartRestDTOs(List<QuickStart> quickStarts) {
        var quickStartRestDTOs = new ArrayList<QuickStartRestDTO>();
        quickStarts.forEach((quickStart) -> {
            quickStartRestDTOs.add(toQuickStartRestDTO(quickStart));
        });
        return quickStartRestDTOs;
    }
}
