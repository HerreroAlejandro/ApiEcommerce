package com.api.crud.exceptions;

public class InvalidRequestException extends ApplicationException {

    public InvalidRequestException() {
        this("Datos de entrada invalidos");
    }

    public InvalidRequestException(String message) {
        super(ErrorCode.INVALID_REQUEST, message);
    }
}
