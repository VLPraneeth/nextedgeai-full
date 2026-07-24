package com.syncari.api.rest.controllers;

import static com.syncari.core.security.Permissions.READ_REFERENCE_DATA;
import static com.syncari.core.security.Permissions.WRITE_REFERENCE_DATA;
import static com.syncari.core.utils.ValidationUtils.validateCondition;
import static com.syncari.utils.I18n.i18n;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.syncari.api.core.util.ObjectTransformer;
import com.syncari.api.rest.controllers.data.Dependency;
import com.syncari.api.rest.controllers.data.ReferenceDataMeta;
import com.syncari.core.SyncariContext;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.misc.ComponentType;
import com.syncari.core.model.misc.ReferenceData;
import com.syncari.core.model.misc.ReferenceDataSource;
import com.syncari.core.model.misc.ReferenceDataSourceType;
import com.syncari.core.model.util.Scope;
import com.syncari.core.service.ComponentDependencyService;
import com.syncari.core.service.MappingGraphService;
import com.syncari.core.service.ReferenceDataService;
import com.syncari.core.service.SchemaService;
import com.syncari.utils.file.FileUtil;

import lombok.extern.slf4j.Slf4j;
import net.minidev.json.JSONObject;

@Slf4j
@RestController
@RequestMapping("/api/v1/referenceData")
public class ReferenceDataController {
    private static final String TEXT_CSV = "text/csv";
    private static final String APP_CSV = "application/csv";
    private static final String APP_OCTET_STREAM = "application/octet-stream"; // Host unknown content type. Windows with no associated application.
    private static final String APP_EXCEL_STREAM = "application/vnd.ms-excel"; // Host with excel
    private static final List<String> supportedContentType = List.of(TEXT_CSV, APP_CSV, APP_OCTET_STREAM, APP_EXCEL_STREAM);

	private static final String CSV = ".csv";
	@Autowired
    ReferenceDataService service;
    @Autowired
    ObjectTransformer transformer;
    @Autowired
    ComponentDependencyService dependencyService;
    @Autowired
    MappingGraphService graphService;
    @Autowired
    SchemaService schemaService;
    @Autowired
    FileUtil fileUtil;

    @Secured(READ_REFERENCE_DATA)
    @RequestMapping(method = RequestMethod.GET)
    public List<ReferenceDataMeta> list() {
        // TODO change the pageNumber and pass from request @RequestParam int pageNumber
        List<ReferenceDataMeta> refDataMeta = transformer.toRefDataMeta(service.listMeta(0));
        for (ReferenceDataMeta referenceDataMeta : refDataMeta) {
            List<String> pipelineIds = dependencyService.findDependencies(referenceDataMeta.getId(),
                    ComponentType.referencedata, ComponentType.pipeline);
            Iterable<MappingGraph> pipelines = graphService.retrieve(pipelineIds);
            pipelines.forEach(p -> {
                if(p.isArchived()) return;
                String path = "";
                if(p.getScope() == Scope.ATTRIBUTE) {
                    Optional<AttributeDefinition> activeAttribute = schemaService.getActiveAttribute(p.getTargetId());
                    if(activeAttribute.isPresent()) {
                        String entityId = activeAttribute.get().getEntityId();
                        path = "/sync-studio/entity/" + entityId + "/field/" + p.getTargetId() + "/pipeline";
                    } else {
                        return;
                    }
                } else {
                    path = "/sync-studio/entity/" + p.getTargetId() + "/pipeline";
                }
                referenceDataMeta.getUsedInPipelines().add(new Dependency(p.getName(), p.getId(), path));
            });
        }
        return refDataMeta;
    }

    @Secured(READ_REFERENCE_DATA)
    @RequestMapping(method = RequestMethod.GET, value = "/preview/{refMetaId}")
    public ReferenceData preview(
        @PathVariable String refMetaId,
        @RequestParam(name="numberOfRows", required = false, defaultValue = "25") int numberOfRows
    ) {
        return service.previewData(refMetaId, numberOfRows);
    }
    
    @Secured(WRITE_REFERENCE_DATA)
    @GetMapping("/download/{refMetaId}")
    public ResponseEntity<Resource> download(@PathVariable String refMetaId) throws IOException {
        com.syncari.core.model.ReferenceDataMeta referenceData = service.getReferenceData(refMetaId);
        InputStreamResource resource = new InputStreamResource(service.getFile(refMetaId));
        return ResponseEntity.ok()
                                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + referenceData.getSource().getLocation() + "\"")
                                .body(resource);
    }

    @Secured(WRITE_REFERENCE_DATA)
    @RequestMapping(method = RequestMethod.DELETE, value = "/{refMetaId}")
    public void delete(@PathVariable String refMetaId) {
        if(service.getReferenceData(refMetaId).getSource().getType()==ReferenceDataSourceType.syncari)
            throw new RuntimeException(i18n("cannot_delete_ref_data"));
        service.deleteMeta(refMetaId);
    }

    @Secured(WRITE_REFERENCE_DATA)
    @RequestMapping(method = RequestMethod.POST)
    public ResponseEntity create(@RequestParam("name") String name, @RequestParam("type") String type,
            @RequestParam("secretKey") String secretKey, @RequestParam("accessKey") String accessKey,
            @RequestParam("fileName") String fileName,
            @RequestParam(name = "file", required = false) MultipartFile file) {
        com.syncari.core.model.ReferenceDataMeta dataset = new com.syncari.core.model.ReferenceDataMeta();
        dataset.setName(name);
        try {
            validateCondition(StringUtils.isBlank(name), i18n("invalid_ref_datatset_name"));
        	validateFile(file);
            String fixedFileName = fileUtil.sanitizeFileName(file.getOriginalFilename());
            ReferenceDataSourceType dataSourceType = ReferenceDataSourceType.valueOf(type);
            switch (dataSourceType) {
            case upload:
                String fullyQualifiedFileName = SyncariContext.getSyncariId()+"/"+fixedFileName;
                dataset.setSource(new ReferenceDataSource(dataSourceType, fullyQualifiedFileName));
                return ResponseEntity.ok(
                    transformer.toRef(service.createMeta(dataset, file.getInputStream(), file.getInputStream(), true))
                );
            case syncari:
                if(!SyncariContext.getUser().isSuperAdmin())
                    throw new RuntimeException(i18n("unauthorized_access"));
                String qualifiedName = "syncaridb"+"/"+fixedFileName;
                dataset.setSource(new ReferenceDataSource(dataSourceType, qualifiedName));
                return ResponseEntity.ok(
                        transformer.toRef(service.createMeta(dataset, file.getInputStream(), file.getInputStream(), true))
                );
            default:
                throw new RuntimeException("Unknown type");
            }
        } catch (Exception e) {
            log.error(e.getMessage());
            JSONObject errResp = new JSONObject();
            errResp.put("message", e.getMessage());
            return new ResponseEntity(errResp, HttpStatus.BAD_REQUEST);
        }
    }

	@Secured(WRITE_REFERENCE_DATA)
    @RequestMapping(method = RequestMethod.PUT)
    public ResponseEntity update(@RequestParam("name") String name, @RequestParam("type") String type,
            @RequestParam("secretKey") String secretKey, @RequestParam("accessKey") String accessKey,
            @RequestParam("fileName") String fileName, @RequestParam("metaId") String refMetaId,
            @RequestParam(name = "file", required = false) MultipartFile file) {
        try {
            com.syncari.core.model.ReferenceDataMeta referenceData =  service.getReferenceData(refMetaId);
            String existingReferenceDataType = referenceData.getSource().getType().name();
            ReferenceDataSourceType existingReferenceDataSourceType =
                    ReferenceDataSourceType.valueOf(existingReferenceDataType);
            ReferenceDataSourceType dataSourceType = ReferenceDataSourceType.valueOf(type);
            if (!existingReferenceDataSourceType.equals(dataSourceType)) {
                throw new RuntimeException("Datasource type mismatch ");
            }
            validateFile(file);
            String fixedFileName = fileUtil.sanitizeFileName(file.getOriginalFilename());
            switch (dataSourceType) {
            case upload:
                String fullyQualifiedFileName = SyncariContext.getSyncariId()+"/"+fixedFileName;
                referenceData.setSource(new ReferenceDataSource(dataSourceType, fullyQualifiedFileName));
                return ResponseEntity.ok(
                    transformer.toRef(service.updateMeta(referenceData, file.getInputStream(), file.getInputStream()))
                );
            case syncari:
                if(!SyncariContext.getUser().isSuperAdmin())
                    throw new RuntimeException(i18n("unauthorized_access"));
                String qualifiedName = "syncaridb"+"/"+fixedFileName;
                referenceData.setSource(new ReferenceDataSource(dataSourceType, qualifiedName));
                return ResponseEntity.ok(
                        transformer.toRef(service.updateMeta(referenceData, file.getInputStream(), file.getInputStream()))
                );
            default:
                throw new RuntimeException("Unknown type");
            }
        } catch (Exception e) {
            log.error(e.getMessage());
            JSONObject errResp = new JSONObject();
            errResp.put("message", e.getMessage());
            return new ResponseEntity(errResp, HttpStatus.BAD_REQUEST);
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null)
            throw new SyncariValidationException(i18n("file_required"));
        if(!file.getOriginalFilename().endsWith(CSV)) {
            throw new SyncariValidationException(i18n("unsupported_file_ext"));
        }
        if (!supportedContentType.stream().anyMatch((contentType -> contentType.equalsIgnoreCase(file.getContentType())))) {
            throw new SyncariValidationException(String.format(i18n("unsupported_content_type"), file.getContentType()));
        }
    }
}
