package com.syncari.api.rest.controllers.exceptions;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.syncari.core.exception.NotFoundException;
import com.syncari.utils.I18n;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.syncari.core.SyncariContext;
import com.syncari.core.config.AppConfig;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.service.EmailService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@ControllerAdvice
public class GenericExceptionHandler extends ResponseEntityExceptionHandler {
    @Autowired
    @Qualifier("defaultEmailService")
    EmailService emailService;
    @Autowired
    AppConfig appConfig;

    @ExceptionHandler(value = { BadRequestException.class })
    protected ResponseEntity<Object> handleBadRequestException(BadRequestException ex, WebRequest request) {
        log.error(ex.getMessage(), ex);
        Map<String, Object> response = new HashMap<String, Object>();
        response.put("timestamp", Instant.now());
        response.put("status", HttpStatus.BAD_REQUEST.value());
        response.put("error", HttpStatus.BAD_REQUEST.getReasonPhrase());
        response.put("message", ex.getMessage());
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
        headers.set("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        return handleExceptionInternal(ex, response, headers, HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(value = { UnauthorizedException.class })
    protected ResponseEntity<Object> handleUnauthorizedException(UnauthorizedException ex, WebRequest request) {
        log.error(ex.getMessage(), ex);
        Map<String, Object> response = new HashMap<String, Object>();
        response.put("timestamp", Instant.now());
        response.put("status", HttpStatus.UNAUTHORIZED.value());
        response.put("error", HttpStatus.UNAUTHORIZED.getReasonPhrase());
        response.put("message", ex.getMessage());
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
        headers.set("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        return handleExceptionInternal(ex, response, headers, HttpStatus.UNAUTHORIZED, request);
    }
    
    @ExceptionHandler(value = { AccessDeniedException.class })
    protected ResponseEntity<Object> handleAccessDeniedException(RuntimeException ex, WebRequest request) {
        log.error(ex.getMessage(), ex);
        Map<String, Object> response = new HashMap<String, Object>();
        response.put("timestamp", Instant.now());
        response.put("status", HttpStatus.FORBIDDEN.value());
        response.put("error", HttpStatus.FORBIDDEN.getReasonPhrase());
        response.put("message", ex.getMessage());
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
        headers.set("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        if(isMailable(ex)) {
            String subject = "Arcade error for " + SyncariContext.getSyncariId() + " in org " + SyncariContext.getOrganziation().getName();
            emailService.sendErrorEmail(List.of(), appConfig.getErrorEmail(), subject, ExceptionUtils.getStackTrace(ex));
        }
        return handleExceptionInternal(ex, response, headers, HttpStatus.FORBIDDEN, request);
    }
    

    @ExceptionHandler(value = { MaxUploadSizeExceededException.class })
    protected ResponseEntity<Object> handleMaxUploadConflict(RuntimeException ex, WebRequest request) {
        log.error(ex.getMessage(), ex);
        SyncariValidationException exception = new SyncariValidationException(String.format(I18n.i18n("max_upload_refdata_message"),"200MB"));
        return this.handleConflict(exception,request);
    }

    @ExceptionHandler(value = { NotFoundException.class })
    protected ResponseEntity<Object> handleNotFoundException(RuntimeException ex, WebRequest request) {
        log.error(ex.getMessage(), ex);
        Map<String, Object> response = new HashMap<String, Object>();
        response.put("timestamp", Instant.now());
        response.put("status", HttpStatus.NOT_FOUND.value());
        response.put("error", HttpStatus.NOT_FOUND.getReasonPhrase());
        response.put("message", ex.getMessage());
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
        headers.set("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        if(isMailable(ex)) {
            String subject = "Arcade error for " + SyncariContext.getSyncariId() + " in org " + SyncariContext.getOrganziation().getName();
            emailService.sendErrorEmail(List.of(), appConfig.getErrorEmail(), subject, ExceptionUtils.getStackTrace(ex));
        }
        return handleExceptionInternal(ex, response, headers, HttpStatus.NOT_FOUND, request);
    }


    @ExceptionHandler(value = { Exception.class })
    protected ResponseEntity<Object> handleConflict(RuntimeException ex, WebRequest request) {
        log.error(ex.getMessage(), ex);
        Map<String, Object> response = new HashMap<String, Object>();
        response.put("timestamp", Instant.now());
        response.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        response.put("error", HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase());
        response.put("message", ex.getMessage());
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
        headers.set("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        if(isMailable(ex)) {
            String subject = "Arcade error for " + SyncariContext.getSyncariId() + " in org " + SyncariContext.getOrganziation().getName();
            emailService.sendErrorEmail(List.of(), appConfig.getErrorEmail(), subject, ExceptionUtils.getStackTrace(ex));
        }
        return handleExceptionInternal(ex, response, headers, HttpStatus.INTERNAL_SERVER_ERROR, request);
    }

    private boolean isMailable(RuntimeException ex) {
        return ! (ex instanceof SyncariValidationException) && ! (ex instanceof ResourceNotFoundException) && ! (ex instanceof AccessDeniedException);
    }

}
