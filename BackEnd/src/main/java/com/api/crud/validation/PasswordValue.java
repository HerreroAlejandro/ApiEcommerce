package com.api.crud.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PasswordValueValidator.class)
public @interface PasswordValue {

    String message() default
            "La contraseña debe tener al menos 8 caracteres y no superar 72 bytes UTF-8";

    int min() default 8;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
