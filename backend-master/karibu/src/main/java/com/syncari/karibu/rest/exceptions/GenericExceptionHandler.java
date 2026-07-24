package com.syncari.karibu.rest.exceptions;

import com.mongodb.MongoNodeIsRecoveringException;
import com.mongodb.MongoSocketOpenException;
import com.syncari.core.SyncariContext;
import com.syncari.core.config.AppConfig;
import com.syncari.core.exceptions.AbacException;
import com.syncari.core.exceptions.SyncariValidationException;
import com.syncari.core.service.EmailService;
import com.syncari.karibu.rest.config.KaribuConstants;
import com.syncari.karibu.rest.response.ValidResponse;
import com.syncari.karibu.rest.util.ResponseUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.List;

@Slf4j
@ControllerAdvice
public class GenericExceptionHandler extends ResponseEntityExceptionHandler {
    @Autowired
    @Qualifier("defaultEmailService")
    EmailService emailService;
    @Autowired
    AppConfig appConfig;
    @Autowired
    ResponseUtils responseUtils;


    @ExceptionHandler(value = { NotFoundException.class })
    protected ResponseEntity<Object> handleNotFoundException(NotFoundException ex, WebRequest request) {
        log.error(ex.getMessage(), ex);
        ValidResponse response = responseUtils.populateErrorResponse(StringUtils.replace(ex.getMessage(), "Connector", "Synapse"));
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
        headers.set("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        return handleExceptionInternal(ex, response, headers, HttpStatus.NOT_FOUND, request);
    }


    @ExceptionHandler(value = { BadRequestException.class })
    protected ResponseEntity<Object> handleBadRequestException(BadRequestException ex, WebRequest request) {
        log.error(ex.getMessage(), ex);
        ValidResponse response = responseUtils.populateErrorResponse(ex.getMessage());
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
        headers.set("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        return handleExceptionInternal(ex, response, headers, HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(value = { MissingRequestHeaderException.class })
    protected ResponseEntity<Object> handleMissingRequestHeaderException(MissingRequestHeaderException ex, WebRequest request) {
        log.error(ex.getMessage(), ex);
        ValidResponse response = responseUtils.populateErrorResponse(ex.getMessage());
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
        headers.set("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        return handleExceptionInternal(ex, response, headers, HttpStatus.BAD_REQUEST, request);
    }


    @Override
    protected ResponseEntity<Object> handleMissingServletRequestPart(MissingServletRequestPartException ex, HttpHeaders headers,
                                                                     HttpStatus status, WebRequest request) {
        log.error(ex.getMessage(), ex);
        ValidResponse response = responseUtils.populateErrorResponse(ex.getMessage());
        HttpHeaders httpHeaders = new HttpHeaders();
        headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
        headers.set("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        return handleExceptionInternal(ex, response, httpHeaders, HttpStatus.BAD_REQUEST, request);
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(MissingServletRequestParameterException ex, HttpHeaders headers,
                                                                          HttpStatus status, WebRequest request) {
        log.error(ex.getMessage(), ex);
        ValidResponse response = responseUtils.populateErrorResponse(ex.getMessage());
        HttpHeaders httpHeaders = new HttpHeaders();
        headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
        headers.set("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        return handleExceptionInternal(ex, response, httpHeaders, HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(value = { UnauthorizedException.class })
    protected ResponseEntity<Object> handleUnauthorizedException(UnauthorizedException ex, WebRequest request) {
        log.error(ex.getMessage(), ex);
        ValidResponse response = responseUtils.populateErrorResponse(ex.getMessage());
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
        headers.set("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        return handleExceptionInternal(ex, response, headers, HttpStatus.UNAUTHORIZED, request);
    }
    
    @ExceptionHandler(value = { AbacException.class })
    protected ResponseEntity<Object> handleAbacException(AbacException ex, WebRequest request) {
        log.error(ex.getMessage(), ex);
        ValidResponse response = responseUtils.populateErrorResponse(ex.getMessage());
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
        headers.set("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        return handleExceptionInternal(ex, response, headers, HttpStatus.UNAUTHORIZED, request);
    }

    @ExceptionHandler(value = { BadCredentialsException.class })
    protected ResponseEntity<Object> handleBadCredentialsException(BadCredentialsException ex, WebRequest request) {
        log.error(ex.getMessage(), ex);
        ValidResponse response = responseUtils.populateErrorResponse(ex.getMessage());
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
        headers.set("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        return handleExceptionInternal(ex, response, headers, HttpStatus.UNAUTHORIZED, request);
    }

    @ExceptionHandler(value = { AccessDeniedException.class })
    protected ResponseEntity<Object> handleAccessDeniedException(AccessDeniedException ex, WebRequest request) {
        log.error(ex.getMessage(), ex);
        ValidResponse response = responseUtils.populateErrorResponse(ex.getMessage());
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
        headers.set("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        return handleExceptionInternal(ex, response, headers, HttpStatus.UNAUTHORIZED, request);
    }

    @ExceptionHandler(value = { TestConnectionError.class })
    protected ResponseEntity<Object> handleTestConnectionException(TestConnectionError ex, WebRequest request) {
        log.error(ex.getMessage(), ex);
        ValidResponse response = responseUtils.populateErrorResponse(StringUtils.replace(ex.getMessage(), "Connector", "Synapse"));
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
        headers.set("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        return handleExceptionInternal(ex, response, headers, HttpStatus.NOT_FOUND, request);
    }

    @ExceptionHandler(value = {MaxUploadSizeExceededException.class })
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex, WebRequest request) {
        log.error(ex.getMessage(), ex);
        ValidResponse response = responseUtils.populateErrorResponse(ex.getMessage());
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
        headers.set("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        return handleExceptionInternal(ex, response, headers, HttpStatus.BAD_REQUEST, request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers, HttpStatus status, WebRequest request) {
        log.error(ex.getMessage(), ex);
        BindingResult result = ex.getBindingResult();
        List<FieldError> fieldErrors = result.getFieldErrors();
        //TODO revisit to add multiple errors to the response
        /*
        List<String> responseErrors = new ArrayList<>();
        ValidResponse response = new ValidResponse();
        if (fieldErrors.size() > 1) {
            for (final FieldError error : ex.getBindingResult().getFieldErrors()) {
                responseErrors.add(error.getDefaultMessage());
            }
            response = responseUtils.populateErrorResponseList(responseErrors);
        } else {
            response = responseUtils.populateErrorResponse(ex.getBindingResult().getFieldErrors().get(0).getDefaultMessage());
        }
         */
        ValidResponse response = responseUtils.populateErrorResponse(ex.getBindingResult().getFieldErrors().get(0).getDefaultMessage());
        HttpHeaders httpHeaders = new HttpHeaders();
        headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
        headers.set("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        return handleExceptionInternal(ex, response, httpHeaders, HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(value = {IOException.class })
    protected ResponseEntity<Object> handleIOException(IOException ex, WebRequest request) {
        log.error("IOException occurred {} ",ex.getMessage(), ex);
        //ValidResponse response = responseUtils.populateErrorResponse(ex.getMessage());
        ValidResponse response = responseUtils.populateErrorResponse(KaribuConstants.INTERNAL_ERROR);
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
        headers.set("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        return handleExceptionInternal(ex, response, headers, HttpStatus.INTERNAL_SERVER_ERROR, request);
    }

    @ExceptionHandler(value = { ResourceAccessException.class, SocketTimeoutException.class, MongoNodeIsRecoveringException.class, MongoSocketOpenException.class})
    protected ResponseEntity<Object> handleSocketTimeout(RuntimeException ex, WebRequest request) {
        log.error("Exception occurred in karibu {}", ex.getMessage(), ex);
        ValidResponse response = responseUtils.populateErrorResponse(KaribuConstants.SERVICE_UNAVAILABLE);
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
        headers.set("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        if(isMailable(ex)) {
            String subject = "Karibu service unavailable error for " + SyncariContext.getSyncariId() + " in org " + SyncariContext.getOrganziation().getName();
            emailService.sendErrorEmail(List.of(), appConfig.getErrorEmail(), subject, ExceptionUtils.getStackTrace(ex));
        }
        return handleExceptionInternal(ex, response, headers, HttpStatus.SERVICE_UNAVAILABLE, request);
    }

    @ExceptionHandler(value = { Exception.class })
    protected ResponseEntity<Object> handleConflict(RuntimeException ex, WebRequest request) {
        log.error(ex.getMessage(), ex);
        //ValidResponse response = responseUtils.populateErrorResponse(ex.getMessage());
        ValidResponse response = responseUtils.populateErrorResponse(KaribuConstants.INTERNAL_ERROR);
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
        headers.set("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        if(isMailable(ex)) {
            String subject = "Karibu error for " + SyncariContext.getSyncariId() + " in org " + SyncariContext.getOrganziation().getName();
            emailService.sendErrorEmail(List.of(), appConfig.getErrorEmail(), subject, ExceptionUtils.getStackTrace(ex));
        }
        return handleExceptionInternal(ex, response, headers, HttpStatus.INTERNAL_SERVER_ERROR, request);
    }

    @ExceptionHandler(value = {HttpServerErrorException.InternalServerError.class})
    protected ResponseEntity<Object> handleInternalServerError(HttpServerErrorException.InternalServerError ex, WebRequest request) {
        log.error(ex.getMessage(), ex);
        final ValidResponse response = responseUtils.populateErrorResponse(ex.getResponseBodyAsString());

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
        headers.set("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        if (isMailable(ex)) {
            String subject = "Karibu error for " + SyncariContext.getSyncariId() + " in org " + SyncariContext.getOrganziation().getName();
            emailService.sendErrorEmail(List.of(), appConfig.getErrorEmail(), subject, ExceptionUtils.getStackTrace(ex));
        }
        return handleExceptionInternal(ex, response, headers, HttpStatus.INTERNAL_SERVER_ERROR, request);
    }

    private boolean isMailable(RuntimeException ex) {
        return ! (ex instanceof SyncariValidationException) && ! (ex instanceof ResourceNotFoundException) && ! (ex instanceof AccessDeniedException);
    }

}
