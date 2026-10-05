package com.smarttrafficflow.backend.api.exception;

import com.smarttrafficflow.backend.api.dto.ApiErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.OffsetDateTime;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();
        log.warn("Validation error: {}", details);

        ApiErrorResponse response = new ApiErrorResponse(
                "VALIDATION_ERROR",
                "Falha de validacao da requisicao",
                details,
                OffsetDateTime.now()
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Invalid argument error: {}", ex.getMessage());
        ApiErrorResponse response = new ApiErrorResponse(
                "INVALID_ARGUMENT",
                ex.getMessage(),
                List.of(),
                OffsetDateTime.now()
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalState(IllegalStateException ex) {
        log.warn("Invalid state error: {}", ex.getMessage());
        ApiErrorResponse response = new ApiErrorResponse(
                "INVALID_STATE",
                ex.getMessage(),
                List.of(),
                OffsetDateTime.now()
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNoResourceFound(NoResourceFoundException ex) {
        log.warn("Route not found: {} /{}", ex.getHttpMethod(), ex.getResourcePath());
        ApiErrorResponse response = new ApiErrorResponse(
                "NOT_FOUND",
                "Recurso nao encontrado",
                List.of(),
                OffsetDateTime.now()
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGeneric(Exception ex) {
        log.error("Unexpected internal error", ex);
        ApiErrorResponse response = new ApiErrorResponse(
                "INTERNAL_ERROR",
                "Erro interno inesperado",
                List.of(ex.getClass().getSimpleName()),
                OffsetDateTime.now()
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
