package com.syncari.connector;

import com.google.common.collect.Lists;
import com.syncari.connector.data.AuthField;
import com.syncari.connector.data.AuthMetadata;
import com.syncari.connector.data.AuthType;
import com.syncari.connector.data.Result;
import com.syncari.connector.exception.*;
import com.syncari.utils.DateUtil;
import com.syncari.utils.Sleeper;
import com.syncari.utils.ThrowingSupplier;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.http.conn.ConnectTimeoutException;
import org.jooq.lambda.function.Function1;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.channels.UnresolvedAddressException;
import java.sql.SQLException;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeoutException;

@Slf4j
public class ConnectorHelper {

    private static final Set<HttpStatus> OTHER_RETRIABLES = Set.of(HttpStatus.BAD_GATEWAY,HttpStatus.GATEWAY_TIMEOUT,
            HttpStatus.INTERNAL_SERVER_ERROR,HttpStatus.SERVICE_UNAVAILABLE,HttpStatus.REQUEST_TIMEOUT,
            HttpStatus.BANDWIDTH_LIMIT_EXCEEDED);

    private static final Set<HttpStatus> LARGE_PAYLOAD_EXCEPTIONS = Set.of(HttpStatus.PAYLOAD_TOO_LARGE,HttpStatus.REQUEST_ENTITY_TOO_LARGE);

    public static final List<Class<? extends Exception>> RETRIEABLE_EXCEPTIONS = List.of(
        ConnectException.class, 
        TimeoutException.class, 
        ConnectTimeoutException.class,
        SocketTimeoutException.class
    );

    public static void withHttpErrorHandling(Runnable block){
        withHttpErrorHandling(() -> {
            block.run();
            return null;
        });
    }

    public static List<Result> doPayloadAdaptivePost(List<EntityData> data, Function1<List<EntityData>, List<Result>> payloadTransformer){
        List<Result> results = new ArrayList<>();
        try{
            return payloadTransformer.apply(data);
        } catch (NonRetriableException e){
            if(ErrorCodes.PAYLOAD_TOO_LARGE.name().equals(e.getErrorCode())){
                // divide the payload in half
                int size = data.size();
                if(size == 1){
                    // we cannot further breakdown a single record.
                    // If payload still exceeds for single record throw the error
                    log.error("Payload size limit exceeded for single record.");
                    throw new NonRetriableException(e.getErrorCode(), e.getMessage(), e.getStatusCode());
                }
                log.info("Payload size exceeded allowed limit. Dividing the records into half and retrying.");
                // partition the list into half and process individually
                var partitions = Lists.partition(data, size%2 == 0 ? size/2 : (size/2)+1);
                partitions.forEach(partition -> {
                    var partialResult = doPayloadAdaptivePost(partition, payloadTransformer);
                    results.addAll(partialResult);
                });
            } else {
                throw e;
            }
        }
        return results;
    }

    public static <T> T withBackoff(ThrowingSupplier<T> externalCall, int minBackoffMillis, int maxBackOffMillis,
                                    int retriesRemaining, Sleeper sleeper){
        return backoff(externalCall, minBackoffMillis, maxBackOffMillis,retriesRemaining, Optional.of(sleeper));
    }

    public static <T> T withBackoff(ThrowingSupplier<T> externalCall, int minBackoffMillis, int maxBackOffMillis, int retriesRemaining){
        return  backoff(externalCall, minBackoffMillis, maxBackOffMillis,retriesRemaining, Optional.empty());
    }

    private static int getRetryTime(RetriableException exception, int minBackoffMillis, int maxBackOffMillis) {
        if (exception.getCause() != null && exception.getCause() instanceof HttpClientErrorException.TooManyRequests) {
             String retryStr = ((HttpClientErrorException.TooManyRequests) exception.getCause()).getResponseHeaders().getFirst("Retry-After");
                if (retryStr != null) {
                    try {
                        int retryTime = Integer.parseInt(retryStr);
                        // add a jitter factor of upto 0.5 * retryTime
                        retryTime += (int) (retryTime * (Math.random() * (0.5)));
                        return retryTime * 1000;
                    } catch (Exception e) {}
                }
        }
        return minBackoffMillis + new Random().nextInt(maxBackOffMillis - minBackoffMillis);
    }

    public static <T> T backoff(ThrowingSupplier<T> externalCall, int minBackoffMillis, int maxBackOffMillis, int retriesRemaining, Optional<Sleeper> sleeper){
        int remaining = retriesRemaining;
        Exception lastException = null;
        while(remaining > 0){
            try{
                return externalCall.throwingGet();
            }catch(RetriableException rex){
                lastException = rex;
                long backoffMs = (sleeper.isPresent()) ? sleeper.get().getBackOffTime(minBackoffMillis, maxBackOffMillis) :
                        getRetryTime(rex, minBackoffMillis, maxBackOffMillis);
                log.error("Action failed with RetriableException {}. Retrying after {} ms. Retries Remaining:{}",
                        lastException.getMessage(), backoffMs, remaining);
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException e) {
                }
                remaining--;
            }catch(NonRetriableException | UnknownException ex){
                throw ex;
            }catch(Exception e){
                log.error(e.getMessage(),e);
                String msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
                throw new UnknownException(msg, e);
            }
        }
        log.error("Exceeded {} of retries with backoffs", retriesRemaining, lastException);
        // This is reached only for RetriableExceptions.
        if (lastException != null) {
            throw new RetriableException(ErrorCodes.TOO_MANY_REQUESTS.name(),
                    String.format("Exceeded %s of retries with backoffs and original exception %s",retriesRemaining, lastException.getMessage()),ErrorCodes.TOO_MANY_REQUESTS.name(),
                    lastException.getCause() != null ? (Exception) lastException.getCause() : lastException);
        } else {
            throw new RetriableException(ErrorCodes.TOO_MANY_REQUESTS,
                    String.format("Exceeded %s of retries with backoffs and original exception %s",retriesRemaining, ""),ErrorCodes.TOO_MANY_REQUESTS.name());
        }
    }

    public static <T> T backoffAndThrowNonRetriableException(ThrowingSupplier<T> externalCall, int minBackoffMillis, int maxBackOffMillis, int retriesRemaining, Optional<Sleeper> sleeper){
        int remaining = retriesRemaining;
        RetriableException lastException = null;
        while(remaining > 0){
            try{
                return externalCall.throwingGet();
            }catch(RetriableException rex){
                lastException = (RetriableException) handleException(minBackoffMillis, maxBackOffMillis, sleeper, remaining, rex);
                remaining--;
            }catch(NonRetriableException | UnknownException ex){
                throw ex;
            }catch(Exception e){
                return handleUnknownException(e);
            }
        }
        log.error("Exceeded {} of retries with backoffs", retriesRemaining, lastException);
        // This is reached only for RetriableExceptions.
        throw new NonRetriableException(lastException.getErrorCode(), 
            String.format("Exceeded %s of retries with backoffs and original exception %s",
                retriesRemaining, lastException==null?"":lastException.getMessage()),
            lastException.getStatusCode(),
            lastException);
    }

    public static <T> T backoffAndThrowOriginalException(ThrowingSupplier<T> externalCall, int minBackoffMillis, int maxBackOffMillis, int retriesRemaining, Optional<Sleeper> sleeper) throws Exception {
        int remaining = retriesRemaining;
        Exception lastException = null;
        while(remaining > 0){
            try{
                return externalCall.throwingGet();
            }catch(Exception e){
                lastException = handleException(minBackoffMillis, maxBackOffMillis, sleeper, remaining, e);
                remaining--;
            }
        }
        log.error("Exceeded {} of retries with backoffs", retriesRemaining, lastException);
        throw lastException;
    }

    private static <T> T handleUnknownException(Exception e) {
        log.error(e.getMessage(), e);
        String msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
        throw new UnknownException(msg, e);
    }

    private static Exception handleException(int minBackoffMillis, int maxBackOffMillis, Optional<Sleeper> sleeper, int remaining, Exception lastException ) {
        long backoffMs = (sleeper.isPresent()) ? sleeper.get().getBackOffTime(minBackoffMillis, maxBackOffMillis) :
                minBackoffMillis + new Random().nextInt(maxBackOffMillis - minBackoffMillis);
        log.error("Action failed with Exception {}. Retrying after {} ms. Retries Remaining:{}",
                lastException.getMessage(), backoffMs, remaining, lastException);
        try {
            Thread.sleep(backoffMs);
        } catch (InterruptedException e) {
        }
        return lastException;
    }

    public static <T> T withBackoff(ThrowingSupplier<T> externalCall){
        return withBackoff(externalCall, 5000,10000, 5);
    }

    public static <T> T withBackoffAndErrorHandling(ThrowingSupplier<T> externalCall){
        return withBackoff(()-> withHttpErrorHandling( externalCall), 5000,10000, 5);
    }

    public static <T> T withBackoffAndErrorHandling(ThrowingSupplier<T> externalCall, Sleeper sleeper){
        return withBackoff(()-> withHttpErrorHandling( externalCall), 5000,10000, 5, sleeper);
    }

    public static void withBackoff(Runnable externalCall){
        withBackoff(()->{externalCall.run();return null;}, 5000,10000, 5);
    }

    public static void withBackoffAndErrorHandling(Runnable externalCall){
        withBackoff(()->{externalCall.run();return null;}, 5000,10000, 5);
    }

    public static void withBackoffAndHttpErrorHandling(Runnable externalCall) {
        withBackoff(() -> { withHttpErrorHandling(externalCall); return null; }, 5000, 10000, 5);
    }

    public static <T> T withHttpErrorHandling(ThrowingSupplier<T> supplier) {
        try {
            return supplier.throwingGet();
        } catch (HttpClientErrorException.BadRequest error) {
            log.error(error.getResponseBodyAsString());
            throw new NonRetriableException(ErrorCodes.BAD_REQUEST, getErrorMessage(error) ,error.getStatusCode().toString(), error);
        } catch (HttpClientErrorException.Forbidden | HttpClientErrorException.Unauthorized error) {
            log.error(error.getResponseBodyAsString());
            throw new NonRetriableException(ErrorCodes.ACCESS_DENIED, getErrorMessage(error), error.getStatusCode().toString(), error);
        } catch (HttpClientErrorException.TooManyRequests error) {
            log.error(error.getResponseBodyAsString());
            throw new RetriableException(ErrorCodes.TOO_MANY_REQUESTS, getErrorMessage(error), error.getStatusCode().toString(), error);
        } catch (HttpClientErrorException.Gone error) {
            log.error(error.getResponseBodyAsString());
            throw new RetriableException(ErrorCodes.ENDPOINT_DOWN, getErrorMessage(error), error.getStatusCode().toString());
        } catch (HttpClientErrorException.NotFound error) {
            log.error(error.getResponseBodyAsString());
            throw new NonRetriableInternalException(ErrorCodes.BAD_ENDPOINT, getErrorMessage(error), error.getStatusCode().toString(), error);
        } catch (HttpClientErrorException error) {
            log.error(error.getResponseBodyAsString(), error);
            if(LARGE_PAYLOAD_EXCEPTIONS.contains(error.getStatusCode())){
                throw new NonRetriableException(ErrorCodes.PAYLOAD_TOO_LARGE, getErrorMessage(error), error.getStatusCode().toString(), error);
            } else if (OTHER_RETRIABLES.contains(error.getStatusCode())) {
                throw new RetriableException(ErrorCodes.NETWORK_ERROR, getErrorMessage(error), error.getStatusCode().toString());
            } else {
                throw new NonRetriableException(ErrorCodes.UNKNOWN_ERROR, getErrorMessage(error), error.getStatusCode().toString(), error);
            }
        } catch ( HttpServerErrorException.BadGateway | HttpServerErrorException.GatewayTimeout | HttpServerErrorException.InternalServerError | HttpServerErrorException.ServiceUnavailable error) {
        	log.error(error.getResponseBodyAsString(),error);
            throw new RetriableException(ErrorCodes.IO_ERROR, error.getResponseBodyAsString(), error.getStatusCode().toString());
        } catch ( ResourceAccessException  | IOException error) {
            log.error(error.getMessage(),error);
            Throwable rootCause = ExceptionUtils.getRootCause(error);
            if (rootCause instanceof UnknownHostException){
                throw new RetriableException(ErrorCodes.NETWORK_ERROR, error.getMessage(), ErrorCodes.IO_ERROR.toString());
            }
            if (rootCause instanceof UnresolvedAddressException) {
                throw new NonRetriableException(ErrorCodes.BAD_ENDPOINT, error.getMessage(), ErrorCodes.IO_ERROR.toString());
            }
            throw new RetriableException(ErrorCodes.IO_ERROR, error.getMessage(), ErrorCodes.IO_ERROR.toString());
        } catch(NonRetriableException | RetriableException e){
            log.error(e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error(e.getMessage());
            Throwable rootCause = ExceptionUtils.getRootCause(e);
            if (rootCause != null && RETRIEABLE_EXCEPTIONS.contains(rootCause.getClass())) {
                throw new RetriableException(ErrorCodes.IO_ERROR, e.getMessage(), ErrorCodes.IO_ERROR.toString());
            } else if (rootCause instanceof SQLException) {
                SQLException sqlEx = (SQLException) rootCause;
                String sqlState = sqlEx.getSQLState();
                log.error("SQLException - SQLState: {}, ErrorCode: {}", sqlState, sqlEx.getErrorCode());
                String message = e.getMessage() != null ? e.getMessage() : ExceptionUtils.getStackTrace(e);

                if("28000".equals(sqlState) ||    // PostgreSQL: invalid authorization specification
                   "28P01".equals(sqlState) ||    // PostgreSQL: password authentication failed
                   "08001".equals(sqlState) ||    // Standard: connection rejected
                   "28001".equals(sqlState)) {    // Standard: invalid authorization specification
                    throw new NonRetriableException(ErrorCodes.LOGIN_ERROR, message, sqlState, e);
                }
                throw new NonRetriableException(ErrorCodes.UNKNOWN_ERROR, message, sqlState, e);
            } else {
                String errorStackTrace = ExceptionUtils.getStackTrace(e);
                if (errorStackTrace.contains("Connection timed out") || errorStackTrace.contains("java.util.concurrent.TimeoutException")) {
                    // This block should ideally be removed. This is only for cases when the timeouts are burried inside nested exceptions.
                    throw new RetriableException(ErrorCodes.IO_ERROR, e.getMessage(), ErrorCodes.IO_ERROR.toString());
                }
            }
            String message = e.getMessage() != null ?  e.getMessage() : ExceptionUtils.getStackTrace(e);
            if(message != null && message.contains("SSL error")) {
            	throw new NonRetriableInternalException(ErrorCodes.UNKNOWN_ERROR.toString(), message, message, e);
            }
            throw new NonRetriableException(ErrorCodes.UNKNOWN_ERROR, message, message, e);
        }
    }
    
    private static String getErrorMessage(HttpClientErrorException e) {
    	String errorMsg = e.getResponseBodyAsString();
    	if(StringUtils.isBlank(errorMsg)) {
    		errorMsg = e.getMessage();
    	} else if(StringUtils.isBlank(errorMsg)) {
    		errorMsg = e.getStatusCode().toString();
    	}
    	return errorMsg;
    }

    public static void handleException(Exception e) {
        withHttpErrorHandling(() -> {throw e;});
    }

    public static <T> T withRateLimitHandling(String connectorId, ThrowingSupplier<T> supplier) {
        try {
            return supplier.throwingGet();
        } catch (Exception e) {
            if (e instanceof ConnectorException && ErrorCodes.TOO_MANY_REQUESTS.toString().equals(((ConnectorException) e).getErrorCode())) {
                throw new QuotaExceededException(ErrorCodes.TOO_MANY_REQUESTS.name(), ErrorCodes.TOO_MANY_REQUESTS.name(), 
                    ErrorCodes.TOO_MANY_REQUESTS.name(), connectorId, DateUtil.getSecondsToNextHour());
            }
            log.error("Failed due to ", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }
    
    public static AuthField getClientIdField() {
        AuthField clientId = new AuthField();
        clientId.setDataType("password");
        clientId.setName("clientId");
        clientId.setLabel("Client ID");
        clientId.setHelpSummary("Public identifier of your application.");
        return clientId;
    }

    public static AuthField getClientSecretField() {
        AuthField clientSecret = new AuthField();
        clientSecret.setDataType("password");
        clientSecret.setName("clientSecret");
        clientSecret.setLabel("Client Secret");
        clientSecret.setHelpSummary("It is a secret known only to the application and the application authorization server.");
        return clientSecret;
    }

    public static AuthField getTokenField() {
        AuthField token = new AuthField();
        token.setDataType("password");
        token.setName("token");
        token.setLabel("Token");
        return token;
    }

    public static AuthField getSupportedAuthPicker() {
        AuthField clientSecret = new AuthField();
        clientSecret.setDataType("picklist");
        clientSecret.setName("authType");
        clientSecret.setLabel("Authentication");
        return clientSecret;
    }

    public static AuthField getEndpointField() {
        AuthField endpoint = new AuthField();
        endpoint.setDataType("text");
        endpoint.setName("endpoint");
        endpoint.setLabel("Endpoint URL");
        return endpoint;
    }
    
    public static AuthMetadata getUserPwdToken() {
        AuthMetadata meta = getUserPwd();
        AuthField token = new AuthField();
        token.setDataType("password");
        token.setName("token");
        token.setLabel("Token");
        meta.setAuthType(AuthType.UserPasswordToken);
        meta.setLabel("User Password Token");
        meta.getFields().add(token);
        return meta;
    }
    
    public static AuthMetadata getUserPwd() {
        AuthField uname = new AuthField();
        uname.setDataType("text");
        uname.setName("userName");
        uname.setLabel("User Name");
        AuthField pwd = new AuthField();
        pwd.setDataType("password");
        pwd.setName("password");
        pwd.setLabel("Password");
        List<AuthField> fields = new ArrayList<AuthField>();
        fields.add(uname);
        fields.add(pwd);
        return new AuthMetadata(AuthType.UserPassword, fields, "User Password", "");
    }

    public static AuthMetadata getUserPwdClientIdSecret() {
        AuthMetadata meta = getUserPwd();
        meta.getFields().add(getClientIdField());
        meta.getFields().add(getClientSecretField());
        meta.setAuthType(AuthType.UserPasswordToken);
        meta.setLabel("User Password Connected App");
        return meta;
    }

    public static AuthMetadata getPrivateKey() {
        AuthField userName = new AuthField();
        userName.setDataType("text");
        userName.setName("userName");
        userName.setLabel("User Name");

        AuthField privateKey = new AuthField();
        privateKey.setDataType("textarea");
        privateKey.setName("privateKey");
        privateKey.setLabel("Private Key");
        privateKey.setHelpSummary("Private key");
        AuthField passphrase = new AuthField();
        passphrase.setDataType("text");
        passphrase.setRequired(false);
        passphrase.setName("passphrase");
        passphrase.setLabel("Passphrase");
        return new AuthMetadata(AuthType.PrivateKey, List.of(userName, privateKey, passphrase), "Private Key", "");
    }

    public static AuthMetadata getAzureBlobStoreAuth() {
        AuthField connectionString = new AuthField();
        connectionString.setDataType("password");
        connectionString.setName(Constants.AZURE_BLOB_STORE_CONNECTION_STRING);
        connectionString.setLabel("Connection String");
        List<AuthField> fields = new ArrayList<AuthField>();
        fields.add(connectionString);
        return new AuthMetadata(AuthType.UserPassword, fields, Constants.AZURE_BLOB_STORE_AUTH_TYPE_SAS_DISPLAY_NAME, "SAS Token");

    }
    
    public static AuthMetadata getS3Auth() {
        AuthField accessKey = new AuthField();
        accessKey.setDataType("password");
        accessKey.setName("accessToken");
        accessKey.setLabel("Access Key (optional)");
        accessKey.setRequired(false);
        accessKey.setHelpSummary("Leave blank to use the AWS workload IAM role.");
        AuthField secretKey = new AuthField();
        secretKey.setDataType("password");
        secretKey.setName("clientSecret");
        secretKey.setLabel("Secret Key (optional)");
        secretKey.setRequired(false);
        secretKey.setHelpSummary("Leave blank to use the AWS workload IAM role.");
        List<AuthField> fields = new ArrayList<AuthField>();
        fields.add(accessKey);
        fields.add(secretKey);
        return new AuthMetadata(AuthType.UserPassword, fields, "IAM Role or Access Keys", "Uses the workload IAM role when both key fields are blank.");
    }
    
    public static AuthMetadata getApiKey() {
        AuthField key = new AuthField();
        key.setDataType("password");
        key.setName("accessToken");
        key.setLabel("API Key");
        return new AuthMetadata(AuthType.ApiKey, List.of(key), "Api Key", "");
    }
    
    public static AuthMetadata getUserApiKey() {
        AuthField uname = new AuthField();
        uname.setDataType("text");
        uname.setName("userName");
        uname.setLabel("User Name");
        AuthField key = new AuthField();
        key.setDataType("password");
        key.setName("accessToken");
        key.setLabel("API Key");
        return new AuthMetadata(AuthType.ApiKey, List.of(uname, key), "Api Key", "");
    }
    
    public static AuthMetadata getSimpleOAuthType() {
        return new AuthMetadata(AuthType.SimpleOAuth, List.of(getClientIdField(), getClientSecretField()), "Simple OAuth", "");
    }
    
    public static AuthMetadata getAccessTokenOauthType() {
        return new AuthMetadata(AuthType.Oauth, List.of(getClientIdField(), getClientSecretField()), "OAuth", "");
    }
    
    public static AuthMetadata getTokenBasedOAuthType() {
        AuthField consumerKey = new AuthField();
        consumerKey.setDataType("password");
        consumerKey.setName("consumerKey");
        consumerKey.setHelpSummary("Consumer key of the application");
        consumerKey.setLabel("Consumer Key");
        AuthField consumerSecret = new AuthField();
        consumerSecret.setDataType("password");
        consumerSecret.setName("consumerSecret");
        consumerSecret.setHelpSummary("Consumer secret of the application");
        consumerSecret.setLabel("Consumer Secret");
        AuthField tokenId = new AuthField();
        tokenId.setDataType("password");
        tokenId.setName("tokenId");
        tokenId.setHelpSummary("Token Id is a unique identifier for the application, user and role");
        tokenId.setLabel("Token Id");
        AuthField tokenSecret = new AuthField();
        tokenSecret.setDataType("password");
        tokenSecret.setName("tokenSecret");
        tokenSecret.setHelpSummary("Token secret for the application, user and role");
        tokenSecret.setLabel("Token Secret");
        return new AuthMetadata(AuthType.NetSuiteTokenBasedAuthentication, List.of(consumerKey, consumerSecret, tokenId, tokenSecret), "Token Based Authentication", "");
    }

    /**
     * Zero based index
     * @param noOfColumns
     * @return
     */
    public static String getColumnAlphabet(int noOfColumns) {
        int dividend = noOfColumns;
        String columnName = "";
        int modulo;
        while (dividend > 0) {
            modulo = (dividend - 1) % 26;
            columnName = ((char)('A' + modulo)) + columnName;
            dividend = ((dividend - modulo) / 26);
        }
        return columnName;
    }
    
    public static Object get(Map<String, Object> nestedMap,String path){
        String[] parts = path.split("\\.");
        Map<String, Object> current= nestedMap;
        for(String part : parts){
            Object value = current.get(part);
            if(value==null) return Optional.empty();
            if(value instanceof Map){
                current = (Map<String, Object>) value;
            }else{
                return Optional.ofNullable(value);
            }
        }
        return Optional.empty();
    }

    public static Map<String, Object> getNestedMap(Map<String, Object> nestedMap,String path){
        String[] parts = path.split("\\.");
        Map<String, Object> current= nestedMap;
        for(String part : parts){
            Object value = current.get(part);
            if(value==null) return Map.of();
            if(value instanceof Map){
                current = (Map<String, Object>) value;
            }else{
                return Map.of();
            }
        }
        return current;
    }
    public static ZonedDateTime convert(String value) {
        ZonedDateTime zonedDateTime = DateUtil.convertDateTime(value);
        if(zonedDateTime == null) {
            LocalDate date = DateUtil.convertDate(value);
            if(date != null) {
                return ZonedDateTime.ofInstant(Instant.ofEpochSecond(date.toEpochSecond(LocalTime.MIDNIGHT,ZoneOffset.UTC)),ZoneOffset.UTC);
            }
        }
        return zonedDateTime;
    }

}
