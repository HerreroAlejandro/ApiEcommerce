package com.api.crud.validation;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.net.URI;
public class HttpUrlValidator implements ConstraintValidator<HttpUrl, String> {
    private boolean allowBlank;
    @Override public void initialize(HttpUrl annotation) { allowBlank = annotation.allowBlank(); }
    @Override public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || (allowBlank && value.isEmpty())) return true;
        try {
            URI uri = new URI(value);
            return uri.getHost() != null && ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()));
        } catch (Exception exception) { return false; }
    }
}