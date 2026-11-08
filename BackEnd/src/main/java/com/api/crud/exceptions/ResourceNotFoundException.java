package com.api.crud.exceptions;

public class ResourceNotFoundException extends ApplicationException {

    public ResourceNotFoundException() {
        this("Recurso no encontrado");
    }

    public ResourceNotFoundException(String message) {
        super(ErrorCode.RESOURCE_NOT_FOUND, message);
    }
}
