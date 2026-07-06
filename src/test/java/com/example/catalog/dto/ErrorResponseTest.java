package com.example.catalog.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorResponseTest {

    @Test
    void testErrorResponseBuilderAndGetters() {
        ErrorResponse errorResponse = ErrorResponse.builder()
                .error("Bad Request")
                .message("minPrice cannot be greater than maxPrice")
                .build();

        assertThat(errorResponse.getError()).isEqualTo("Bad Request");
        assertThat(errorResponse.getMessage()).isEqualTo("minPrice cannot be greater than maxPrice");
    }

    @Test
    void testNoArgsConstructorAndSetters() {
        ErrorResponse errorResponse = new ErrorResponse();
        errorResponse.setError("Internal Error");
        errorResponse.setMessage("An unexpected error occurred");

        assertThat(errorResponse.getError()).isEqualTo("Internal Error");
        assertThat(errorResponse.getMessage()).isEqualTo("An unexpected error occurred");
    }
}
