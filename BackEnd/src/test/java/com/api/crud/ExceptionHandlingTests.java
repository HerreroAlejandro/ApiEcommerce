package com.api.crud;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.api.crud.exceptions.*;

import jakarta.persistence.OptimisticLockException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;

class ExceptionHandlingTests {

    private MockMvc mvc;

    @RestController
    @RequestMapping("/test/errors")
    static class ErrorController {

        record Input(@NotBlank String name) {}

        @GetMapping("/{kind}")
        void error(@PathVariable String kind) {
            switch (kind) {
                case "missing" -> throw new ResourceNotFoundException("Orden no encontrada");
                case "conflict" -> throw new BusinessConflictException("Orden cancelada");
                case "stock" -> throw new InsufficientStockException("Cantidad no disponible");
                case "invalid" -> throw new InvalidRequestException("Rango invalido");
                case "authentication" -> throw new UnauthenticatedException();
                case "permission" -> throw new ForbiddenOperationException();
                case "integrity" -> throw new DataIntegrityViolationException("SQL secret");
                case "concurrent" -> throw new OptimisticLockException("Internal entity details");
                case "mail" ->
                        throw new ExternalServiceException(
                                "No se pudo enviar el correo",
                                new IllegalStateException("SMTP secret"));
                default -> throw new IllegalStateException("Internal SQL/password details");
            }
        }

        @PostMapping
        void validate(@Valid @RequestBody Input input) {}
    }

    @BeforeEach
    void setUp() {
        mvc =
                MockMvcBuilders.standaloneSetup(new ErrorController())
                        .setControllerAdvice(
                                new InputValidationHandler(), new GlobalExceptionHandler())
                        .build();
    }

    @Test
    void businessErrorsHaveStableCodesAndStatuses() throws Exception {
        assertProblem("missing", 404, "RESOURCE_NOT_FOUND");
        assertProblem("conflict", 409, "BUSINESS_CONFLICT");
        assertProblem("stock", 409, "INSUFFICIENT_STOCK");
        assertProblem("invalid", 400, "INVALID_REQUEST");
        assertProblem("authentication", 401, "UNAUTHENTICATED");
        assertProblem("permission", 403, "FORBIDDEN");
    }

    @Test
    void persistenceAndInternalFailuresDoNotExposeImplementationDetails() throws Exception {
        assertProblem("integrity", 409, "DATA_CONFLICT");
        assertProblem("concurrent", 409, "CONCURRENT_MODIFICATION");
        assertProblem("mail", 503, "EXTERNAL_SERVICE_UNAVAILABLE");
        assertProblem("unexpected", 500, "INTERNAL_ERROR");
    }

    @Test
    void validationRemainsAFieldErrorAndMalformedJsonIsBadRequest() throws Exception {
        mvc.perform(
                        post("/test/errors")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors.name").exists());
        mvc.perform(post("/test/errors").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void unsupportedHttpMethodsKeepTheirHttpStatus() throws Exception {
        mvc.perform(delete("/test/errors/missing"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", containsString("GET")))
                .andExpect(jsonPath("$.code").value("REQUEST_ERROR"));
    }

    private void assertProblem(String kind, int status, String code) throws Exception {
        mvc.perform(get("/test/errors/" + kind))
                .andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.instance").value("/test/errors/" + kind))
                .andExpect(content().string(not(containsString("secret"))))
                .andExpect(content().string(not(containsString("Internal SQL"))))
                .andExpect(content().string(not(containsString("Internal entity details"))));
    }
}
