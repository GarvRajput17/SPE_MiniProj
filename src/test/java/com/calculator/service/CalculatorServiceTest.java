package com.calculator.service;

import com.calculator.exception.CalculatorException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CalculatorServiceTest {

    private CalculatorService calculatorService;

    @BeforeEach
    void setUp() {
        calculatorService = new CalculatorService();
    }

    @Test
    void testAddPositiveNumbers() {
        assertEquals(5.0, calculatorService.add(2.0, 3.0));
    }

    @Test
    void testAddNegativeNumbers() {
        assertEquals(-5.0, calculatorService.add(-2.0, -3.0));
    }

    @Test
    void testSubtractPositiveNumbers() {
        assertEquals(6.0, calculatorService.subtract(10.0, 4.0));
    }

    @Test
    void testSubtractResultNegative() {
        assertEquals(-2.0, calculatorService.subtract(3.0, 5.0));
    }

    @Test
    void testMultiplyPositiveNumbers() {
        assertEquals(10.0, calculatorService.multiply(2.5, 4.0));
    }

    @Test
    void testMultiplyByZero() {
        assertEquals(0.0, calculatorService.multiply(5.0, 0.0));
    }

    @Test
    void testDividePositiveNumbers() {
        assertEquals(3.5, calculatorService.divide(7.0, 2.0));
    }

    @Test
    void testDivideByZeroThrowsException() {
        CalculatorException ex = assertThrows(CalculatorException.class, () -> calculatorService.divide(10.0, 0.0));
        assertEquals("Division by zero is not allowed", ex.getMessage());
    }

    @Test
    void testParseParameterValid() {
        assertEquals(42.5, calculatorService.parseParameter("42.5", "a"));
    }

    @Test
    void testParseParameterMissingNull() {
        CalculatorException ex = assertThrows(CalculatorException.class, () -> calculatorService.parseParameter(null, "b"));
        assertEquals("Missing required parameter 'b'", ex.getMessage());
    }

    @Test
    void testParseParameterMissingBlank() {
        CalculatorException ex = assertThrows(CalculatorException.class, () -> calculatorService.parseParameter("   ", "a"));
        assertEquals("Missing required parameter 'a'", ex.getMessage());
    }

    @Test
    void testParseParameterNonNumeric() {
        CalculatorException ex = assertThrows(CalculatorException.class, () -> calculatorService.parseParameter("abc", "a"));
        assertEquals("Parameter 'a' must be a number, but was 'abc'", ex.getMessage());
    }

    @Test
    void testParseParameterNaN() {
        CalculatorException ex = assertThrows(CalculatorException.class, () -> calculatorService.parseParameter("NaN", "a"));
        assertEquals("Parameter 'a' must be a finite number, but was 'NaN'", ex.getMessage());
    }

    @Test
    void testParseParameterInfinity() {
        CalculatorException ex = assertThrows(CalculatorException.class, () -> calculatorService.parseParameter("Infinity", "b"));
        assertEquals("Parameter 'b' must be a finite number, but was 'Infinity'", ex.getMessage());
    }

    @Test
    void testMultiplyOverflowThrowsException() {
        CalculatorException ex = assertThrows(CalculatorException.class, () -> calculatorService.multiply(1e308, 10.0));
        assertEquals("Result of multiply is too large to represent as a finite number", ex.getMessage());
    }

    @Test
    void testAddOverflowThrowsException() {
        CalculatorException ex = assertThrows(CalculatorException.class, () -> calculatorService.add(Double.MAX_VALUE, Double.MAX_VALUE));
        assertEquals("Result of add is too large to represent as a finite number", ex.getMessage());
    }
}
