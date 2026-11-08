package com.api.crud.exceptions;

public abstract class ApplicationException extends RuntimeException {

    private final ErrorCode code;

    protected ApplicationException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    protected ApplicationException(ErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public ErrorCode getCode() {
        return code;
    }
}
