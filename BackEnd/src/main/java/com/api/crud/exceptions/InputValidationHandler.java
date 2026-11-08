package com.api.crud.exceptions;

import jakarta.validation.ConstraintViolationException;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;

@Order(0)
@RestControllerAdvice
public class InputValidationHandler {

    private ProblemDetail invalid(String detail, Map<String, String> errors) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setTitle("Datos de entrada invalidos");
        problem.setProperty("code", "INVALID_REQUEST");
        if (!errors.isEmpty()) problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail invalidBody(MethodArgumentNotValidException exception) {
        Map<String, String> errors = new LinkedHashMap<>();
        exception
                .getBindingResult()
                .getFieldErrors()
                .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        exception
                .getBindingResult()
                .getGlobalErrors()
                .forEach(
                        error ->
                                errors.putIfAbsent(
                                        error.getObjectName(), error.getDefaultMessage()));
        return invalid("Revise los campos de la solicitud", errors);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ProblemDetail invalidParameters(HandlerMethodValidationException exception) {
        Map<String, String> errors = new LinkedHashMap<>();
        exception
                .getAllValidationResults()
                .forEach(
                        result -> {
                            String name = result.getMethodParameter().getParameterName();
                            String key =
                                    name == null
                                            ? "parameter"
                                                    + result.getMethodParameter()
                                                            .getParameterIndex()
                                            : name;
                            result.getResolvableErrors()
                                    .forEach(
                                            error ->
                                                    errors.putIfAbsent(
                                                            key, error.getDefaultMessage()));
                        });
        return invalid("Parametros de consulta o ruta invalidos", errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail constraintViolation(ConstraintViolationException exception) {
        Map<String, String> errors = new LinkedHashMap<>();
        exception
                .getConstraintViolations()
                .forEach(
                        error ->
                                errors.putIfAbsent(
                                        error.getPropertyPath().toString(), error.getMessage()));
        return invalid("La solicitud no cumple las validaciones", errors);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail invalidType(MethodArgumentTypeMismatchException exception) {
        return invalid(
                "Tipo o formato de parametro invalido",
                Map.of(exception.getName(), "Valor invalido"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail invalidJson(HttpMessageNotReadableException exception) {
        return invalid("JSON invalido: revise los tipos, enums y campos enviados", Map.of());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ProblemDetail missingParameter(MissingServletRequestParameterException exception) {
        return invalid(
                "Falta un parametro obligatorio",
                Map.of(exception.getParameterName(), "Campo obligatorio"));
    }
}
