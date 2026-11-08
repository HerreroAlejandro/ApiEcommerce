package com.api.crud.exceptions;

public class ForbiddenOperationException extends ApplicationException {

    public ForbiddenOperationException() {
        this("No tiene permisos para realizar esta operacion");
    }

    public ForbiddenOperationException(String message) {
        super(ErrorCode.FORBIDDEN, message);
    }
}
