package com.api.crud.exceptions;

public class ExternalServiceException extends ApplicationException {

    public ExternalServiceException() {
        this("El servicio externo no esta disponible");
    }

    public ExternalServiceException(String message) {
        super(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE, message);
    }

    public ExternalServiceException(String message, Throwable cause) {
        super(ErrorCode.EXTERNAL_SERVICE_UNAVAILABLE, message, cause);
    }
}
