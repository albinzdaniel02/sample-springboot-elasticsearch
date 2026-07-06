package com.example.catalog.controller;

import com.example.catalog.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ValidationException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({
            ConstraintViolationException.class,
            MethodArgumentNotValidException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            BindException.class,
            HandlerMethodValidationException.class,
            ValidationException.class,
            IllegalArgumentException.class
    })
    public ResponseEntity<ErrorResponse> handleValidationExceptions(Exception ex) {
        log.error("Validation error: {}", ex.getMessage(), ex);
        ErrorResponse errorResponse = ErrorResponse.builder()
                .error("Bad Request")
                .message(ex.getMessage())
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler({
            org.springframework.dao.DataAccessResourceFailureException.class,
            co.elastic.clients.transport.TransportException.class,
            ConnectException.class,
            SocketTimeoutException.class
    })
    public ResponseEntity<ErrorResponse> handleConnectionExceptions(Exception ex) {
        log.error("Elasticsearch connection/timeout error: {}", ex.getMessage(), ex);
        ErrorResponse errorResponse = ErrorResponse.builder()
                .error("Service Unavailable")
                .message("Elasticsearch service is currently unavailable. Please try again later.")
                .build();
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorResponse);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleAllOtherExceptions(Exception ex) {
        log.error("Internal server error: {}", ex.getMessage(), ex);

        // Check cause chain for connection exceptions
        Throwable cause = ex;
        while (cause != null) {
            if (cause instanceof ConnectException ||
                    cause instanceof SocketTimeoutException ||
                    cause instanceof co.elastic.clients.transport.TransportException ||
                    cause.getClass().getName().contains("DataAccessResourceFailureException")) {
                return handleConnectionExceptions((Exception) cause);
            }
            cause = cause.getCause();
        }

        ErrorResponse errorResponse = ErrorResponse.builder()
                .error("Internal Server Error")
                .message(ex.getMessage())
                .build();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
}