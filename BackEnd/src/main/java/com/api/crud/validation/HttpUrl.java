package com.api.crud.validation;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;
@Target(ElementType.FIELD) @Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = HttpUrlValidator.class)
public @interface HttpUrl {
    String message() default "Debe ser una URL absoluta HTTP o HTTPS valida";
    boolean allowBlank() default false;
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}