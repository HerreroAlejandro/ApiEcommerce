package com.api.crud.exceptions;

import jakarta.persistence.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.*;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApplicationException.class)
    public ProblemDetail applicationError(ApplicationException exception) {
        if (exception instanceof ExternalServiceException) {
            logger.error("Fallo de servicio externo", exception);
        }
        return ExceptionProblems.create(
                ExceptionProblems.status(exception),
                exception.getCode().name(),
                exception.getMessage());
    }

    @ExceptionHandler({
        OptimisticLockException.class, OptimisticLockingFailureException.class,
        PessimisticLockException.class, PessimisticLockingFailureException.class,
        LockTimeoutException.class
    })
    public ProblemDetail concurrentChange(Exception exception) {
        return ExceptionProblems.create(
                HttpStatus.CONFLICT,
                "CONCURRENT_MODIFICATION",
                "Los datos cambiaron durante la operacion. Consulte el recurso y vuelva a intentar.");
    }

    @ExceptionHandler({DataIntegrityViolationException.class, EntityExistsException.class})
    public ProblemDetail conflictingData(Exception exception) {
        return ExceptionProblems.create(
                HttpStatus.CONFLICT,
                "DATA_CONFLICT",
                "La operacion entra en conflicto con los datos actuales.");
    }

    @ExceptionHandler({
        DataAccessResourceFailureException.class,
        CannotCreateTransactionException.class,
        QueryTimeoutException.class
    })
    public ProblemDetail persistenceUnavailable(Exception exception) {
        logger.error("Persistencia no disponible", exception);
        return ExceptionProblems.create(
                HttpStatus.SERVICE_UNAVAILABLE,
                "SERVICE_UNAVAILABLE",
                "El servicio no esta disponible temporalmente. Intente nuevamente mas tarde.");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail accessDenied(AccessDeniedException exception) {
        return ExceptionProblems.create(
                HttpStatus.FORBIDDEN,
                "FORBIDDEN",
                "No tiene permisos para realizar esta operacion");
    }

    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail unauthenticated(AuthenticationException exception) {
        return ExceptionProblems.create(
                HttpStatus.UNAUTHORIZED,
                "UNAUTHENTICATED",
                "Debe iniciar sesion con credenciales validas");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> unexpected(Exception exception) {
        if (exception instanceof ErrorResponse error && !error.getStatusCode().is5xxServerError()) {
            ProblemDetail problem = error.getBody();
            problem.setProperty("code", "REQUEST_ERROR");
            return new ResponseEntity<>(problem, error.getHeaders(), error.getStatusCode());
        }
        logger.error("Error inesperado procesando la solicitud", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(
                        ExceptionProblems.create(
                                HttpStatus.INTERNAL_SERVER_ERROR,
                                "INTERNAL_ERROR",
                                "Ocurrio un error inesperado. Intente nuevamente mas tarde."));
    }
}
