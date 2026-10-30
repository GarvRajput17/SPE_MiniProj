package com.calculator.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class CalculatorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testAddSuccess() throws Exception {
        mockMvc.perform(get("/api/v1/add")
                        .param("a", "2")
                        .param("b", "3")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operation", is("add")))
                .andExpect(jsonPath("$.a", is(2.0)))
                .andExpect(jsonPath("$.b", is(3.0)))
                .andExpect(jsonPath("$.result", is(5.0)));
    }

    @Test
    void testSubtractSuccess() throws Exception {
        mockMvc.perform(get("/api/v1/subtract")
                        .param("a", "10")
                        .param("b", "4")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operation", is("subtract")))
                .andExpect(jsonPath("$.a", is(10.0)))
                .andExpect(jsonPath("$.b", is(4.0)))
                .andExpect(jsonPath("$.result", is(6.0)));
    }

    @Test
    void testMultiplySuccess() throws Exception {
        mockMvc.perform(get("/api/v1/multiply")
                        .param("a", "2.5")
                        .param("b", "4")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operation", is("multiply")))
                .andExpect(jsonPath("$.a", is(2.5)))
                .andExpect(jsonPath("$.b", is(4.0)))
                .andExpect(jsonPath("$.result", is(10.0)));
    }

    @Test
    void testDivideSuccess() throws Exception {
        mockMvc.perform(get("/api/v1/divide")
                        .param("a", "7")
                        .param("b", "2")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operation", is("divide")))
                .andExpect(jsonPath("$.a", is(7.0)))
                .andExpect(jsonPath("$.b", is(2.0)))
                .andExpect(jsonPath("$.result", is(3.5)));
    }

    @Test
    void testDivideByZero() throws Exception {
        mockMvc.perform(get("/api/v1/divide")
                        .param("a", "10")
                        .param("b", "0")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Bad Request")))
                .andExpect(jsonPath("$.message", is("Division by zero is not allowed")))
                .andExpect(jsonPath("$.path", is("/api/v1/divide")));
    }

    @Test
    void testMissingParameterB() throws Exception {
        mockMvc.perform(get("/api/v1/subtract")
                        .param("a", "5")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Bad Request")))
                .andExpect(jsonPath("$.message", is("Missing required parameter 'b'")))
                .andExpect(jsonPath("$.path", is("/api/v1/subtract")));
    }

    @Test
    void testNonNumericParameter() throws Exception {
        mockMvc.perform(get("/api/v1/multiply")
                        .param("a", "abc")
                        .param("b", "2")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Bad Request")))
                .andExpect(jsonPath("$.message", is("Parameter 'a' must be a number, but was 'abc'")))
                .andExpect(jsonPath("$.path", is("/api/v1/multiply")));
    }

    @Test
    void testNanParameter() throws Exception {
        mockMvc.perform(get("/api/v1/add")
                        .param("a", "NaN")
                        .param("b", "1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Bad Request")))
                .andExpect(jsonPath("$.message", is("Parameter 'a' must be a finite number, but was 'NaN'")))
                .andExpect(jsonPath("$.path", is("/api/v1/add")));
    }

    @Test
    void testMultiplyOverflow() throws Exception {
        mockMvc.perform(get("/api/v1/multiply")
                        .param("a", "1e308")
                        .param("b", "10")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Bad Request")))
                .andExpect(jsonPath("$.message", is("Result of multiply is too large to represent as a finite number")))
                .andExpect(jsonPath("$.path", is("/api/v1/multiply")));
    }

    @Test
    void testActuatorHealth() throws Exception {
        mockMvc.perform(get("/actuator/health")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")));
    }
}
