package com.calculator.dto;

public class CalculatorResponse {

    private String operation;
    private double a;
    private double b;
    private double result;

    public CalculatorResponse() {
    }

    public CalculatorResponse(String operation, double a, double b, double result) {
        this.operation = operation;
        this.a = a;
        this.b = b;
        this.result = result;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public double getA() {
        return a;
    }

    public void setA(double a) {
        this.a = a;
    }

    public double getB() {
        return b;
    }

    public void setB(double b) {
        this.b = b;
    }

    public double getResult() {
        return result;
    }

    public void setResult(double result) {
        this.result = result;
    }
}
