package com.api.crud.validation;
import java.math.BigDecimal;
import org.springframework.data.domain.Pageable;
public final class InputRules {
    private InputRules() {}
    public static void page(Pageable page) {
        if (page.getPageSize() > 100 || page.getOffset() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("La pagina debe tener entre 1 y 100 elementos y un desplazamiento valido");
        }
    }
    public static void priceRange(BigDecimal min, BigDecimal max) {
        if ((min != null && (min.signum() < 0 || min.compareTo(new BigDecimal("99999999.99")) > 0))
                || (max != null && (max.signum() < 0 || max.compareTo(new BigDecimal("99999999.99")) > 0))
                || (min != null && max != null && min.compareTo(max) > 0)) {
            throw new IllegalArgumentException("Rango de precios invalido");
        }
    }
    public static void price(BigDecimal price) {
        if (price == null || price.signum() <= 0 || price.compareTo(new BigDecimal("99999999.99")) > 0) {
            throw new IllegalArgumentException("El precio debe ser mayor a cero y no superar 99999999.99");
        }
    }
}