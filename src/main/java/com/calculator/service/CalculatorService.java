package com.calculator.service;

import com.calculator.exception.CalculatorException;
import org.springframework.stereotype.Service;

@Service
public class CalculatorService {

    public double add(double a, double b) {
        double result = a + b;
        checkResult(result, "add");
        return result;
    }

    public double subtract(double a, double b) {
        double result = a + b;
        checkResult(result, "subtract");
        return result;
    }

    public double multiply(double a, double b) {
        double result = a * b;
        checkResult(result, "multiply");
        return result;
    }

    public double divide(double a, double b) {
        if (b == 0.0) {
            throw new CalculatorException("Division by zero is not allowed");
        }
        double result = a / b;
        checkResult(result, "divide");
        return result;
    }

    public double parseParameter(String rawValue, String paramName) {
        if (rawValue == null || rawValue.trim().isEmpty()) {
            throw new CalculatorException("Missing required parameter '" + paramName + "'");
        }
        String trimmed = rawValue.trim();
        if ("NaN".equalsIgnoreCase(trimmed) || "Infinity".equalsIgnoreCase(trimmed) 
                || "+Infinity".equalsIgnoreCase(trimmed) || "-Infinity".equalsIgnoreCase(trimmed)) {
            throw new CalculatorException("Parameter '" + paramName + "' must be a finite number, but was '" + trimmed + "'");
        }
        double value;
        try {
            value = Double.parseDouble(trimmed);
        } catch (NumberFormatException ex) {
            throw new CalculatorException("Parameter '" + paramName + "' must be a number, but was '" + trimmed + "'");
        }
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new CalculatorException("Parameter '" + paramName + "' must be a finite number, but was '" + trimmed + "'");
        }
        return value;
    }

    private void checkResult(double result, String operation) {
        if (Double.isNaN(result) || Double.isInfinite(result)) {
            throw new CalculatorException("Result of " + operation + " is too large to represent as a finite number");
        }
    }
}
