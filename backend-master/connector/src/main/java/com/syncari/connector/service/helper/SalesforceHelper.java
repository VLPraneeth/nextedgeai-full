package com.syncari.connector.service.helper;

import com.sforce.soap.partner.fault.ApiFault;
import com.sforce.soap.partner.fault.ExceptionCode;
import com.sforce.ws.ConnectionException;
import com.syncari.connector.ConnectorInfo;
import com.syncari.connector.exception.*;
import com.syncari.utils.DateUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class SalesforceHelper {

    public static void handleException(Exception e, ConnectorInfo config) {
        log.error("handleException {} and stacktrace {}", e.getMessage());
        log.debug("handleException {} and stacktrace {}", e.getMessage(), ExceptionUtils.getStackTrace(e));
        if (ConnectionException.class.isInstance(e) || ConnectionException.class.isAssignableFrom(e.getClass())) {
            String unknownCode = String.valueOf(HttpStatus.INTERNAL_SERVER_ERROR.ordinal());
            if (ApiFault.class.isAssignableFrom(e.getClass())) {
                ApiFault ex = (ApiFault) e;
                log.error("Exception code is {}", ex.getExceptionCode());
                String msg = ex.getMessage() != null ? ex.getMessage() : ex.getExceptionMessage() != null ? ex.getExceptionMessage(): ex.toString() != null ? ex.toString() : ex.getClass().getName();
                List<ExceptionCode> retriable = List.of(ExceptionCode.QUERY_TIMEOUT, ExceptionCode.UNABLE_TO_LOCK_ROW,
                        ExceptionCode.INVALID_SESSION_ID, ExceptionCode.SERVER_UNAVAILABLE);
                String code = ex.getExceptionCode() == null ? ErrorCodes.API_ERROR.name()
                        : ex.getExceptionCode().name();
                if (ex.getExceptionCode() != null && retriable.contains(ex.getExceptionCode())) {
                    throw new RetriableException(code, msg, unknownCode);
                } else if(ex.getExceptionCode() != null && ex.getExceptionCode() == ExceptionCode.INVALID_LOGIN || ex.getExceptionCode() == ExceptionCode.INVALID_OPERATION_WITH_EXPIRED_PASSWORD) {
                    log.error("Salesforce synapse auth exception - " + ex.getExceptionMessage());
                    log.debug("Salesforce synapse auth exception - " + ex.getExceptionMessage(), ex);
                    throw new AuthenticationException(config.getId(), config.getName(), ex.getExceptionMessage(), ExceptionCode.INVALID_LOGIN.name());
                } else if(ex.getExceptionCode() != null && ex.getExceptionCode() == ExceptionCode.REQUEST_LIMIT_EXCEEDED) {
                    throw new QuotaExceededException(ErrorCodes.TOO_MANY_REQUESTS.name(),
                            ErrorCodes.TOO_MANY_REQUESTS.name(), ErrorCodes.TOO_MANY_REQUESTS.name(),
                            config.getId(), DateUtil.getSecondsToNextHour());
                } else {
                    throw new NonRetriableException(code, msg, String.valueOf(HttpStatus.BAD_REQUEST.ordinal()));
                }
            } else {
                log.error(e.getMessage());
                throw new RetriableException(ErrorCodes.CONNECTION_ERROR.name(), e.getMessage(), unknownCode);
            }
        } else {
            log.error(e.getMessage());
            throw new UnknownException(e.getMessage());
        }
    }

    public static String getApiFaultMessage(Exception e) {
        ApiFault ex = (ApiFault) e;
        log.error("Exception code is {}", ex.getExceptionCode());
        return ex.getMessage() != null ? ex.getMessage() : ex.getExceptionMessage() != null ? ex.getExceptionMessage(): ex.toString() != null ? ex.toString() : ex.getClass().getName();
    }

}
