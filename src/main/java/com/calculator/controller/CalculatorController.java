package com.calculator.controller;

import com.calculator.dto.CalculatorResponse;
import com.calculator.service.CalculatorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class CalculatorController {

    private final CalculatorService calculatorService;

    public CalculatorController(CalculatorService calculatorService) {
        this.calculatorService = calculatorService;
    }

    @GetMapping("/add")
    public ResponseEntity<CalculatorResponse> add(
            @RequestParam(value = "a", required = false) String aStr,
            @RequestParam(value = "b", required = false) String bStr) {
        double a = calculatorService.parseParameter(aStr, "a");
        double b = calculatorService.parseParameter(bStr, "b");
        double result = calculatorService.add(a, b);
        return ResponseEntity.ok(new CalculatorResponse("add", a, b, result));
    }

    @GetMapping("/subtract")
    public ResponseEntity<CalculatorResponse> subtract(
            @RequestParam(value = "a", required = false) String aStr,
            @RequestParam(value = "b", required = false) String bStr) {
        double a = calculatorService.parseParameter(aStr, "a");
        double b = calculatorService.parseParameter(bStr, "b");
        double result = calculatorService.subtract(a, b);
        return ResponseEntity.ok(new CalculatorResponse("subtract", a, b, result));
    }

    @GetMapping("/multiply")
    public ResponseEntity<CalculatorResponse> multiply(
            @RequestParam(value = "a", required = false) String aStr,
            @RequestParam(value = "b", required = false) String bStr) {
        double a = calculatorService.parseParameter(aStr, "a");
        double b = calculatorService.parseParameter(bStr, "b");
        double result = calculatorService.multiply(a, b);
        return ResponseEntity.ok(new CalculatorResponse("multiply", a, b, result));
    }

    @GetMapping("/divide")
    public ResponseEntity<CalculatorResponse> divide(
            @RequestParam(value = "a", required = false) String aStr,
            @RequestParam(value = "b", required = false) String bStr) {
        double a = calculatorService.parseParameter(aStr, "a");
        double b = calculatorService.parseParameter(bStr, "b");
        double result = calculatorService.divide(a, b);
        return ResponseEntity.ok(new CalculatorResponse("divide", a, b, result));
    }
}
