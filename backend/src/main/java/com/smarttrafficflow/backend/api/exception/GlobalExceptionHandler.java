package com.smarttrafficflow.backend.api.exception;

import com.smarttrafficflow.backend.api.dto.ApiErrorResponse;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final int MAX_ECHOED_VALUE_LENGTH = 80;

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

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleHandlerMethodValidation(HandlerMethodValidationException ex) {
        List<String> details = ex.getParameterValidationResults().stream()
                .flatMap(result -> describe(result).stream())
                .toList();
        log.warn("Parameter validation error: {}", details);
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Parâmetro inválido na requisição.", details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        List<String> details = ex.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .sorted()
                .toList();
        log.warn("Constraint violation: {}", details);
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Parâmetro inválido na requisição.", details);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("Parameter '{}' with invalid type or value", ex.getName());
        String message = "Parâmetro '" + ex.getName() + "' com valor inválido: '" + abbreviate(ex.getValue()) + "'.";
        return error(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", message, List.of());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex) {
        log.warn("Request body could not be read: {}", ex.getClass().getSimpleName());
        return error(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST",
                "Corpo da requisição ausente ou malformado. Envie um JSON válido.", List.of());
    }

    @ExceptionHandler(ServletRequestBindingException.class)
    public ResponseEntity<ApiErrorResponse> handleBinding(ServletRequestBindingException ex) {
        if (ex instanceof MissingServletRequestParameterException missing) {
            log.warn("Missing required parameter '{}'", missing.getParameterName());
            return error(HttpStatus.BAD_REQUEST, "MISSING_PARAMETER",
                    "Parâmetro obrigatório ausente: '" + missing.getParameterName() + "'.", List.of());
        }
        log.warn("Request binding error: {}", ex.getClass().getSimpleName());
        return error(HttpStatus.BAD_REQUEST, "MISSING_PARAMETER", "Requisição incompleta ou inválida.", List.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        log.warn("Method {} not supported", ex.getMethod());
        List<String> details = ex.getSupportedHttpMethods() == null
                ? List.of()
                : List.of("Métodos aceitos: " + ex.getSupportedHttpMethods().stream()
                        .map(HttpMethod::name)
                        .sorted()
                        .collect(Collectors.joining(", ")));
        return error(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED",
                "Método " + abbreviate(ex.getMethod()) + " não suportado para esta rota.", details, ex.getHeaders());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
        log.warn("Content type {} not supported", ex.getContentType());
        String sent = ex.getContentType() == null ? "ausente" : abbreviate(ex.getContentType().toString());
        return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE",
                "Tipo de conteúdo não suportado: " + sent + ". Use application/json.", List.of(), ex.getHeaders());
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
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Erro interno inesperado", List.of());
    }

    private static List<String> describe(ParameterValidationResult result) {
        String parameter = result.getMethodParameter().getParameterName();
        String prefix = parameter == null ? "" : parameter + ": ";
        return result.getResolvableErrors().stream()
                .map(MessageSourceResolvable::getDefaultMessage)
                .filter(Objects::nonNull)
                .map(message -> prefix + message)
                .toList();
    }

    private static String abbreviate(Object value) {
        String text = String.valueOf(value);
        return text.length() <= MAX_ECHOED_VALUE_LENGTH ? text : text.substring(0, MAX_ECHOED_VALUE_LENGTH) + "...";
    }

    private static ResponseEntity<ApiErrorResponse> error(
            HttpStatus status, String code, String message, List<String> details) {
        return error(status, code, message, details, HttpHeaders.EMPTY);
    }

    private static ResponseEntity<ApiErrorResponse> error(
            HttpStatus status, String code, String message, List<String> details, HttpHeaders headers) {
        ApiErrorResponse body = new ApiErrorResponse(code, message, details, OffsetDateTime.now());
        return ResponseEntity.status(status).headers(headers).body(body);
    }
}
