package com.api.crud.exceptions;

// Internal provider result. It is persisted only after a canonical verification.
public class RefundRejectedException extends ExternalServiceException {

    private final String providerCode;

    public RefundRejectedException(String providerCode) {
        super("Mercado Pago rechazo la solicitud de reembolso");
        this.providerCode = providerCode;
    }

    public String getProviderCode() {
        return providerCode;
    }
}
