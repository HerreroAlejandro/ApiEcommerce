package com.api.crud;

import static org.junit.jupiter.api.Assertions.*;

import com.api.crud.DTO.*;
import com.api.crud.models.enums.ProductType;

import jakarta.validation.*;

import org.junit.jupiter.api.*;

import java.math.BigDecimal;

class InputValidationTests {

    private static ValidatorFactory factory;

    private static Validator validator;

    @BeforeAll
    static void start() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void close() {
        factory.close();
    }

    private boolean valid(Object dto) {
        return validator.validate(dto).isEmpty();
    }

    @Test
    void registrationRequiresNamesEmailAndValidPasswordButPhoneIsOptional() {
        UserRegisterDTO dto =
                new UserRegisterDTO("José", "Muñoz", "client@example.com", null, "valid-password");
        assertTrue(valid(dto));
        dto.setEmail("bad-email");
        assertFalse(valid(dto));
        dto.setEmail("client@example.com");
        dto.setFirstName(" ");
        assertFalse(valid(dto));
        dto.setFirstName("José");
        dto.setPhone("only letters");
        assertFalse(valid(dto));
        dto.setPhone("+54 11 1234-5678");
        assertTrue(valid(dto));
        dto.setPassword("short");
        assertFalse(valid(dto));
    }

    @Test
    void passwordLimitUsesUtf8BytesAndConfirmationMustMatch() {
        PasswordResetDTO dto = new PasswordResetDTO("🔑".repeat(18), "🔑".repeat(18));
        assertTrue(valid(dto));
        dto.setNewPassword("🔑".repeat(19));
        dto.setConfirmPassword(dto.getNewPassword());
        assertFalse(valid(dto));
        dto.setNewPassword("12345678");
        dto.setConfirmPassword("different");
        assertFalse(valid(dto));
        dto.setConfirmPassword("12345678");
        assertTrue(valid(dto));
        assertFalse(valid(new PasswordChangeRequestDTO("old", "new", "new")));
        assertTrue(valid(new LoginRequestDTO("client@example.com", "old")));
        assertFalse(valid(new LoginRequestDTO("client@example.com", " ")));
    }

    @Test
    void profilePatchRequiresOneValidFieldAndDoesNotRequireAllFields() {
        assertFalse(valid(new UserUpdateRequestDTO()));
        assertFalse(valid(new UserUpdateRequestDTO("   ", null, null)));
        assertTrue(valid(new UserUpdateRequestDTO("Ana", null, null)));
        assertFalse(valid(new UserUpdateRequestDTO(null, "A".repeat(26), null)));
    }

    @Test
    void physicalProductNeedsStockAndRejectsDigitalFieldsOrClientSuppliedId() {
        ProductSaveDTO dto =
                new ProductSaveDTO(
                        "Libro",
                        new BigDecimal("100.00"),
                        "Descripcion",
                        null,
                        "Libros",
                        ProductType.PHYSICAL,
                        5);
        assertTrue(valid(dto));
        dto.setStockProduct(null);
        assertFalse(valid(dto));
        dto.setStockProduct(5);
        dto.setDownloadLink("https://example.com/file");
        assertFalse(valid(dto));
        dto.setDownloadLink(null);
        dto.setIdProduct(4L);
        assertFalse(valid(dto));
    }

    @Test
    void digitalProductNeedsHttpDownloadAndCannotHaveStock() {
        ProductSaveDTO dto =
                new ProductSaveDTO(
                        "Libro digital",
                        new BigDecimal("100.00"),
                        null,
                        null,
                        "Libros",
                        ProductType.DIGITAL,
                        "https://example.com/file",
                        null);
        assertTrue(valid(dto));
        dto.setStockProduct(1);
        assertFalse(valid(dto));
        dto.setStockProduct(null);
        dto.setDownloadLink("javascript:alert(1)");
        assertFalse(valid(dto));
        dto.setDownloadLink("https://example.com/file");
        dto.setImageUrl("not a url");
        assertFalse(valid(dto));
    }

    @Test
    void productPatchRoleAndRecoveryHaveUsefulConstraints() {
        assertFalse(valid(new ProductUpdateDTO()));
        ProductUpdateDTO dto = new ProductUpdateDTO();
        dto.setDescription("");
        assertTrue(valid(dto));
        dto.setDownloadLink(" ");
        assertFalse(valid(dto));
        assertTrue(valid(new RoleDTO("SUPPORT")));
        assertFalse(valid(new RoleDTO(" ")));
        assertFalse(valid(new RoleDTO("A".repeat(21))));
        assertFalse(valid(new AccountRecoveryRequestDTO("invalid")));
        assertFalse(valid(new AccountRecoveryRequestDTO("")));
    }
}
