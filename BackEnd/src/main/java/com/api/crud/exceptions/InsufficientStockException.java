package com.api.crud.exceptions;

public class InsufficientStockException extends ApplicationException {

    public InsufficientStockException(String message) {
        super(ErrorCode.INSUFFICIENT_STOCK, message);
    }
}
