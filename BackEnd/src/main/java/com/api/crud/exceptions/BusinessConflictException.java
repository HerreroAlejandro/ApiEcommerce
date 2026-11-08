package com.api.crud.exceptions;

public class BusinessConflictException extends ApplicationException {

    public BusinessConflictException() {
        this("La operacion no es compatible con el estado actual");
    }

    public BusinessConflictException(String message) {
        super(ErrorCode.BUSINESS_CONFLICT, message);
    }
}
