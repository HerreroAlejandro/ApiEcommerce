package com.api.crud.exceptions;

public class UnauthenticatedException extends ApplicationException {

    public UnauthenticatedException() {
        this("Debe iniciar sesion con credenciales validas");
    }

    public UnauthenticatedException(String message) {
        super(ErrorCode.UNAUTHENTICATED, message);
    }
}
