package com.syncari.connector.service;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalListener;
import com.syncari.connector.*;
import com.syncari.connector.data.*;
import com.syncari.connector.exception.NotSupportedException;
import com.syncari.connector.service.def.AuthenticationService;
import com.syncari.connector.service.def.CommonDataService;
import com.syncari.connector.service.def.MetadataService;
import com.syncari.connector.service.def.SynapseInfoService;
import com.syncari.utils.Storage;
import com.syncari.utils.file.SftpClient;
import com.syncari.utils.file.SftpFileManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component(Constants.SFTP)
public class SftpService extends BaseFileService implements CommonDataService, MetadataService, SynapseInfoService, AuthenticationService {

    private static final String SFTP_SETUP_ARTICLE = "/8104545538708-SFTP-Setup";
    private static final long CLIENT_EXPIRATION_TIME = 15;
    public static final String PRIVATE_KEY = "privateKey";
    public static final String PASSPHRASE = "passphrase";
    public static final String BASE_FOLDER_PATH = CSVService.BASE_FOLDER_PATH;
    public static final String HOST = "host";


    protected static LoadingCache<ConnectorInfo, SftpClient> cache = CacheBuilder.newBuilder()
            .maximumSize(30)
            .expireAfterAccess(CLIENT_EXPIRATION_TIME, TimeUnit.MINUTES)
            .removalListener((RemovalListener<ConnectorInfo, SftpClient>) cachedClient -> cachedClient.getValue().close())
            .build(
                    new CacheLoader<>() {
                        @Override
                        public SftpClient load(ConnectorInfo config) {
                            return getClient(config);
                        }
                    }
            );


    @Override
    public List<AuthMetadata> getSupportedAuthTypes() {
        return List.of(ConnectorHelper.getUserPwd(), ConnectorHelper.getPrivateKey());
    }

    @Override
    public List<AuthField> getConfigureFields() {
        AuthField server = new AuthField();
        server.setDataType("text");
        server.setName(BASE_FOLDER_PATH);
        server.setLabel("Full Path");
        server.setHelpSummary("The fully qualified path to the folder that has all entity csv files. ex: /home/folderName");
        AuthField folderSelectorPattern = new AuthField();
        folderSelectorPattern.setRequired(false);
        folderSelectorPattern.setDataType("text");
        folderSelectorPattern.setName(CSVService.MATCHING_FOLDERS);
        folderSelectorPattern.setLabel("Matching folders");
        folderSelectorPattern.setHelpSummary("A regular expression to only select matching subfolders under the base folder.Each subfolder maps to an entity.");
        AuthField fileSelectorPattern = new AuthField();
        fileSelectorPattern.setRequired(false);
        fileSelectorPattern.setDataType("text");
        fileSelectorPattern.setName(CSVService.MATCHING_FILES);
        fileSelectorPattern.setLabel("Matching files");
        fileSelectorPattern.setHelpSummary("A regular expression to only select matching files under each entity folder.Optional");
        AuthField separator = new AuthField();
        separator.setRequired(false);
        separator.setDataType("text");
        separator.setName("delimiter");
        separator.setLabel("Field Separator");
        separator.setHelpSummary("Field separator for CSV. Defaults to ',' . To use tabs, enter \\t");
        AuthField hasHeader = new AuthField();
        hasHeader.setRequired(false);
        hasHeader.setDataType("boolean");
        hasHeader.setName("hasHeader");
        hasHeader.setLabel("Has Header");
        hasHeader.setDefaultValue(true);
        hasHeader.setHelpSummary("Assumes the first line to be the header headers in files, if checked");
        AuthField skipLinesPattern = new AuthField();
        skipLinesPattern.setRequired(false);
        skipLinesPattern.setDataType("text");
        skipLinesPattern.setName("skipLinesPattern");
        skipLinesPattern.setLabel("Skip Lines Matching Pattern");
        skipLinesPattern.setHelpSummary("A regular expression to skip matching lines from CSV. Note that this doesn't work well when CSV fields have newlines");

        AuthField host = new AuthField();
        host.setDataType("text");
        host.setName(HOST);
        host.setLabel("Host");
        host.setHelpSummary("The name/IP address of the host (optionally add port, default is 22). ex: server:22");
        return List.of(host, server, folderSelectorPattern, fileSelectorPattern, separator, hasHeader, skipLinesPattern, ConnectorHelper.getSupportedAuthPicker());
    }

    @Override
    public String getCategory() {
        return "Productivity";
    }

    @Override
    public String getCapabilitiesArticleId() {
        return "19200805855636";
    }


    @Override
    public String getName() {
        return Constants.SFTP;
    }

    public UIMetadata getUIMetadata() {
        return new UIMetadata().setIconPath("/assets/icons/logos/sftp.svg")
                .setDisplayName("SFTP")
                .setBackgroundColor("#FFF3F1")
                .setHelpUrl(helpArticlesBaseUrl + SFTP_SETUP_ARTICLE);
    }

    @Override
    public List<Capability> getCapabilities() {
        var capabilities = new ArrayList<Capability>();
        capabilities.add(Capability.schemaEditInSyncari);
        capabilities.add(Capability.userEditableId);
        capabilities.add(Capability.userEditableWm);
        capabilities.add(Capability.compositeId);
        return capabilities;
    }

    private static SftpClient getSftpClient(ConnectorInfo config) {
        try {
            return cache.get(config);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public FetchResponse getByWatermark(SyncRequest request) {
        return getFileSystemService(request.getConnector()).getByWatermark(request);
    }



    @Override
    public Optional<EntitySchema> describe(DescribeRequest request) {
        DescribeAllRequest req = new DescribeAllRequest(request.getConnector(), List.of());
        return describeAll(req).stream().filter(e -> e.getApiName().equalsIgnoreCase(request.getEntity())).findFirst();
    }

    @Override
    public List<EntitySchema> describeAll(DescribeAllRequest request) {
        final ConnectorInfo connector = request.getConnector();
        return getFileSystemService(connector).describeAll(request);
    }

    @Override
    public TestConnectionResponse testConnection(ConnectorInfo config, List<String> entityNames) {
        TestConnectionResponse result = new TestConnectionResponse();
        try {
            if (!config.getMetaConfig().containsKey(HOST) || StringUtils.isBlank(config.getMetaConfig().get(HOST).toString())) {
                throw new RuntimeException("Host is required");
            }
            if (!config.getMetaConfig().containsKey(BASE_FOLDER_PATH) || StringUtils.isBlank(config.getMetaConfig().get(BASE_FOLDER_PATH).toString())) {
                throw new RuntimeException("Base Folder Path is required");
            }
            getSftpClient(config).getClient();
        } catch (Exception e) {
            log.error(ExceptionUtils.getStackTrace(e));
            result.setMessage(e.getMessage());
            result.setCode(HttpStatus.UNAUTHORIZED.name());
        }
        return result;
    }

    @Override
    public long getFirstCreatedTime(SyncRequest request) {
        return 0;
    }

    @Override
    public AttributeSchema createField(CreateFieldRequest request) {
        throw new RuntimeException("sftp does not support create field");
    }

    @Override
    public void deleteField(DeleteFieldRequest request) {
        throw new RuntimeException("sftp does not support delete field");
    }

    @Override
    public List<EntityData> getByIds(SyncRequest request) {
        throw new NotSupportedException("sftp does not support getbyids");
    }

    @Override
    public SyncResponse create(SyncRequest request) {
        return getFileSystemService(request.getConnector()).writeFile(
                request, false, "create", getBaseFolder(request)
        );
    }

    private static FileSystemService getFileSystemService(ConnectorInfo connector) {
        String fileFormat = connector.getMetaConfig().getOrDefault("fileFormat", "CSV").toString();
        //TODO: return different services based on fileFormat
        final SftpClient sftpClient = getSftpClient(connector);
        SftpFileManager manager = new SftpFileManager(sftpClient);
        return new CSVService(manager, manager);
    }

    @Override
    public SyncResponse update(SyncRequest request) {
        return getFileSystemService(request.getConnector()).writeFile(request, false, "update", getBaseFolder(request));
    }

    @Override
    public SyncResponse delete(SyncRequest request) {
        return getFileSystemService(request.getConnector()).writeFile(
                request, false, "delete", getBaseFolder(request)
        );
    }

    private static String getBaseFolder(SyncRequest request) {
        return request.getConnector().getMetaConfig().get(BASE_FOLDER_PATH).toString();
    }

    @Override
    public EntitySchema createObject(CreateObjectRequest request) {
        return getFileSystemService(request.getConnector()).createObject(request);
    }

    @Override
    public void deleteObject(DeleteObjectRequest request) {
        getFileSystemService(request.getConnector()).deleteObject(request);
    }

    @Override
    public Map<String, String> getEntityMappings() {
        return new HashMap<>();
    }

    @Override
    public Map<String, String> getAttributeMappings(String entityApiName) {
        return Map.of();
    }

    protected static SftpClient getClient(ConnectorInfo info) {
        String host = info.getMetaConfig().get(HOST).toString();
        String baseFolderPath = info.getMetaConfig().get(BASE_FOLDER_PATH).toString();
        String userName = info.getAuthConfig().getUserName();
        String password = info.getAuthConfig().getPassword();
        String privateKey = info.getAuthConfig().getAdditionalHeaders().get(PRIVATE_KEY);
        String passPhrase = info.getAuthConfig().getAdditionalHeaders().get(PASSPHRASE);
        if (StringUtils.isBlank(privateKey)) {
            return new SftpClient(baseFolderPath, userName, password, host);
        }
        return SftpClient.withPrivateKey(baseFolderPath, userName, privateKey, passPhrase, host);
    }

    @Override
    protected Storage getFileManager(ConnectorInfo connector) {
        SftpClient sftpClient = getSftpClient(connector);
        return new SftpFileManager(sftpClient);
    }
}

