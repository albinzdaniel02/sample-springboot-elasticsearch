package com.example.catalog.controller;

import com.example.catalog.service.SearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.net.ConnectException;
import java.net.SocketTimeoutException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SearchController.class)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SearchService searchService;

    @Test
    void testHandleDataAccessResourceFailureException() throws Exception {
        doThrow(new DataAccessResourceFailureException("Connection refused"))
                .when(searchService).search(any(), any(), any(), any(), anyInt(), anyInt());

        mockMvc.perform(get("/search")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("Service Unavailable"))
                .andExpect(jsonPath("$.message").value("Elasticsearch service is currently unavailable. Please try again later."));
    }

    @Test
    void testHandleConnectExceptionWrapped() throws Exception {
        doThrow(new RuntimeException("Connect failure wrapper", new ConnectException("Connection timed out")))
                .when(searchService).search(any(), any(), any(), any(), anyInt(), anyInt());

        mockMvc.perform(get("/search")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("Service Unavailable"))
                .andExpect(jsonPath("$.message").value("Elasticsearch service is currently unavailable. Please try again later."));
    }

    @Test
    void testHandleSocketTimeoutExceptionWrapped() throws Exception {
        doThrow(new RuntimeException("Timeout failure wrapper", new SocketTimeoutException("Read timed out")))
                .when(searchService).search(any(), any(), any(), any(), anyInt(), anyInt());

        mockMvc.perform(get("/search")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("Service Unavailable"))
                .andExpect(jsonPath("$.message").value("Elasticsearch service is currently unavailable. Please try again later."));
    }

    @Test
    void testHandleRuntimeExceptionGeneric() throws Exception {
        doThrow(new RuntimeException("Something went wrong"))
                .when(searchService).search(any(), any(), any(), any(), anyInt(), anyInt());

        mockMvc.perform(get("/search")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Internal Server Error"))
                .andExpect(jsonPath("$.message").value("Something went wrong"));
    }

    @Test
    void testHandleTypeMismatchException() throws Exception {
        mockMvc.perform(get("/search")
                        .param("page", "abc")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"));
    }
}