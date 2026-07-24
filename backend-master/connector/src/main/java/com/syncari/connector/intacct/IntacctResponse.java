package com.syncari.connector.intacct;

import com.syncari.connector.EntityData;
import com.syncari.connector.EntityPage;
import com.syncari.connector.data.EntitySchema;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamImplicit;
import lombok.Data;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.StringUtils;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Data
@Accessors(chain = true)
public class IntacctResponse {
    ResponseControl control;
    OperationResponse operation;
    List<Error> errormessage = List.of();

    public boolean hasErrors() {
        return !nullOrEmpty(errormessage) || !(operation.getResults() == null || operation.getResults().get(0) == null || nullOrEmpty(operation.getResults().get(0).getErrorMessage())) || !nullOrEmpty(operation.getErrormessage());
    }

    public static boolean nullOrEmpty(List<Error> errormessage) {
        return errormessage == null || errormessage.isEmpty();
    }

    public List<Error> getErrors() {
        List<Error> allErrors = new ArrayList<>();
        allErrors.addAll(errormessage != null ? errormessage : List.of());
        allErrors.addAll(operation!=null && operation.getErrormessage() != null ? operation.getErrormessage() : List.of());
        allErrors.addAll(operation!=null && (operation.getResults() != null && operation.getResults().get(0) != null) ? operation.getResults().get(0).getErrorMessage() : List.of());
        return allErrors;
    }

    public List<String> getErrorMessages() {
        return getErrors().stream().map(e -> e.toString()).collect(Collectors.toList());
    }

    public String getErrorMessage() {
        return getErrors().stream().map(e -> e.toString()).reduce((e1, e2) -> e1 + "." + e2).orElse("");
    }

    public String getErrorCode() {
        return getErrors().stream().map(e -> e.getErrorno()).reduce((e1, e2) -> e1 + "," + e2).orElse("");
    }
}

@Data
@Accessors(chain = true)
class ResponseControl {
    String status;
    String senderid;
    String controlid;
    boolean uniqueid;
    String dtdversion;

}

@Data
@Accessors(chain = true)
class OperationResponse {
    AuthenticationResponse authentication;
    @XStreamImplicit(itemFieldName = "result")
    List<Result> results;
    List<Error> errormessage = List.of();
}

@Data
@Accessors(chain = true)
class AuthenticationResponse {
    String status;
    String userid;
    String companyid;
    String locationid;
    ZonedDateTime sessiontimestamp;
    ZonedDateTime sessiontimeout;

    public long expiresIn() {
        return sessiontimeout.toEpochSecond() - sessiontimestamp.toEpochSecond();
    }
}

@Data
@Accessors(chain = true)
class Result {
    String status;
    String function;
    String controlid;
    //inspect response - has only apiName, label set
    List<EntitySchema> entities;
    //readByQuery
    InacctEntityPage records;
    //lookup - fully populated schema
    EntitySchema entity;
    //Create/Update
    EntityData entityData;
    //error messages
    @XStreamAlias("errormessage")
    List<Error> errorMessage = List.of();

    //getAPISession
    API api;
}

@Data
@Accessors(chain = true)
class API {
    String sessionid;
    String endpoint;
    String locationid;
}

@Data
@Accessors(chain = true)
class Error {
    String errorno;
    String description;
    String description2;
    String correction;

    public String toString() {
        StringBuilder errors = new StringBuilder();
        if (!StringUtils.isBlank(description)) {
            errors.append(description);
            errors.append(".");
        }
        if (!StringUtils.isBlank(description2)) {
            errors.append(description2);
            errors.append(".");
        }
        if (!StringUtils.isBlank(correction)) {
            errors.append(correction);
        }
        return errors.toString();
    }
}

