package com.syncari.karibu.rest.util;

import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.MappingGraph;
import com.syncari.core.model.ReferenceDataMeta;
import com.syncari.core.model.misc.ComponentType;
import com.syncari.core.model.util.Scope;
import com.syncari.core.service.ComponentDependencyService;
import com.syncari.core.service.MappingGraphService;
import com.syncari.core.service.SchemaService;
import com.syncari.karibu.rest.request.ReferenceDataRequest;
import com.syncari.karibu.rest.response.ReferenceDataResponse;
import com.syncari.utils.DateUtil;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import static com.syncari.utils.I18n.i18n;

@Component
public class ReferenceDataUtils {

    @Autowired
    ComponentDependencyService dependencyService;
    @Autowired
    SchemaService schemaService;
    @Autowired
    MappingGraphService graphService;

    private static final String TEXT_CSV = "text/csv";
    private static final String APP_CSV = "application/csv";
    private static final String APP_OCTET_STREAM = "application/octet-stream"; // Host unknown content type. Windows with no associated application.
    private static final String APP_EXCEL_STREAM = "application/vnd.ms-excel"; // Host with excel
    private static final String CSV = ".csv";

    private static final List<String> supportedContentType = List.of(TEXT_CSV, APP_CSV, APP_OCTET_STREAM, APP_EXCEL_STREAM);

    public List<ReferenceDataResponse> getReferenceDataResponses (List<ReferenceDataMeta> referenceDataMetaList) {
        List<ReferenceDataResponse> referenceDataResponseList = new ArrayList<>();
        for(ReferenceDataMeta rdm : referenceDataMetaList) {
            ReferenceDataResponse referenceDataResponse = getReferenceDataResponse(rdm);
            referenceDataResponseList.add(referenceDataResponse);
        }
        return referenceDataResponseList;
    }

    public ReferenceDataResponse getReferenceDataResponse (ReferenceDataMeta referenceDataMeta) {
        ReferenceDataResponse referenceDataResponse = new ReferenceDataResponse();
        referenceDataResponse.setId(referenceDataMeta.getId());
        referenceDataResponse.setName(referenceDataMeta.getName());
        referenceDataResponse.setStatus(referenceDataMeta.getStatus().name());
        referenceDataResponse.setImportDetails(referenceDataMeta.getImportDetails());
        referenceDataResponse.setLastImported(new DateUtil()
                .format(referenceDataMeta.getUpdatedAt() == null ? referenceDataMeta.getCreatedAt() : referenceDataMeta.getUpdatedAt()));
        referenceDataResponse.setTotalRecords(referenceDataMeta.getTotalRecords());
        referenceDataResponse.setUsedInFieldPipelines(getUsedInPipelines(referenceDataMeta));
        referenceDataResponse.setHeaderColumns(List.copyOf(referenceDataMeta.getFields().keySet()));
        referenceDataResponse.setCreatedAt(referenceDataMeta.getCreatedAt());
        referenceDataResponse.setCreatedBy(referenceDataMeta.getCreatedBy());
        referenceDataResponse.setUpdatedAt(referenceDataMeta.getUpdatedAt());
        referenceDataResponse.setUpdatedBy(referenceDataMeta.getUpdatedBy());

        return referenceDataResponse;
    }

    private List<String> getUsedInPipelines(ReferenceDataMeta referenceDataMeta) {
        List<String> usedFieldPipelines = new ArrayList<>();

        List<String> pipelineIds = dependencyService.findDependencies(referenceDataMeta.getId(),
                ComponentType.referencedata, ComponentType.pipeline);
        Iterable<MappingGraph> pipelines = graphService.retrieve(pipelineIds);
        pipelines.forEach(p -> {
            if (p.getScope() == Scope.ATTRIBUTE && !p.isArchived()) {
                Optional<AttributeDefinition> activeAttribute = schemaService.getActiveAttribute(p.getTargetId());
                if (activeAttribute.isPresent()) {
                    usedFieldPipelines.add(p.getId());
                }
            }
        });
        return usedFieldPipelines;
    }

    public void validateFile(MultipartFile file) {
        if (file == null)
            throw new SyncariValidationException(i18n("file_required"));
        if(!file.getOriginalFilename().endsWith(CSV)) {
            throw new SyncariValidationException(i18n("unsupported_file_ext", FilenameUtils.getExtension(file.getOriginalFilename())));
        }
        if (!supportedContentType.stream().anyMatch((contentType -> contentType.equalsIgnoreCase(file.getContentType())))) {
            throw new SyncariValidationException(String.format(i18n("unsupported_content_type"), file.getContentType()));
        }
    }

    public MultipartFile getMultipartFile(ReferenceDataRequest referenceDataRequest) {
        try {
            String filename = UUID.randomUUID() + ".csv";
            FileWriter csvWriter = new FileWriter(Path.of(System.getProperty("java.io.tmpdir"))+"/"+filename);

            String fields = referenceDataRequest.getHeaderColumns().stream().collect(Collectors.joining(", "));

            csvWriter.append(fields);
            csvWriter.append("\n");

            csvWriter.flush();
            csvWriter.close();

            Path path = Paths.get(Path.of(System.getProperty("java.io.tmpdir"))+"/"+filename);
            String name = referenceDataRequest.getName()+".csv";
            String originalFileName = referenceDataRequest.getName()+".csv";
            String contentType = "text/csv";
            byte[] content = null;
            content = Files.readAllBytes(path);

            MultipartFile file = new MockMultipartFile(name, originalFileName, contentType, content);

            Files.delete(path);

            return file;
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }
}
