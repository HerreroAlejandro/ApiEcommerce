package com.api.crud;

import static org.junit.jupiter.api.Assertions.*;

import com.api.crud.config.CorsConfig;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

class CorsConfigTests {
    @Test
    void originsAreExactDeduplicatedAndMayBeDisabled() {
        var factory = new CorsConfig();
        var source =
                factory.corsConfigurationSource(
                        " http://localhost:5173/, https://shop.example, http://localhost:5173 ");
        var configuration =
                source.getCorsConfiguration(new MockHttpServletRequest("GET", "/orders"));
        assertNotNull(configuration);
        assertEquals(
                List.of("http://localhost:5173", "https://shop.example"),
                configuration.getAllowedOrigins());
        assertEquals("http://localhost:5173", configuration.checkOrigin("http://localhost:5173"));
        assertNull(configuration.checkOrigin("http://localhost:5174"));
        assertNull(configuration.checkOrigin("https://shop.example.evil.test"));
        var disabled =
                factory.corsConfigurationSource("")
                        .getCorsConfiguration(new MockHttpServletRequest("GET", "/orders"));
        assertNotNull(disabled);
        assertNull(disabled.checkOrigin("http://localhost:5173"));
    }

    @Test
    void malformedOriginsAndWildcardsCannotAccidentallyOpenBrowserAccess() {
        for (String invalid :
                List.of(
                        "*",
                        "https://*.example.com",
                        "null",
                        "ftp://example.com",
                        "https://user:pass@example.com",
                        "https://example.com/frontend",
                        "https://example.com?x=1",
                        "https://example.com#fragment",
                        "http://localhost:99999",
                        "http://localhost:5173,"))
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new CorsConfig().corsConfigurationSource(invalid),
                    invalid);
    }
}
