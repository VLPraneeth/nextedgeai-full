package com.syncari.core.service;

import com.google.api.gax.paging.Page;
import com.google.cloud.ReadChannel;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Storage;
import com.syncari.connector.*;
import com.syncari.connector.data.*;
import com.syncari.connector.data.iterator.DefaultDataOffsetIterator;
import com.syncari.connector.service.Transformer;
import com.syncari.connector.service.def.AuthenticationService;
import com.syncari.connector.service.def.CommonDataService;
import com.syncari.connector.service.def.MetadataService;
import com.syncari.connector.service.def.SynapseInfoService;
import com.syncari.core.file.GCSFileManager;
import com.syncari.utils.CSVOptions;
import com.syncari.utils.CsvUtils;
import com.syncari.utils.TextUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.channels.Channels;
import java.util.*;
import java.util.stream.Collectors;

import static java.lang.String.format;

@Slf4j
@Component(Constants.FILE_DATA)
public class FileDataConnector implements CommonDataService, MetadataService, SynapseInfoService, AuthenticationService {
    private static final String SLASH = "/";
    @Autowired
    Transformer transformer;
    @Autowired
    TextUtil textUtil;
    @Autowired
    CsvUtils csvUtils;
    @Autowired
    @Qualifier("gcsImportedFilesFileManager")
    private GCSFileManager gcsFileManager;
    @Autowired
    SchemaService schemaService;

    @Override
    public List<AuthMetadata> getSupportedAuthTypes() {
        return List.of();
    }

    @Override
    public List<AuthField> getConfigureFields() {
        return List.of();
    }
    
    @Override
    public boolean isSink() {
        return false;
    }

    @Override
    public String getCategory() {
        return "Productivity";
    }
    
    @Override
    public String getName() {
        return Constants.FILE_DATA;
    }

    public UIMetadata getUIMetadata() {
        return new UIMetadata().setIconPath("/assets/icons/logos/imported-files.svg")
                .setDisplayName(Constants.IMPORTED_FILES)
                .setBackgroundColor("#EFEFEF");
    }

    @Override
    public List<Capability> getCapabilities() {
        var capabilities = new ArrayList<Capability>();
        capabilities.add(Capability.schemaEditInSyncari);
        capabilities.add(Capability.userEditableId);
        capabilities.add(Capability.userEditableWm);
        return capabilities;
    }

    @Override
    public String getCapabilitiesArticleId() {
        return "";
    }


    @Override
	public FetchResponse getByWatermark(SyncRequest request) {
        log.info("FileDataService received getByWatermark request for {}", request.getEntityName());
        String bucket = request.getConnector().getMetaConfig().get("bucketName").toString();
        String baseFolder = getBaseFolder(request.getConnector());
        log.info("GCS bucket configured is {} and basefolder is {} with connectorId {}", bucket, baseFolder, request.getConnector().getId());
        List<Blob> files = getFiles(bucket, baseFolder + request.getEntityName() + SLASH);
        files = files.stream().filter(f -> hasRows(bucket, f)).collect(Collectors.toList());
        FileDataGenerator generator = new FileDataGenerator(csvUtils, textUtil, request, files);
        int pgSize = (request.getPageSize() <= 0) ? 1000 : request.getPageSize();
        DefaultDataOffsetIterator iterator = new DefaultDataOffsetIterator(request.getWatermark(),
                request.getWatermark().getOffset(), generator, new ArrayList<>(),
                request.getEntitySchema().getWatermarkField(), pgSize, request.getWatermark().getLimit());
        return new FetchResponse(request.getWatermark(), iterator);
    }

    @Override
    public Optional<EntitySchema> describe(DescribeRequest request) {
    	log.info("FileDataService received describe request for {}", request.getEntity());
        DescribeAllRequest req = new DescribeAllRequest(request.getConnector(), List.of(request.getEntity()));
        if(request.getExistingSchema() != null && request.getExistingSchema().isPresent()) {
            req.getExisting().add(request.getExistingSchema().get());
        }
        return describeAll(req).stream().filter(e -> e.getApiName().equalsIgnoreCase(request.getEntity())).findFirst();
    }

    @Override
    public List<EntitySchema> describeAll(DescribeAllRequest request) {
    	log.info("FileDataService received describeAll request for {}", request.getEntities());
        List<EntitySchema> schemaList = new ArrayList<>();
        String bucket = request.getConnector().getMetaConfig().get("bucketName").toString();
        log.info("GCS bucket configured is {}", bucket);
        String baseFolder = getBaseFolder(request.getConnector());
        List<String> allFolders = getFolders(bucket, baseFolder);
        List<String> folders = allFolders.stream().filter(f -> request.getEntities().contains(f)).collect(Collectors.toList());
        for (String folder : folders) {
            String entityFolder = baseFolder + folder + SLASH;
            List<Blob> files = getFiles(bucket, entityFolder);
            Map<String, String> columns = Map.of();
        	for (Blob file : files) {
                String fileFullPath = file.getName();
                InputStream stream = read(bucket, fileFullPath);
                columns = csvUtils.detectDatatypes(stream, new CSVOptions());
        		break;
        	}
        	String entityName = folder;
        	EntitySchema entitySchema = new EntitySchema(textUtil.createApiName(entityName), StringUtils.capitalize(entityName));
            Map additionProps = request.getExistingEntity(entityName).map(e -> e.getAdditionalProperties()).orElse(Map.of());
            entitySchema.setAdditionalProperties(additionProps);

            // Track API names to prevent duplicates (same logic as FileDataService.createAttributes)
            Set<String> apiNames = new HashSet<>();

            for (Map.Entry<String, String> field : columns.entrySet()) {
        		String filedName = field.getKey();
        		String apiName = textUtil.createApiName(filedName).toLowerCase();

        		// Handle duplicates using SchemaService logic
        		while(apiNames.contains(apiName)) {
        		    apiName = schemaService.populateApiNameWithCounter(apiName);
        		}
        		apiNames.add(apiName);

        		AttributeSchema attr = new AttributeSchema(apiName, field.getValue());
        		attr.setDisplayName(filedName);
        		entitySchema.addField(attr);
        	}
        	if(!apiNames.contains("lastmodifiedtime")) {
        		AttributeSchema attr = new AttributeSchema("lastModifiedTime", "datetime");
        		attr.setDisplayName("Last Modified Time");
        		entitySchema.addField(attr);
        	}
        	AttributeSchema wm = entitySchema.getField("lastModifiedTime").get();
        	wm.setDataType("datetime");
        	wm.setWatermarkField(true);
        	wm.setUpdateable(false);
        	schemaList.add(entitySchema);
        }
        return schemaList;
    }

    @Override
    public TestConnectionResponse testConnection(ConnectorInfo config, List<String> entityNames) {
        return new TestConnectionResponse();
    }

    @Override
    public long getFirstCreatedTime(SyncRequest request) {
        return 0;
    }

    @Override
    public AttributeSchema createField(CreateFieldRequest request) {
        throw new RuntimeException("File Data does not support create field");
    }

    @Override
    public void deleteField(DeleteFieldRequest request) {
        throw new RuntimeException("File Data does not support delete field");
    }

    @Override
    public List<EntityData> getByIds(SyncRequest request) {
    	log.info("FileDataService received getByIds request for {}", request.getEntityName());
        if (!request.getEntitySchema().hasIdField()) {
            throw new RuntimeException("Id field not defined for entity " + request.getEntityName());
        }
        List<String> ids = getIds(request);
        if (CollectionUtils.isEmpty(ids)) {
            throw new RuntimeException("Incoming Ids field not defined for entity" + request.getEntityName());
        }
        String bucket = request.getConnector().getMetaConfig().get("bucketName").toString();
        String baseFolder = getBaseFolder(request.getConnector());
        log.info("GCS bucket configured is {} and basefolder is {} with connectorId {}", bucket, baseFolder, request.getConnector().getId());
        List<Blob> files = getFiles(bucket, baseFolder + request.getEntityName() + SLASH);
        files = files.stream().filter(f -> hasRows(bucket, f)).collect(Collectors.toList());
        boolean withTrim = getWithTrimProps(request);
        Map<String, EntityData> entityMap = new HashMap<>();
        for (Blob file : files) {
            try {
                InputStream stream = read(bucket, file.getName());
                var recordParser = csvUtils.getCSVParser(stream, new CSVOptions().withTrim(withTrim));
                for (var rec : recordParser) {
                    EntityData ed = createRecord(request, rec);
                    if (ed != null && ed.getId() != null && ids.contains(ed.getId())) {
                        entityMap.put(ed.getId(), ed);
                    }
				}
			} catch (Exception e) {
				log.error("Csv file parsing error", e);
			}
			
		}
		return new ArrayList<EntityData>(entityMap.values());
    	
    }
    
    private List<String> getIds(SyncRequest request) {
        List<EntityData> entityList = request.getData().get(request.getConnector().getId());
        return entityList.stream().map(e -> e.getId()).collect(Collectors.toList());
    }

    private boolean getWithTrimProps(SyncRequest request) {
        return (boolean) request.getEntitySchema().getAdditionalProperties().getOrDefault(Constants.WITH_TRIM, true);
    }

    @Override
    public SyncResponse create(SyncRequest request) {
        throw new RuntimeException("File Data does not support create");
    }

    @Override
    public SyncResponse update(SyncRequest request) {
        throw new RuntimeException("File Data does not support update");
    }

    @Override
    public SyncResponse delete(SyncRequest request) {
        throw new RuntimeException("File Data does not support delete");
    }
    
    @Override
    public EntitySchema createObject(CreateObjectRequest request) {
        throw new RuntimeException("createObject not supported in File Data yet");
    }
    
    @Override
    public void deleteObject(DeleteObjectRequest request) {
        throw new RuntimeException("File Data does not support delete");
    }

    @Override
    public Map<String, String> getEntityMappings() {
        return new HashMap<>();
    }
    
    @Override
    public Map<String, String> getAttributeMappings(String entityApiName) {
        return Map.of();
    }

    private String getBaseFolder(ConnectorInfo connector) {
    	String instance = connector.getInstanceId();
        return instance + SLASH + "FileData" + SLASH;
    }

    private List<String> getFolders(final String bucket, final String directory) {
        List<String> folderNames = new ArrayList<>();
        Page<Blob> blobs = gcsFileManager.getStorageService().list(bucket, Storage.BlobListOption.currentDirectory(), Storage.BlobListOption.prefix(directory));
        Iterable<Blob> blobIterator = blobs.iterateAll();
        blobIterator.forEach(blob -> {
            if (blob.isDirectory()) {
                String folderName = blob.getName().substring(0, blob.getName().lastIndexOf(SLASH));
                folderName = folderName.substring(folderName.lastIndexOf(SLASH) + 1);
                folderNames.add(folderName);
            }
        });
        return folderNames;
    }

    private List<Blob> getFiles(final String bucket, final String directory) {
        List<Blob> files = new ArrayList<>();
        Page<Blob> blobs = gcsFileManager.getStorageService().list(bucket, Storage.BlobListOption.prefix(directory));
        Iterable<Blob> blobIterator = blobs.iterateAll();
        blobIterator.forEach(blob -> {
            if (!blob.getName().endsWith(SLASH)) {
                files.add(blob);
            }
        });
        return files;
    }
    
    protected EntityData createRecord(SyncRequest request, CSVRecord next) {
        boolean withTrim = getWithTrimProps(request);
    	String idFieldName = request.getEntitySchema().getIdField().getApiName();
    	String watermarkFieldName = request.getEntitySchema().getWatermarkField().getApiName();
        Map<String, Object> values = new HashMap<>();
		next.toMap().forEach((k, v) -> {
			var attr = request.getEntitySchema().getFieldByDisplayName(k).orElse(null);
			// If not found by display name, try by API name (handles header variations like spaces vs underscores)
			if (attr == null) {
				String apiName = textUtil.createApiName(k);
				attr = request.getEntitySchema().getField(apiName).orElse(null);
			}
            var key = attr == null ? k : attr.getApiName();
            if(key != null) {
            	key = key.trim().replaceAll("^\"|\"$", "");
            }
            if(v != null && attr != null && attr.isMultiValueField()) {
                if(withTrim) {
                    values.put(key, Arrays.stream(v.trim().split(",")).map(v1 -> v1.trim()).collect(Collectors.toList()));
                } else {
                    values.put(key, Arrays.stream(v.split(",")).map(v1 -> v1).collect(Collectors.toList()));
                }
            } else {
                if(withTrim) {
                    values.put(key, v == null ? null : v.trim());
                } else {
                    values.put(key, v == null ? null : v);
                }
            }
		});
        EntityData record = new EntityData().setValues(values);
        String id = Objects.toString(values.get(idFieldName.toLowerCase()),null);
		Long watermark = 0L;
		var wm = values.get(watermarkFieldName.toLowerCase());
		if (wm != null) {
			watermark = ConnectorHelper.convert(Objects.toString(wm)).toInstant().toEpochMilli();
        } else if (request.getWatermark() != null) {
            watermark = request.getWatermark().getStart();
        }
        record.setId(id);
        record.setLastModified(watermark);
        record.setName(request.getEntitySchema().getApiName());
        record.setConnectorId(request.getConnector().getId());
        return record;
    }

    private InputStream read(final String bucket, final String file) {
        Blob blob = gcsFileManager.getStorageService().get(bucket, file);
        if (blob == null) throw new RuntimeException("File with name " + file + " not found");
        ReadChannel reader = blob.reader();
        log.info(format("File with name %s successfully read", file));
        return Channels.newInputStream(reader);
    }

    private boolean hasRows(final String bucket, final Blob file) {
        InputStream stream = read(bucket, file.getName());

        return csvUtils.hasRows(stream, new CSVOptions());
    }
}