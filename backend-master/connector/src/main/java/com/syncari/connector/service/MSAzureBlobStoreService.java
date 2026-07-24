package com.syncari.connector.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syncari.connector.*;
import com.syncari.connector.config.AuthConfig;
import com.syncari.connector.data.*;
import com.syncari.connector.exception.ConnectorException;
import com.syncari.connector.exception.NotSupportedException;
import com.syncari.connector.service.def.*;
import com.syncari.utils.DateUtil;
import com.syncari.utils.Storage;
import com.syncari.utils.file.AzureBlobStoreFileManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component(Constants.MS_AZURE_BLOB_STORE)
public class MSAzureBlobStoreService extends BaseFileService implements CommonDataService, MetadataService, SynapseInfoService, OauthAuthenticationService {

    private static final String WM_FIELD = "lastmodifiedtime";
    public static final String OAUTH_URL = "%s/oauth2/token";
    public static final String AZURE_RESOURCE_URL = "https://storage.azure.com/";
    public static final String NAMESPACE = "Microsoft.Dynamics.CRM";

    @Autowired
    DefaultAuthTokenHandler tokenHandler;

    @Autowired
    ObjectMapper mapper;

    @Autowired
    DateUtil dateUtil;

    @Override
    public AuthConfig getAccessToken(OAuthRequest oAuthRequest) {
        oAuthRequest.setEndpoint(oAuthRequest.getEndpoint());
        Map<String, String> map = Map.of(DefaultAuthTokenHandler.GRANT_TYPE, "client_credentials",
                DefaultAuthTokenHandler.CODE, oAuthRequest.getCode(),
                DefaultAuthTokenHandler.CLIENT_ID, oAuthRequest.getConfig().getClientId(),
                DefaultAuthTokenHandler.CLIENT_SECRET, oAuthRequest.getConfig().getClientSecret(),
                DefaultAuthTokenHandler.REDIRECT_URI, oAuthRequest.getRedirectUri());
        AuthConfig a = tokenHandler.getAccessToken(String.format(OAUTH_URL, oAuthRequest.getEndpoint()), map);
        return a;
    }

    @Override
    public AuthConfig refreshToken(ConnectorInfo connector) {
        throw new RuntimeException("azure storage follows client_credentials flow. So, no refresh token is needed");
    }

    @Override
    public String getOAuthUri(ConnectorInfo connector) {
        return "/oauth2/v2.0/authorize?redirect_uri={{redirect_uri}}&client_id={{client_id}}&response_type=code&scope=offline_access https://storage.azure.com/.default";
    }

    public static String getContainerName(ConnectorInfo connector) {
        Object containerName = connector.getMetaConfig().get(Constants.AZURE_BLOB_STORE_CONTAINER_NAME);
        return containerName != null ? containerName.toString(): "";
    }

    public static String getStorageAccountName(ConnectorInfo connector) {
        Object storageAccountName = connector.getMetaConfig().get(Constants.AZURE_BLOB_STORE_STORAGE_ACCOUNT_NAME);
        return storageAccountName != null ? storageAccountName.toString(): "";
    }

    public static String getParentDirectory(ConnectorInfo connector) {
        Object directoryName = connector.getMetaConfig().get(Constants.AZURE_BLOB_STORE_DIRECTORY_NAME);
        return directoryName != null ? directoryName.toString(): "";
    }

    public static String getConnectionString(ConnectorInfo connector) {
        String connString = connector.getAuthConfig().getHeader(Constants.AZURE_BLOB_STORE_CONNECTION_STRING);
        return connString != null ? connString: "";
    }

    public static String getClientID(ConnectorInfo connector) {
        AuthConfig config = connector.getAuthConfig();
        return config.getClientId();
    }

    public static String getClientSecret(ConnectorInfo connector) {
        AuthConfig config = connector.getAuthConfig();
        return config.getClientSecret();
    }

    public static String getAuthCode(ConnectorInfo connector) {
        AuthConfig config = connector.getAuthConfig();
        return config.getAccessToken();
    }

    public static String getOauthEndpoint(ConnectorInfo connector) {
        AuthConfig config = connector.getAuthConfig();
        return config.getEndpoint();
    }


    @Override
    public List<Capability> getCapabilities() {
        var capabilities = new ArrayList<Capability>();
        capabilities.add(Capability.schemaEditInSyncari);
        capabilities.add(Capability.userEditableId);
        return capabilities;
    }


    @Override
    public boolean isSource() { return false; }

    @Override
    public TestConnectionResponse testConnection(ConnectorInfo config, List<String> entityNames) {
        TestConnectionResponse result = new TestConnectionResponse();
        try {
            if(!config.getMetaConfig().containsKey(Constants.AZURE_BLOB_STORE_CONTAINER_NAME) || StringUtils.isBlank(config.getMetaConfig().get(Constants.AZURE_BLOB_STORE_CONTAINER_NAME).toString()))
                throw new RuntimeException("Container Name is required");

            if(!config.getMetaConfig().containsKey(Constants.AZURE_BLOB_STORE_STORAGE_ACCOUNT_NAME) || StringUtils.isBlank(config.getMetaConfig().get(Constants.AZURE_BLOB_STORE_STORAGE_ACCOUNT_NAME).toString()))
                throw new RuntimeException("Storage Account Name is really required");

            if(!config.getMetaConfig().containsKey(Constants.AZURE_BLOB_STORE_DIRECTORY_NAME) || StringUtils.isBlank(config.getMetaConfig().get(Constants.AZURE_BLOB_STORE_DIRECTORY_NAME).toString()))
                throw new RuntimeException("Directory Name is required");

            if(!config.getMetaConfig().containsKey(Constants.AUTH_TYPE) || StringUtils.isBlank(config.getMetaConfig().get(Constants.AUTH_TYPE).toString()))
                throw new RuntimeException("Cannot recognize Authentication type");
            else{
                if(config.getMetaConfig().get(Constants.AUTH_TYPE).toString().equals(Constants.AZURE_BLOB_STORE_AUTH_TYPE_OAUTH_DISPLAY_NAME)){
                    if(StringUtils.isBlank(config.getEndpoint()))
                        throw new RuntimeException("End Point field is required for OAuth Authentication. Please fill a valid value for endpoint");
                } else if(config.getMetaConfig().get(Constants.AUTH_TYPE).toString().equals("UserPassword")) {
                    if(config.getAuthConfig().getHeader(Constants.AZURE_BLOB_STORE_CONNECTION_STRING) == null || (StringUtils.isBlank(config.getAuthConfig().getHeader(Constants.AZURE_BLOB_STORE_CONNECTION_STRING))))
                        throw new RuntimeException("Connection String is required");
                } else {
                    throw new RuntimeException("Auth Type is not recognizable");
                }
            }

            if(!config.getMetaConfig().containsKey(Constants.AUTH_TYPE) || StringUtils.isBlank(config.getMetaConfig().get(Constants.AZURE_BLOB_STORE_DIRECTORY_NAME).toString()))
                throw new RuntimeException("Cannot recognize Authentication type");

            DescribeAllRequest request = new DescribeAllRequest(config, List.of());
            describeAll(request);
        } catch (Exception e) {
            result.setMessage(e.getMessage());
            result.setCode(HttpStatus.UNAUTHORIZED.name());
        }
        return result;
    }

    @Override
    public FetchResponse getByWatermark(SyncRequest request) {
        return null;
    }

    @Override
    public long getFirstCreatedTime(SyncRequest request) {
        return 0;
    }

    @Override
    public List<EntityData> getByIds(SyncRequest request) {
        throw new NotSupportedException("Azure Blob store does not support getbyids");
    }

    private String getRootDirectory(ConnectorInfo connector) {
        String directory = connector.getMetaConfig().get(Constants.AZURE_BLOB_STORE_DIRECTORY_NAME).toString();
        return directory.endsWith(SLASH) ? directory : directory + SLASH;
    }

    @Override
    public SyncResponse create(SyncRequest request) {
        return writeFile(request, true, "create", getRootDirectory(request.getConnector()));
    }

    @Override
    public SyncResponse update(SyncRequest request) {
        return writeFile(request, true, "create", getRootDirectory(request.getConnector()));
    }

    @Override
    public SyncResponse delete(SyncRequest request) {
        return null;
    }

    @Override
    public Optional<EntitySchema> describe(DescribeRequest request) {
        DescribeAllRequest req = new DescribeAllRequest(request.getConnector(), List.of());
        return describeAll(req).stream().filter(e -> e.getApiName().equalsIgnoreCase(request.getEntity())).findFirst();
    }

    @Override
    public List<EntitySchema> describeAll(DescribeAllRequest request) {
        List<EntitySchema> schemaList = new ArrayList<>();
        try {
            Map<String, List<String>> entities = getAzureFileManager(request.getConnector()).getSchemaFromDirectory(getParentDirectory(request.getConnector()));
            for (Map.Entry<String, List<String>> entry : entities.entrySet()) {
                List<String> columns = new ArrayList<>();
                String entityName = entry.getKey();
                for (String item: entry.getValue()){
                    if (!item.equals(SYNCARI_ID))
                        columns.add(item);
                }
                EntitySchema entitySchema = new EntitySchema(entityName, StringUtils.capitalize(entityName));
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
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return schemaList;
    }

    @Override
    public EntitySchema createObject(CreateObjectRequest request) {
        throw new RuntimeException("createObject not supported in Azure blob store yet");
    }

    @Override
    public AttributeSchema createField(CreateFieldRequest request) {
        throw new RuntimeException("Azure blob store does not support create field");
    }

    @Override
    public void deleteField(DeleteFieldRequest request) {
        throw new RuntimeException("Azure blob store does not support delete field");
    }

    @Override
    public List<AuthMetadata> getSupportedAuthTypes() {
        return List.of(ConnectorHelper.getAzureBlobStoreAuth(), new AuthMetadata(AuthType.Oauth,
                List.of(ConnectorHelper.getClientIdField(), ConnectorHelper.getClientSecretField()), Constants.AZURE_BLOB_STORE_AUTH_TYPE_OAUTH_DISPLAY_NAME, ""));
    }

    @Override
    public List<AuthField> getConfigureFields() {
        AuthField accountName = new AuthField();
        accountName.setDataType("text");
        accountName.setName(Constants.AZURE_BLOB_STORE_STORAGE_ACCOUNT_NAME);
        accountName.setLabel("Storage Account Name");
        accountName.setHelpSummary("Storage Account Name");
        AuthField bucket = new AuthField();
        bucket.setDataType("text");
        bucket.setName(Constants.AZURE_BLOB_STORE_CONTAINER_NAME);
        bucket.setLabel("Container Name");
        bucket.setHelpSummary("The Container where data will be stored");
        AuthField folder = new AuthField();
        folder.setDataType("text");
        folder.setName(Constants.AZURE_BLOB_STORE_DIRECTORY_NAME);
        folder.setLabel("Directory Name");
        folder.setHelpSummary("The directory in container where data will be stored");
        AuthField endpointURL = ConnectorHelper.getEndpointField();
        endpointURL.setHelpSummary("Required only for OAuth based Authentication. Format : https://login.microsoftonline.com/{tenant_id}");
        endpointURL.setRequired(false);
        AuthField useDisplayName = new AuthField();
        useDisplayName.setDataType("boolean");
        useDisplayName.setName("useDisplayName");
        useDisplayName.setLabel("Use Display Name for Writes");
        useDisplayName.setRequired(false);
        useDisplayName.setHelpSummary("When writing to a file in S3, display names will be used as column header");
        return List.of(accountName, bucket, folder, endpointURL, ConnectorHelper.getSupportedAuthPicker(), useDisplayName);
    }

    @Override
    public Map<String, String> getEntityMappings() {
        return new HashMap<>();
    }

    @Override
    public Map<String, String> getAttributeMappings(String entityApiName) {
        return Map.of();
    }

    @Override
    public String getName() {
        return Constants.MS_AZURE_BLOB_STORE;
    }

    @Override
    public String getCategory() {
        return "Productivity";
    }

    @Override
    public UIMetadata getUIMetadata() {
        return new UIMetadata().setIconPath("/assets/icons/logos/azureBlogStorage.svg")
                .setDisplayName("Azure Blob Store")
                .setBackgroundColor("#FFF3F1")
                .setHelpUrl(helpArticlesBaseUrl + "/");
    }

    @Override
    public String getCapabilitiesArticleId() {
        //TODO: Implement
        return "";
    }

    @Override
    protected Storage getFileManager(ConnectorInfo connector) {
        return getAzureFileManager(connector);
    }

    private Boolean isOauth(ConnectorInfo connector) {
        if (connector.getMetaConfig().get(Constants.AUTH_TYPE).toString().equals(Constants.AZURE_BLOB_STORE_AUTH_TYPE_OAUTH_DISPLAY_NAME))
            return true;
        return false;
    }

    private AzureBlobStoreFileManager getAzureFileManager(ConnectorInfo connector) {
        if (!isOauth(connector))
            return new AzureBlobStoreFileManager(getStorageAccountName(connector), getConnectionString(connector),
                    getParentDirectory(connector), getContainerName(connector));
        return new AzureBlobStoreFileManager(getStorageAccountName(connector), getParentDirectory(connector), getContainerName(connector),
                    getClientID(connector), getClientSecret(connector), getOauthEndpoint(connector), getAuthCode(connector));
    }

}
