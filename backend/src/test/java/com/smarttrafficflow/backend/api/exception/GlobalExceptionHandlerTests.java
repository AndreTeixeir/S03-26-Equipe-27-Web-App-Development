package com.smarttrafficflow.backend.api.exception;

import com.smarttrafficflow.backend.api.dto.ApiErrorResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("GlobalExceptionHandler tests")
class GlobalExceptionHandlerTests {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("answers 400 with the violated constraints when a validated parameter is invalid")
    void answers400ForConstraintViolations() {
        Path path = mock(Path.class);
        when(path.toString()).thenReturn("findPage.page");
        @SuppressWarnings("unchecked")
        ConstraintViolation<Object> violation = mock(ConstraintViolation.class);
        when(violation.getPropertyPath()).thenReturn(path);
        when(violation.getMessage()).thenReturn("deve ser maior ou igual a 0");

        ResponseEntity<ApiErrorResponse> response =
                handler.handleConstraintViolation(new ConstraintViolationException("invalido", Set.of(violation)));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("VALIDATION_ERROR");
        assertThat(response.getBody().details()).containsExactly("findPage.page: deve ser maior ou igual a 0");
    }
}
