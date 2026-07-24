package com.syncari.utils.file;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.http.client.utils.URIBuilder;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;

@Slf4j
public class AzureBlobStoreFileManager implements FileManager , com.syncari.utils.Storage {

    private static final String END_POINT_FORMAT = "https://%s.dfs.core.windows.net";
    private static final Logger log = LoggerFactory.getLogger(AzureBlobStoreFileManager.class);
    private static final Integer BATCH_SIZE = 10000;
    private static final Set<String> ALLOWED_HTTP_METHODS = new HashSet<>(Arrays.asList("GET", "PUT", "PATCH"));

    private String endPoint;
    private String connectionString;
    private Map<String, String> connectionMap;
    private String directoryName;
    private String containerName;
    private String clientId;
    private String clientSecret;
    private String authCode;
    private Boolean isOAuth;

    public static String getAccessToken(String endPoint, String code, String clientId, String clientSecret){
        String url = endPoint.endsWith("/")?endPoint + "oauth2/v2.0/token": endPoint + "/oauth2/v2.0/token";
        String payload = String.format("grant_type=client_credentials&code=%s&client_id=%s&client_secret=%s&scope=https://storage.azure.com/.default",
                code, clientId, clientSecret);
        try{
            HttpRequest request = HttpRequest.newBuilder()
                        .uri(new URI(url))
                        .POST(HttpRequest.BodyPublishers.ofString(payload))
                        .build();
            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            ObjectMapper mapper = new ObjectMapper();
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            OAuthResponse resp = mapper.readValue(response.body(), new TypeReference<OAuthResponse>() {
                });
            return resp.getAccess_token();
        } catch (Exception e) {
            log.error("Error parsing fetching oauth access token. Error : {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private AzureBlobStoreFileManager(String storageAccountName, String directoryName, String containerName) {
        this.directoryName = directoryName;
        this.containerName = containerName;
        endPoint = String.format(END_POINT_FORMAT, storageAccountName);
    }

    private Map<String, String> getConnectionStringMap(String conn){
        Map<String, String> connectionStringMap = new HashMap<>();
        List<String> parts = Arrays.asList(conn.split("&"));
        for (String part: parts){
            List<String> elements = Arrays.asList(part.split("="));
            if (elements.size() != 2){
                log.error("Invalid connection String");
                throw new RuntimeException("Invalid connection String");
            }
            connectionStringMap.put(elements.get(0), elements.get(1));
        }
        return connectionStringMap;
    }

    public AzureBlobStoreFileManager(String storageAccountName, String connectionString, String directoryName, String containerName) {
        this(storageAccountName, directoryName, containerName);
        this.connectionString = connectionString;
        this.connectionMap = getConnectionStringMap(connectionString);
        isOAuth = false;
    }

    public AzureBlobStoreFileManager(String storageAccountName, String directoryName, String containerName, String clientId, String clientSecret, String endPoint, String authCode) {
        this(storageAccountName, directoryName, containerName);
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.authCode = getAccessToken(endPoint, authCode, clientId, clientSecret);
        isOAuth = true;
    }

    private HttpRequest buildRequest(String url, String method, String body, Map<String, String> queryParams, Map<String, String> headers){
        if (ALLOWED_HTTP_METHODS.contains(method.toLowerCase())){
            log.error("Invalid HTTP Method : "+method);
            throw new RuntimeException("Invalid HTTP Method : "+method);
        }
        HttpRequest.Builder request = HttpRequest.newBuilder();

        try {
            List<String> qParams = new ArrayList<>();
            URIBuilder uri = new URIBuilder(url);
            if (!queryParams.isEmpty()){
                for (Map.Entry<String, String> entry : queryParams.entrySet()) {
                    qParams.add(entry.getKey()+"="+entry.getValue());
                }
            }
            if (!isOAuth)
                qParams.add(connectionString);
            url += "?"+StringUtils.join(qParams,"&");
            request.uri(new URI(url));
        } catch (Exception e){
            log.error("Error building uri. Error : {}", e.getMessage());
            throw new RuntimeException(e);
        }

        if (!headers.isEmpty()){
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                request.setHeader(entry.getKey(), entry.getValue());
            }
        }
        if (isOAuth){
            request.setHeader("Authorization", "Bearer "+authCode);
        }

        if(method.equalsIgnoreCase("put")) {
            request.PUT(HttpRequest.BodyPublishers.ofString(body));
        } else if(method.equalsIgnoreCase("patch")) {
            request.method("PATCH", HttpRequest.BodyPublishers.ofString(body));
        } else {
            request.GET();
        }
        HttpRequest req = request.build();
        return req;
    }



    private HttpResponse<String> listFromDirectory(String directoryPath) {
        String baseURL = endPoint+"/"+containerName;
        Map<String, String> queries = new HashMap<>();
        queries.put("resource", "filesystem");
        queries.put("recursive", "false");
        queries.put("directory", directoryPath);

        Map<String, String> headers = new HashMap<>();
        headers.put("x-ms-version", "2023-11-03");

        HttpRequest req = buildRequest(baseURL, "get", "", queries, headers);
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.error("Error making List directories api call for endpoint {}, container {}, directory {}. Status Code {}, Response {}",
                        endPoint, containerName, directoryPath, response.statusCode(), response.body());
                throw new RuntimeException(String.format("Failed to make API call to List directories for endpoint %s, container %s, directory %s. Status Code : %s",endPoint, containerName, directoryPath, response.statusCode()));
            }
            log.info("List directories api call for endpoint {}, container {}, directory {} succeeded",endPoint, containerName, directoryPath);
            return response;
        } catch (ConnectException e){
            String errorMsg = String.format("Error connecting to Azure for endpoint %s, container %s",endPoint, containerName);
            log.error(errorMsg);
            throw new RuntimeException(errorMsg);
        } catch (Exception e){
            log.error("Error making List directories api call for endpoint {}, container {}, directory {}. Error : {}",endPoint, containerName, directoryPath, e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public List<String> getFilesFromDirectory(String directory)  {
        List<String> files = new ArrayList<>();
        try {
            HttpResponse<String> data = listFromDirectory(directory);
            ObjectMapper mapper = new ObjectMapper();
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            ListDirectoryResponse resp = mapper.readValue(data.body(), new TypeReference<ListDirectoryResponse>() {
            });
            for (Path p: resp.getPaths())
                if (p.getIsDirectory() != null || !Boolean.parseBoolean(p.getIsDirectory()))
                    files.add(p.getName());
            return files;
        } catch (Exception e){
            throw new RuntimeException(e);
        }

    }

    private List<String> getDirectoriesFromDirectory(String directory) {
        List<String> directories = new ArrayList<>();
        try {
            HttpResponse<String> data = listFromDirectory(directory);
            ObjectMapper mapper = new ObjectMapper();
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            ListDirectoryResponse resp = mapper.readValue(data.body(), new TypeReference<ListDirectoryResponse>() {
            });
            for (Path p: resp.getPaths()) {
                if ((p.getIsDirectory() != null && Boolean.parseBoolean(p.getIsDirectory())) && !p.getName().equals(directory))
                    directories.add(p.getName());
            }
            return directories;
        } catch (Exception e){
            log.error("Error occurred while fetching subdirectories from directory {}, container {}, endpoint {}. Error : {}", directory, containerName, endPoint, e.getMessage());
            throw new RuntimeException(e.getMessage());
        }
    }

    private String fetchCsvFromDirectory(String directory){
        List<String> files = getFilesFromDirectory(directory);
        for (String file: files){
            if (file.endsWith(".csv"))
                return file;
        }
        log.info("No csv files found in directory : {}",directory);
        return "";
    }

    private String downloadFile(String filePath){
        String DOWNLOAD_FILE_URL_FORMAT = "%s/%s/%s";
        String url = String.format(DOWNLOAD_FILE_URL_FORMAT, endPoint, containerName,filePath);
        Map<String, String> headers = new HashMap<>();
        headers.put("x-ms-version", "2023-11-03");
        url += "?"+connectionString;

        try {
            HttpRequest req = buildRequest(url, "get", "", Map.of(), headers);
            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.error("Error downloading file for endpoint {}, container {}, filepath {}. Status Code {}, Response {}",
                        endPoint, containerName, filePath, response.statusCode(), response.body());
                throw new RuntimeException(String.format("Failed to download file %s for endpoint %s, container %s. Status Code : %s",filePath, endPoint, containerName, response.statusCode()));
            }
            log.info("File download api call for endpoint {}, container {}, file {} succeeded",endPoint, containerName, filePath);
            return response.body();

        } catch (Exception e){
            log.error("Error Downloading file {} endpoint {}, container {}. Error : {}", filePath, endPoint, containerName, e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public Map<String, List<String>> getSchemaFromDirectory(String directory) {
        Map<String, List<String>> entities = new HashMap<>();
        List<String> subDirectoryList = getDirectoriesFromDirectory(directory);
        for (String dir: subDirectoryList){
            try {
                String csvFile = fetchCsvFromDirectory(dir);
                if (StringUtils.isBlank(csvFile)) {
                    continue;
                }
                String data = downloadFile(csvFile);
                List<String> columns = getColumns(data);
                if (!columns.isEmpty()){
                    String[] fileNameParts = csvFile.split("/");
                    String entityName = fileNameParts[fileNameParts.length-2];
                    entities.put(entityName, columns);
                }
            } catch (Exception e){
                log.error("Error while parsing directory {}. Error : {}", dir, e.getMessage());
            }
        }
        return entities;
    }

    private List<String> getColumns(String data) {
        try (CSVParser csv = CSVParser.parse(data, CSVFormat.DEFAULT.withFirstRecordAsHeader().withTrim().withQuote(null))) {
            return csv.getHeaderNames();
        } catch (Exception e) {
            log.info("Error reading csv file. Error : {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private void createFilePath(String fileName){
        String url = endPoint+"/"+containerName+"/"+fileName;

        Map<String, String> queries = new HashMap<>();
        queries.put("resource", "file");

        Map<String, String> headers = new HashMap<>();
        headers.put("x-ms-version", "2023-11-03");
        headers.put("x-ms-content-type", "text/csv");

        try {
            HttpRequest req = buildRequest(url, "put", "", queries, headers);
            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 201){
                log.info("created file path {} in container {}", fileName, containerName);
            } else {
                String errMsg = String.format("Error creating file path %s in container %s. statuscode : %s, message : %s",
                        fileName, containerName, response.statusCode(), response.body());
                log.error(errMsg);
                throw new RuntimeException(errMsg);
            }
        } catch (Exception e) {
            log.error("Error occured while creating file path {} in container {}. error : {}",
                    fileName, containerName, e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private int appendToFilePath(InputStream fileStream, String fileName){

        String url = endPoint+"/"+containerName+"/"+fileName;
        Map<String, String> headers = new HashMap<>();
        headers.put("x-ms-version", "2023-11-03");

        try {
            String payload = new String(fileStream.readAllBytes());
            int curr = 0;
            int step = BATCH_SIZE;
            int end = payload.length();
            int position = 0;
            while(curr < end){

                String currBatch = payload.substring(curr, Integer.min(curr+step, end));
                Map<String, String> queries = new HashMap<>();
                queries.put("action", "append");
                queries.put("position", String.valueOf(position));
                HttpRequest req = buildRequest(url, "patch", currBatch, queries, headers);
                HttpClient client = HttpClient.newHttpClient();
                HttpResponse<String> response = client.send(req, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 202){
                    log.info("Appended data file path {} in container {}. Position : {}. Processed {} of {}", fileName, containerName, position, curr, payload.length());
                } else {
                    String errMsg = String.format("Error Appending data to file path %s in container %s. statuscode : %s, message : %s",
                            fileName, containerName, response.statusCode(), response.body());
                    log.error(errMsg);
                    throw new RuntimeException(errMsg);
                }
                curr = curr+step;
                position += currBatch.length();
            }
            return position;
        } catch (Exception e) {
            log.error("Error occurred while appending data to file path {} in container {}. error : {}",
                    fileName, containerName, e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private void flushFilePath(int position, String fileName){
        String url = endPoint+"/"+containerName+"/"+fileName;

        Map<String, String> queries = new HashMap<>();
        queries.put("action", "flush");
        queries.put("close", "true");
        queries.put("position", String.valueOf(position));

        Map<String, String> headers = new HashMap<>();
        headers.put("x-ms-version", "2023-11-03");
        headers.put("x-ms-content-type", "text/csv");

        try {
            HttpRequest req = buildRequest(url, "patch", "", queries, headers);
            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200){
                log.info("Flush file path {} in container {} succeeded", fileName, containerName);
            } else {
                String errMsg = String.format("Error flushing file path %s in container %s. statuscode : %s, message : %s",
                fileName, containerName, response.statusCode(), response.body());
                log.error(errMsg);
                throw new Exception(errMsg);
            }
        } catch (Exception e) {
            log.error("Error occurred while flushing data to file path {} in container {}. error : {}",
                    fileName, containerName, e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public Boolean isDirectoryExists(String directoryPath) {
        try {
            listFromDirectory(directoryPath);
            return true;
        } catch (Exception e) {
            log.error("Error with locating path. Error : {}", e.getMessage());
            return false;
        }
    }

    public String write(InputStream fileStream, String uri) {
        String[] fileParts = uri.split("/");
        String subdirName = fileParts[fileParts.length-2];
        String fileName = fileParts[fileParts.length-1];
        String dirPath = directoryName+"/"+subdirName;
        String filePath = directoryName+"/"+subdirName +"/"+fileName;
        if (!isDirectoryExists(dirPath))
            throw new RuntimeException("Directory "+uri+" doesn't exist");
        createFilePath(filePath);
        int position = appendToFilePath(fileStream, filePath);
        flushFilePath(position, filePath);
        return "";
    }

    @Override
    public String writeToFolder(InputStream fileStream, String fileName, String folderName, String bucketName) {
        throw new RuntimeException("Not yet implemented");
    }

    @Override
    public InputStream read(String uri) {
        throw new RuntimeException("Not yet implemented");
    }

    @Override
    public void delete(String uri) {
        throw new RuntimeException("Not yet implemented");
    }

    @Override
    public void delete(String fileName, String bucketName) {
        throw new RuntimeException("Not yet implemented");
    }

    @Override
    public long lastModified(String uri) {
        throw new RuntimeException("Not yet implemented");
    }

    @Override
    public String uploadFile(InputStream fileStream, String fileName) throws IOException {
        return write(fileStream, fileName);
    }

    @Override
    public InputStream readFile(String fileName) throws IOException {
        throw new RuntimeException("Not yet implemented");
    }

    @Override
    public void deleteFile(String fileName) throws IOException {
        throw new RuntimeException("Not yet implemented");
    }

    @Override
    public void createDirectory(String name) throws IOException {
        throw new RuntimeException("Not yet implemented");
    }

}


class ListDirectoryResponse{
    public List<Path> paths;

    public List<Path> getPaths() {
        return paths;
    }

    public void setPaths(List<Path> paths) {
        this.paths = paths;
    }

}


class Path{
    String name;
    String isDirectory;
    String lastModified;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIsDirectory() {
        return isDirectory;
    }

    public void setIsDirectory(String isDirectory) {
        this.isDirectory = isDirectory;
    }

    public String getLastModified() {
        return lastModified;
    }

    public void setLastModified(String lastModified) {
        this.lastModified = lastModified;
    }

}

class OAuthResponse{
    public String getToken_type() {
        return token_type;
    }

    public void setToken_type(String token_type) {
        this.token_type = token_type;
    }

    public String getExpires_in() {
        return expires_in;
    }

    public void setExpires_in(String expires_in) {
        this.expires_in = expires_in;
    }

    public String getExt_expires_in() {
        return ext_expires_in;
    }

    public void setExt_expires_in(String ext_expires_in) {
        this.ext_expires_in = ext_expires_in;
    }

    public String getAccess_token() {
        return access_token;
    }

    public void setAccess_token(String access_token) {
        this.access_token = access_token;
    }

    String token_type;
    String expires_in;
    String ext_expires_in;
    String access_token;

}
