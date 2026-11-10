package com.api.crud;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.api.crud.DTO.*;
import com.api.crud.exceptions.*;
import com.api.crud.models.entity.ProductDigital;
import com.api.crud.models.entity.ProductPhysical;
import com.api.crud.repositories.ProductDao;
import com.api.crud.services.ProductService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

class ProductValidationRulesTests {

    private ProductService service;

    private ProductDao products;

    private ProductPhysical product;

    @BeforeEach
    void setUp() {
        products = mock(ProductDao.class);
        service = new ProductService();
        ReflectionTestUtils.setField(service, "productDao", products);
        product = new ProductPhysical();
        product.setPriceProduct(new BigDecimal("1.99"));
        product.setStockProduct(5);
        when(products.getProductById(1L)).thenReturn(Optional.of(product));
    }

    @Test
    void percentageAdjustmentRoundsToTwoDecimalPlaces() {
        assertTrue(
                service.adjustProductPrice(
                        1L, new ProductPriceAdjustmentDTO(new BigDecimal("10"))));
        assertEquals(new BigDecimal("2.19"), product.getPriceProduct());
    }

    @Test
    void unusedActiveOrInactiveProductCanBeDeleted() {
        when(products.deleteProduct(1L)).thenReturn(true);
        product.setActive(true);
        assertTrue(service.deleteProduct(1L));
        product.setActive(false);
        assertTrue(service.deleteProduct(1L));
        verify(products, times(2)).deleteProduct(1L);
    }

    @Test
    void referencedProductCannotBeDeletedOrAutomaticallyDeactivated() {
        product.setActive(true);
        when(products.hasProductReferences(1L)).thenReturn(true);
        BusinessConflictException exception = assertThrows(
                BusinessConflictException.class, () -> service.deleteProduct(1L));
        assertTrue(exception.getMessage().contains("Puede desactivarlo"));
        assertTrue(product.isActive());
        verify(products, never()).deleteProduct(anyLong());
    }

    @Test
    void missingProductPreservesNotFoundBehavior() {
        assertFalse(service.deleteProduct(99L));
        verify(products, never()).hasProductReferences(99L);
        verify(products, never()).deleteProduct(anyLong());
    }

    @Test
    void referenceCreatedDuringDeletionReturnsBusinessConflict() {
        when(products.deleteProduct(1L)).thenThrow(
                new DataIntegrityViolationException("Foreign key"));
        assertThrows(BusinessConflictException.class, () -> service.deleteProduct(1L));
    }

    @Test
    void roundedZeroOrPriceBeyondStorageLimitIsRejectedBeforeMutation() {
        assertThrows(
                InvalidRequestException.class,
                () ->
                        service.adjustProductPrice(
                                1L, new ProductPriceAdjustmentDTO(new BigDecimal("-99.99"))));
        assertEquals(new BigDecimal("1.99"), product.getPriceProduct());
        product.setPriceProduct(new BigDecimal("99999999.99"));
        assertThrows(
                InvalidRequestException.class,
                () ->
                        service.adjustProductPrice(
                                1L, new ProductPriceAdjustmentDTO(BigDecimal.ONE)));
        assertEquals(new BigDecimal("99999999.99"), product.getPriceProduct());
    }

    @Test
    void stockAdjustmentRejectsOverflowAndNegativeResult() {
        ProductStockAdjustmentDTO dto = new ProductStockAdjustmentDTO();
        dto.setAdjustment(1);
        product.setStockProduct(Integer.MAX_VALUE);
        assertThrows(InvalidRequestException.class, () -> service.adjustProductStock(1L, dto));
        assertEquals(Integer.MAX_VALUE, product.getStockProduct());
        product.setStockProduct(1);
        dto.setAdjustment(-2);
        assertThrows(InvalidRequestException.class, () -> service.adjustProductStock(1L, dto));
        assertEquals(1, product.getStockProduct());
    }

    @Test
    void invertedCatalogPricesAndOversizedPagesNeverReachDao() {
        assertThrows(
                InvalidRequestException.class,
                () ->
                        service.showProducts(
                                null,
                                null,
                                null,
                                new BigDecimal("100"),
                                new BigDecimal("10"),
                                null,
                                PageRequest.of(0, 10)));
        assertThrows(
                InvalidRequestException.class,
                () ->
                        service.adminShowProducts(
                                null, null, null, null, null, null, null, PageRequest.of(0, 101)));
        verifyNoInteractions(products);
    }

    @Test
    void digitalProductStockChangesAreBusinessConflicts() {
        when(products.getProductById(1L)).thenReturn(Optional.of(new ProductDigital()));
        ProductStockUpdateDTO update = new ProductStockUpdateDTO();
        update.setNewStockProduct(2);
        ProductStockAdjustmentDTO adjustment = new ProductStockAdjustmentDTO();
        adjustment.setAdjustment(1);
        assertThrows(BusinessConflictException.class, () -> service.updateProductStock(1L, update));
        assertThrows(
                BusinessConflictException.class, () -> service.adjustProductStock(1L, adjustment));
    }
}
