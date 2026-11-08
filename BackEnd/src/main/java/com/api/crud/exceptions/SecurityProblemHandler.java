package com.api.crud.exceptions;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;
import java.net.URI;

public class SecurityProblemHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper mapper =
            new ObjectMapper().addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class);

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception)
            throws IOException {
        write(
                request,
                response,
                HttpStatus.UNAUTHORIZED,
                "UNAUTHENTICATED",
                "Debe iniciar sesion con credenciales validas");
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException exception)
            throws IOException {
        write(
                request,
                response,
                HttpStatus.FORBIDDEN,
                "FORBIDDEN",
                "No tiene permisos para realizar esta operacion");
    }

    public void unavailable(
            HttpServletRequest request, HttpServletResponse response, Exception exception)
            throws IOException {
        write(
                request,
                response,
                HttpStatus.SERVICE_UNAVAILABLE,
                "SERVICE_UNAVAILABLE",
                "El servicio no esta disponible temporalmente. Intente nuevamente mas tarde.");
    }

    private void write(
            HttpServletRequest request,
            HttpServletResponse response,
            HttpStatus status,
            String code,
            String detail)
            throws IOException {
        ProblemDetail problem = ExceptionProblems.create(status, code, detail);
        problem.setInstance(URI.create(request.getRequestURI()));
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getOutputStream(), problem);
    }
}
