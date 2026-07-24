package com.syncari.connector.service;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.*;
import com.syncari.connector.*;
import com.syncari.connector.data.*;
import com.syncari.connector.data.DeleteObjectRequest;
import com.syncari.connector.data.iterator.CSVStorageIterator;
import com.syncari.connector.data.iterator.DefaultDataOffsetIterator;
import com.syncari.connector.exception.ConnectorException;
import com.syncari.connector.exception.NotSupportedException;
import com.syncari.connector.service.def.AuthenticationService;
import com.syncari.connector.service.def.CommonDataService;
import com.syncari.connector.service.def.MetadataService;
import com.syncari.connector.service.def.SynapseInfoService;
import com.syncari.utils.*;
import com.syncari.utils.file.S3FileManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.io.input.BOMInputStream;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component(Constants.S3)
public class S3Service extends BaseFileService implements CommonDataService, MetadataService, SynapseInfoService, AuthenticationService {
    private static final String FILE_NAME_FORMAT = "%s/%s_%s_%s.csv";
    private static final String WM_FIELD = "lastmodifiedtime";
    private static final long CLIENT_EXPIRATION_TIME = 15;
    @Autowired
    Transformer transformer;
    @Autowired
    TextUtil textUtil;
    @Autowired
    CsvUtils csvUtils;

    private AmazonS3 getClient(ConnectorInfo info) {
        String clientRegion = info.getMetaConfig().get("region").toString();
        ClientConfiguration clientConfiguration = new ClientConfiguration();
        clientConfiguration.setConnectionTimeout(30000);
        clientConfiguration.setSocketTimeout(60000);
        clientConfiguration.setMaxConnections(100);
        clientConfiguration.setMaxErrorRetry(5);
        return AmazonS3ClientBuilder.standard().withRegion(clientRegion)
                .withCredentials(credentialsProvider(info)).withClientConfiguration(clientConfiguration).build();
    }

    private AWSCredentialsProvider credentialsProvider(ConnectorInfo info) {
        String accessKey = info.getAuthConfig() == null ? null : info.getAuthConfig().getAccessToken();
        String secretKey = info.getAuthConfig() == null ? null : info.getAuthConfig().getClientSecret();
        if (StringUtils.isBlank(accessKey) && StringUtils.isBlank(secretKey)) {
            log.info("Amazon S3 connector is using the AWS workload IAM role");
            return DefaultAWSCredentialsProviderChain.getInstance();
        }
        if (StringUtils.isBlank(accessKey) || StringUtils.isBlank(secretKey)) {
            throw new IllegalArgumentException("Supply both an AWS access key and secret key, or leave both blank to use the workload IAM role");
        }
        return new AWSStaticCredentialsProvider(new BasicAWSCredentials(accessKey, secretKey));
    }

    @Override
    public List<AuthMetadata> getSupportedAuthTypes() {
        return List.of(ConnectorHelper.getS3Auth());
    }

    @Override
    public List<AuthField> getConfigureFields() {
        AuthField bucket = new AuthField();
        bucket.setDataType("text");
        bucket.setName("bucketName");
        bucket.setLabel("Bucket Name");
        bucket.setHelpSummary("The s3 bucket which holds data");
        AuthField folder = new AuthField();
        folder.setDataType("text");
        folder.setName("folderName");
        folder.setLabel("Folder Name");
        folder.setHelpSummary("The s3 folder which holds data");
        AuthField region = new AuthField();
        region.setDataType("text");
        region.setName("region");
        region.setLabel("Region");
        region.setHelpSummary("The region in which the bucket exists");
        AuthField useDisplayName = new AuthField();
        useDisplayName.setDataType("boolean");
        useDisplayName.setName("useDisplayName");
        useDisplayName.setLabel("Use Display Name for Writes");
        useDisplayName.setRequired(false);
        useDisplayName.setHelpSummary("When writing to a file in S3, display names will be used as column header");
        return List.of(bucket, folder, region, ConnectorHelper.getSupportedAuthPicker(), useDisplayName);
    }

    @Override
    public String getCategory() {
        return "Productivity";
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
    public String getName() {
        return Constants.S3;
    }

    public UIMetadata getUIMetadata() {
        return new UIMetadata().setIconPath("/assets/icons/logos/s3.svg")
                .setDisplayName("Amazon S3")
                .setBackgroundColor("#FFF3F1")
                .setHelpUrl(helpArticlesBaseUrl + "/360059073371-Amazon-S3-Setup");
    }

    @Override
    public FetchResponse getByWatermark(SyncRequest request) {
        AmazonS3 client = getAmazonS3Client(request.getConnector());
        String bucket = getBucketName(request.getConnector());
        String baseFolder = getBaseFolder(request.getConnector());
        List<S3ObjectSummary> files = getList(client, bucket, baseFolder + request.getEntityName()).stream()
				.filter(f -> !f.getKey().endsWith(SLASH)).collect(Collectors.toList());
		S3Generator generator = new S3Generator(csvUtils, request, client, bucket, files);
		int pgSize = (request.getPageSize() <= 0) ? 1000 : request.getPageSize();
        DefaultDataOffsetIterator iterator = new DefaultDataOffsetIterator(request.getWatermark(),
                request.getWatermark().getOffset(), generator, new ArrayList<>(),
                request.getEntitySchema().getWatermarkField(), pgSize, request.getWatermark().getLimit());
		return new FetchResponse(request.getWatermark(), iterator);
    }

    protected AmazonS3 getAmazonS3Client(ConnectorInfo connectorInfo) {
        try {
            return ConnectorHelper.backoffAndThrowOriginalException(() -> getClient(connectorInfo)
                    , 5000, 10000, 5, Optional.empty());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Optional<EntitySchema> describe(DescribeRequest request) {
        DescribeAllRequest req = new DescribeAllRequest(request.getConnector(), List.of());
        return describeAll(req).stream().filter(e -> e.getApiName().equalsIgnoreCase(request.getEntity())).findFirst();
    }

    @Override
    public List<EntitySchema> describeAll(DescribeAllRequest request) {
        List<EntitySchema> schemaList = new ArrayList<>();
        Set<String> entityList = new HashSet<>();
        AmazonS3 client = getAmazonS3Client(request.getConnector());
        try {
            String bucket = request.getConnector().getMetaConfig().get("bucketName").toString();
            String baseFolder = getBaseFolder(request.getConnector());
            List<S3ObjectSummary> folders = getList(client, bucket, baseFolder);
            for (S3ObjectSummary folder : folders) {
                // Only get folders as entities, ignore files
                String[] parts = folder.getKey().split("/");
                if (parts.length < 2) {
                    log.debug("Path not supported {}", folder.getKey());
                    continue;
                }
                String path = parts[1];
                if (!path.contains(".") && !baseFolder.equalsIgnoreCase(path) && !entityList.contains(path)) {
                    List<S3ObjectSummary> files = getList(client, bucket, baseFolder + path);
                    // Sort files by last modified date descending to get latest file first
                    // Files with null lastModified are placed at the end
                    files.sort((a, b) -> {
                        if (a.getLastModified() == null && b.getLastModified() == null) return 0;
                        if (a.getLastModified() == null) return 1;
                        if (b.getLastModified() == null) return -1;
                        return b.getLastModified().compareTo(a.getLastModified());
                    });
                    List<String> columns = new ArrayList<>();
                    for (S3ObjectSummary file : files) {
                        if (!file.getKey().endsWith(SLASH)) {
                            columns = getColumnsFromFile(client, bucket, file.getKey());
                            break;
                        }
                    }
                    EntitySchema entitySchema = new EntitySchema(path, StringUtils.capitalize(path));
                    entityList.add(path);
                    for (String field : columns) {
                        if (StringUtils.isBlank(field)) continue;
                        AttributeSchema attr = new AttributeSchema(textUtil.createApiName(field), "string");
                        attr.setDisplayName(field);
                        entitySchema.addField(attr);
                    }
                    if (!entitySchema.hasField(WM_FIELD)) {
                        AttributeSchema attr = new AttributeSchema(WM_FIELD, "datetime");
                        attr.setDisplayName("Last Modified Time");
                        entitySchema.addField(attr);
                    }
                    AttributeSchema wm = entitySchema.getField(WM_FIELD).get();
                    wm.setDataType("datetime");
                    wm.setWatermarkField(true);
                    wm.setUpdateable(false);
                    schemaList.add(entitySchema);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            client.shutdown();
        }
        return schemaList;
    }

    @Override
    public TestConnectionResponse testConnection(ConnectorInfo config, List<String> entityNames) {
        TestConnectionResponse result = new TestConnectionResponse();
        try {
            if(!config.getMetaConfig().containsKey("bucketName") || StringUtils.isBlank(config.getMetaConfig().get("bucketName").toString())) {
                throw new RuntimeException("Bucket Name is required");
            }
            boolean accessKeySupplied = StringUtils.isNotBlank(config.getAuthConfig().getAccessToken());
            boolean secretKeySupplied = StringUtils.isNotBlank(config.getAuthConfig().getClientSecret());
            if (accessKeySupplied != secretKeySupplied) {
                throw new RuntimeException("Supply both an AWS access key and secret key, or leave both blank to use the workload IAM role");
            }
            if(!config.getMetaConfig().containsKey("region") || StringUtils.isBlank(config.getMetaConfig().get("region").toString())) {
                throw new RuntimeException("Client Region is required");
            }
            DescribeAllRequest request = new DescribeAllRequest(config, List.of());
            describeAll(request);
        } catch (ConnectorException e) {
            result.setMessage(e.getMessage());
            result.setCode(HttpStatus.UNAUTHORIZED.name());
        } catch (Exception e) {
            result.setMessage(e.getMessage());
            result.setCode(HttpStatus.UNAUTHORIZED.name());
        }
        return result;
    }

    @Override
    public String getCapabilitiesArticleId() {
        return "19203235461908";
    }

    @Override
    public long getFirstCreatedTime(SyncRequest request) {
        return 0;
    }

    @Override
    public AttributeSchema createField(CreateFieldRequest request) {
        throw new RuntimeException("S3 does not support create field");
    }

    @Override
    public void deleteField(DeleteFieldRequest request) {
        throw new RuntimeException("s3 does not support delete field");
    }

    @Override
    public List<EntityData> getByIds(SyncRequest request) {
        throw new NotSupportedException("s3 does not support getbyids");
    }

    @Override
    public SyncResponse create(SyncRequest request) {
        // create a new file in s3 with all records in this batch
        return writeFile(request, true, "create", getBaseFolder(request.getConnector()));
    }

    @Override
    public SyncResponse update(SyncRequest request) {
        // create a new file in s3 with all updated records in this batch
        return writeFile(request, false, "update", getBaseFolder(request.getConnector()));
    }

    @Override
    public SyncResponse delete(SyncRequest request) {
        // create a new file in s3 with all deleted records in this batch
        return writeFile(request, false, "delete", getBaseFolder(request.getConnector()));
    }

    @Override
    public EntitySchema createObject(CreateObjectRequest request) {
        throw new RuntimeException("createObject not supported in s3 yet");
    }

    @Override
    public void deleteObject(DeleteObjectRequest request) {
        Storage storage = getFileManager(request.getConnector());
        String baseFolder = getBaseFolder(request.getConnector());
        AmazonS3 client = getAmazonS3Client(request.getConnector());
        try {
            String bucket = getBucketName(request.getConnector());
            List<S3ObjectSummary> files = getList(client, bucket, baseFolder + request.getEntityName());
            for (S3ObjectSummary file : files) {
                storage.delete(file.getKey());
            }
            storage.delete(baseFolder + request.getEntityName() + SLASH);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            client.shutdown();
        }
    }

    @Override
    public Map<String, String> getEntityMappings() {
        return new HashMap<>();
    }

    @Override
    public Map<String, String> getAttributeMappings(String entityApiName) {
        return Map.of();
    }

    private List<String> getColumns(InputStream stream) {
        InputStreamReader reader = new InputStreamReader(new BOMInputStream(stream));
        try (CSVParser parser = new CSVParser(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader().withTrim())) {
            return parser.getHeaderNames();
        } catch (IOException e) {
            log.error("Error while reading columns - {}", e.getMessage());
            throw new RuntimeException(e.getMessage());
        }
    }

    protected List<String> getColumnsFromFile(AmazonS3 client, String bucket, String fileKey) {
        S3Object f = null;
        try {
            f = client.getObject(new GetObjectRequest(bucket, fileKey));
            List<String> columns = new ArrayList<>(getColumns(f.getObjectContent()));
            columns.removeIf(item -> item.equalsIgnoreCase(SYNCARI_ID));
            log.info("{} columns fetched for file {}", columns.size(), f.getKey());
            return columns;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (f != null) {
                try {
                    f.close();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private String getBaseFolder(ConnectorInfo connector) {
        String folderName = connector.getMetaConfig().get("folderName").toString();
        return folderName.endsWith(SLASH) ? folderName : folderName + SLASH;
    }

    protected List<S3ObjectSummary> getList(AmazonS3 client, String bucket, String baseFolder) {
    	List<S3ObjectSummary> summaryList = new ArrayList<S3ObjectSummary>();
        ListObjectsRequest listObjectsRequest = new ListObjectsRequest().withBucketName(bucket).withPrefix(baseFolder);
        ObjectListing objListing = client.listObjects(listObjectsRequest);
        summaryList.addAll(objListing.getObjectSummaries());
        while(objListing.isTruncated()) {
        	objListing = client.listNextBatchOfObjects(objListing);
        	summaryList.addAll(objListing.getObjectSummaries());
        }
        return summaryList;
    }

    private String getBucketName(ConnectorInfo connector) {
        return connector.getMetaConfig().get("bucketName").toString();
    }

    @Override
    protected Storage getFileManager(ConnectorInfo connector) {
        String bucket = getBucketName(connector);
        String region = connector.getMetaConfig().get("region").toString();
        return new S3FileManager(bucket, region, connector.getAuthConfig().getAccessToken(), connector.getAuthConfig().getClientSecret(), getAmazonS3Client(connector));
    }

    private String getFileName(SyncRequest request, String operation) {
        return String.format(FILE_NAME_FORMAT, getBaseFolder(request.getConnector()) + request.getEntityName(), request.getEntityName(), operation, DateUtil.format(new Date()));
    }
}

class S3CSVStorageIterator extends CSVStorageIterator{

    public S3CSVStorageIterator(Storage storage, BatchJob job, int pageSize, SyncRequest request, CSVOptions options) {
        super(storage, job, pageSize, request, options);
    }
    public S3CSVStorageIterator(Storage storage, BatchJob job, int pageSize, SyncRequest request, boolean hasHeader) {
        super(storage, job, pageSize, request, hasHeader);
    }

    public S3CSVStorageIterator(Storage storage, BatchJob job, int pageSize, SyncRequest request, boolean hasHeader, char delimiter) {
        super(storage, job, pageSize, request, hasHeader, delimiter);
    }

    @Override
    protected Long getWatermark(Map<String, Object> values) {
        String watermarkFieldName = request.getEntitySchema().getWatermarkField().getApiName();
        if(values.get(watermarkFieldName)!=null){
            return super.getWatermark(values);
        }
        return storageLastModified;
    }
}
