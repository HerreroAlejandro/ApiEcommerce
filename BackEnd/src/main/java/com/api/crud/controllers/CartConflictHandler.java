package com.api.crud.controllers;

import jakarta.persistence.LockTimeoutException;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PessimisticLockException;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(
        assignableTypes = {
            AdminCartItemController.class,
            CartItemController.class,
            CartController.class,
            AdminCartController.class,
            OrderController.class,
            AdminOrderController.class,
            AdminUserController.class
        })
public class CartConflictHandler {

    @ExceptionHandler({
        OptimisticLockException.class,
        OptimisticLockingFailureException.class,
        PessimisticLockingFailureException.class,
        PessimisticLockException.class,
        LockTimeoutException.class
    })
    public ProblemDetail concurrentChange(Exception exception) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                "Los datos cambiaron durante la operacion. Consulte el recurso y vuelva a intentar.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail conflictingData(DataIntegrityViolationException exception) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "La operacion entra en conflicto con los datos actuales.");
    }
}
