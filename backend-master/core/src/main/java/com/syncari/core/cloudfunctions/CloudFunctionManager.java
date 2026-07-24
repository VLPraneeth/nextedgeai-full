package com.syncari.core.cloudfunctions;


import java.io.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.google.api.core.ApiFutureCallback;
import com.google.api.core.ApiFutures;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.api.gax.longrunning.OperationFuture;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.functions.v1.*;
import com.google.protobuf.Duration;
import com.syncari.core.SyncariContext;
import com.syncari.core.config.AppConfig;
import com.syncari.core.file.GCSFileManager;

import com.syncari.core.service.ConnectorMetadataService;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.ZipParameters;

@Slf4j
@Component
public class CloudFunctionManager {

    public static final String PYTHON_RUNTIME = "python39";
    public static final String DEFAULT_REGION = "us-west2";

    public static final long DEFAULT_CF_EXEC_TIMEOUT_SECS = 540;
    public static final int MAX_CF_INSTANCES_DEFAULT = 5;
    public static final int MAX_CF_INSTANCES_GLOBAL = 50;

    @Autowired
    AppConfig appConfig;

    @Autowired @Qualifier("gcsCfFileManager")
    GCSFileManager gcsCfFileManager;

    @Autowired
    CloudFunctionLogPoller cloudFunctionLogPoller;

    @Autowired
    ConnectorMetadataService connectorMetadataService;

    public void create(String functionName, InputStream synapseFile, InputStream requirementsFile, String region, String fileName) {
        createAndUploadSourceZip(functionName, synapseFile, requirementsFile, fileName);
        execute(functionName, PYTHON_RUNTIME, region, CloudFunctionOperation.CREATE, false, fileName, MAX_CF_INSTANCES_DEFAULT);
    }

    public void update(String functionName, InputStream synapseFile, InputStream requirementsFile, String region, String fileName) {
        createAndUploadSourceZip(functionName, synapseFile, requirementsFile, fileName);
        execute(functionName, PYTHON_RUNTIME, region, CloudFunctionOperation.UPDATE, false, fileName, MAX_CF_INSTANCES_DEFAULT);
    }

    public void delete(String functionName, String region, String fileName) {
        execute(functionName, null, region, CloudFunctionOperation.DELETE, false, fileName, MAX_CF_INSTANCES_DEFAULT);
        gcsCfFileManager.delete(fileName, getBucketName());
    }

    public void clone(String functionName, String newFunctionName, String region, boolean isGlobal, String fileName, String newFileName, Integer maxInstances) {
        cloneZipFile(functionName, newFunctionName, fileName, newFileName);
        try {
            if (hasFunction(newFunctionName, region)) {
                execute(newFunctionName, PYTHON_RUNTIME, region, CloudFunctionOperation.UPDATE, isGlobal, newFileName, maxInstances);
            } else {
                execute(newFunctionName, PYTHON_RUNTIME, region, CloudFunctionOperation.CREATE, isGlobal, newFileName, maxInstances);
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new RuntimeException("Failed to clone cloud function due to " + e.getMessage(), e);
        }
    }

    public InputStream getSourceFiles(String fileName) {
        return gcsCfFileManager.read(fileName, getBucketName());
    }

    public boolean hasFunction(String functionName, String region) {
        try (CloudFunctionsServiceClient client = getClient()) {
            functionName = CloudFunctionName.of(appConfig.getGcpCfProjectId(), region, functionName).toString();
            CloudFunction cloudFunction = client.getFunction(functionName);
            return true;
        } catch (Exception e) {
            String msg = String.format("Failed to get cloud function with name %s.", functionName);
            log.warn(msg);
            return false;
        }
    }

    public SyncariCloudFunctionStatus getStatus(String functionName, String region) {
        try (CloudFunctionsServiceClient client = getClient()) {
            String cfName = CloudFunctionName.of(appConfig.getGcpCfProjectId(), region, functionName).toString();
            CloudFunction cloudFunction = client.getFunction(GetFunctionRequest.newBuilder().setName(cfName).build());
            client.awaitTermination(2, TimeUnit.SECONDS);
            return toSyncariCloudFunctionStatus(functionName, cloudFunction);
        } catch (Exception e) {
            String msg = String.format("Failed to get status on cloud function with name %s.", functionName);
            if(e.getMessage().contains("NOT_FOUND")) {
                msg = String.format("Cloud function with name %s does not exist. Status = 'NOT_FOUND'", functionName);
            }
            log.error(msg, e);
            throw new RuntimeException(msg, e);
        }
    }

    public InputStream getCloudFunctionErrorLog(String functionName) {
        try {
            File file = File.createTempFile(functionName + RandomUtils.nextInt(1,2000), ".txt");
            String data = cloudFunctionLogPoller.getErrorLogEntries(functionName, Instant.now().minus(1, ChronoUnit.DAYS).toEpochMilli());
            if (StringUtils.isEmpty(data)) {
                data = "No error logs found";
            }
            try (FileWriter fileWriter = new FileWriter(file)) {
                fileWriter.append(data).flush();
                return new FileInputStream(file);
            }
        } catch (Exception ex) {
            log.error("Failed to prepare error log entries for custom synapse {} due to {}", functionName, ex.getMessage(), ex);
            throw new RuntimeException("Failed to prepare error log entries for custom synapse " + functionName, ex);
        }
    }

    // Wrapper for Syncari for better control on the status and actions based on the state.
    private SyncariCloudFunctionStatus toSyncariCloudFunctionStatus(String cfName, CloudFunction cloudFunction) {
        switch (cloudFunction.getStatus()) {
            case DEPLOY_IN_PROGRESS:
                return new SyncariCloudFunctionStatus(SyncariCloudFunctionStatus.CODE.DEPLOY_IN_PROGRESS, "");
            case DELETE_IN_PROGRESS:
                return new SyncariCloudFunctionStatus(SyncariCloudFunctionStatus.CODE.DELETE_IN_PROGRESS, "");
            case ACTIVE:
                return new SyncariCloudFunctionStatus(SyncariCloudFunctionStatus.CODE.ACTIVE, "");
            default:
                return new SyncariCloudFunctionStatus(SyncariCloudFunctionStatus.CODE.ERROR,
                    cloudFunctionLogPoller.getRecentDeploymentError(cfName, cloudFunction.getUpdateTime().getSeconds()));
        }
    }

    protected void execute(String functionName, String runtime, String region, CloudFunctionOperation operation, boolean isGlobal, String fileName, Integer maxInstances)  {
        String sourcePath = String.format("gs://%s/%s", getBucketName(), fileName);
        String cloudFunctionName = CloudFunctionName.of(appConfig.getGcpCfProjectId(), region, functionName).toString();
        try (CloudFunctionsServiceClient client = getClient()) {
            String location = LocationName.of(appConfig.getGcpCfProjectId(), region).toString();
            int cfMaxInstances = isGlobal ? MAX_CF_INSTANCES_GLOBAL : maxInstances != null && maxInstances > 0 ? maxInstances : MAX_CF_INSTANCES_DEFAULT;
            switch (operation) {
                case CREATE:
                    CloudFunction cloudFunctionToCreate = CloudFunction.newBuilder()
                            .setName(cloudFunctionName)
                            .setEntryPoint("execute")
                            .setSourceArchiveUrl(sourcePath)
                            .setServiceAccountEmail(appConfig.getCfExecutorSAEmail())
                            .setVpcConnector(appConfig.getCfVpcConnector())
                            .setVpcConnectorEgressSettings(CloudFunction.VpcConnectorEgressSettings.ALL_TRAFFIC)
                            // TODO change this when we have the networking setup betwween main and synapse gcp projects
                            .setIngressSettings(CloudFunction.IngressSettings.ALLOW_ALL)
                            .setRuntime(runtime)
                            .setHttpsTrigger(HttpsTrigger.getDefaultInstance().toBuilder().setSecurityLevel(HttpsTrigger.SecurityLevel.SECURE_ALWAYS))
                            .setMinInstances(1)
                            .setMaxInstances(cfMaxInstances)
                            .setTimeout(Duration.newBuilder().setSeconds(DEFAULT_CF_EXEC_TIMEOUT_SECS).build())
                            .build();
                    client.createFunctionAsync(location, cloudFunctionToCreate);
                    log.info("Successfully deployed function {}", cloudFunctionName);
                    break;
                case UPDATE:
                    CloudFunction cloudFunctionToUpdate = CloudFunction.newBuilder()
                            .setName(cloudFunctionName)
                            .setEntryPoint("execute")
                            .setSourceArchiveUrl(sourcePath)
                            .setRuntime(runtime)
                            .setVpcConnector(appConfig.getCfVpcConnector())
                            .setVpcConnectorEgressSettings(CloudFunction.VpcConnectorEgressSettings.ALL_TRAFFIC)
                            .setIngressSettings(CloudFunction.IngressSettings.ALLOW_ALL)
                            .setHttpsTrigger(HttpsTrigger.getDefaultInstance())
                            .setMinInstances(1)
                            .setMaxInstances(cfMaxInstances)
                            .setTimeout(Duration.newBuilder().setSeconds(DEFAULT_CF_EXEC_TIMEOUT_SECS).build())
                            .build();
                    client.updateFunctionAsync(cloudFunctionToUpdate);
                    log.info("Successfully updated function {}", cloudFunctionName);
                    break;
                case DELETE:
                    client.deleteFunctionAsync(cloudFunctionName);
                    log.info("Successfully deleted function {}", cloudFunctionName);
                    break;
            }
            client.awaitTermination(2, TimeUnit.SECONDS);
        } catch (Exception e) {
            String msg = String.format("Failed to execute operation %s on cloud function with name %s.", operation, cloudFunctionName);
            log.error(msg, e);
            throw new RuntimeException(msg, e);
        }
    }

    public File createSourceZip(String functionName, InputStream synapseFile, InputStream requirementsFile) {
        ZipFile zipFile = new ZipFile(functionName+".zip");
        try {
            ZipParameters synapseFileParams = new ZipParameters();
            synapseFileParams.setFileNameInZip("main.py");
            zipFile.addStream(synapseFile, synapseFileParams);

            ZipParameters requirementsFileZipParams = new ZipParameters();
            requirementsFileZipParams.setFileNameInZip("requirements.txt");
            zipFile.addStream(requirementsFile, requirementsFileZipParams);
        } catch (IOException e) {
            throw new RuntimeException("Zip file creation failed - " + e.getMessage(), e);
        }
        return zipFile.getFile();
    }

    private void createAndUploadSourceZip(String functionName, InputStream synapseFile, InputStream requirementsFile, String fileName) {
        File zipFile = createSourceZip(functionName, synapseFile, requirementsFile);
        try {
            gcsCfFileManager.write(new FileInputStream(zipFile), fileName, getBucketName());
            zipFile.delete();
        } catch (FileNotFoundException e) {
            throw new RuntimeException("Zip file not found - " + e.getMessage());
        }
    }

    private void cloneZipFile(String functionName, String newFunctionName, String fileName, String newFileName) {
        try {
            gcsCfFileManager.copyFile(getBucketName(), fileName,
                    getBucketName(), newFileName);
        } catch (Exception e) {
            throw new RuntimeException("Cannot clone zip file - " + e.getMessage());
        }
    }

    private CloudFunctionsServiceClient getClient() {
        try {
            CloudFunctionsServiceSettings settings = CloudFunctionsServiceSettings.newBuilder()
                    .setCredentialsProvider(FixedCredentialsProvider.create(getCredentials())).build();
            return CloudFunctionsServiceClient.create(settings);
        } catch (IOException e) {
            throw new RuntimeException("CloudFunctionsServiceClient creation failed - " + e.getMessage());
        }
    }

    private GoogleCredentials getCredentials() {
        try {
            return GoogleCredentials
                    .fromStream(new ByteArrayInputStream(Base64.getDecoder().decode(appConfig.getCfDeployerCredentialsKey())));
        } catch (IOException e) {
            throw new RuntimeException("Google credentials creation failed - " + e.getMessage());
        }
    }

    private String getFilename(String functionName) {
        return "customsynapse/" + SyncariContext.getSyncariId() +"/" + functionName + ".zip";
    }

    private String getBucketName() {
        return appConfig.getGcsCfBucketName();
    }
}
