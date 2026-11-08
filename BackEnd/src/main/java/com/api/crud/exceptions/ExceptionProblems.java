package com.api.crud.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

public final class ExceptionProblems {

    private ExceptionProblems() {}

    public static HttpStatus status(ApplicationException exception) {
        return switch (exception.getCode()) {
            case INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
            case RESOURCE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case BUSINESS_CONFLICT, INSUFFICIENT_STOCK -> HttpStatus.CONFLICT;
            case UNAUTHENTICATED -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case EXTERNAL_SERVICE_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
    }

    public static ProblemDetail create(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setProperty("code", code);
        return problem;
    }
}
