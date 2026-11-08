package com.api.crud.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.nio.charset.StandardCharsets;

public class PasswordValueValidator implements ConstraintValidator<PasswordValue, String> {

    private int min;

    @Override
    public void initialize(PasswordValue annotation) {
        min = annotation.min();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null
                || (!value.isBlank()
                        && value.codePointCount(0, value.length()) >= min
                        && value.getBytes(StandardCharsets.UTF_8).length <= 72);
    }
}
