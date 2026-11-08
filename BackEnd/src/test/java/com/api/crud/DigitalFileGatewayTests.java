package com.api.crud;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.api.crud.exceptions.ExternalServiceException;
import com.api.crud.services.DigitalDeliveryService.DownloadGrant;
import com.api.crud.services.DigitalFileGateway;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;

class DigitalFileGatewayTests {

    private DigitalFileGateway gateway(HttpURLConnection connection) {
        return new DigitalFileGateway() {
            @Override
            public URI validateSource(String source) {
                return URI.create(source);
            }

            @Override
            protected HttpURLConnection openConnection(URI source) {
                return connection;
            }
        };
    }

    @Test
    void proxyTransfersTheFileWithoutExposingOrRedirectingToItsOrigin() throws Exception {
        HttpURLConnection connection = mock(HttpURLConnection.class);
        when(connection.getResponseCode()).thenReturn(200);
        when(connection.getContentLengthLong()).thenReturn(3L);
        when(connection.getInputStream())
                .thenReturn(new ByteArrayInputStream(new byte[] {1, 2, 3}));
        MockHttpServletResponse response = new MockHttpServletResponse();
        gateway(connection).stream(
                new DownloadGrant(20L, "https://example.com/secret.zip"), response);
        assertArrayEquals(new byte[] {1, 2, 3}, response.getContentAsByteArray());
        assertEquals(
                "attachment; filename=\"download-20.zip\"",
                response.getHeader("Content-Disposition"));
        assertEquals("no-store, private", response.getHeader("Cache-Control"));
        assertNull(response.getHeader("Location"));
        verify(connection).setInstanceFollowRedirects(false);
        verify(connection).disconnect();
    }

    @Test
    void upstreamFailureReturnsASafeErrorAndClosesTheConnection() throws IOException {
        HttpURLConnection connection = mock(HttpURLConnection.class);
        when(connection.getResponseCode()).thenReturn(302);
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertThrows(
                ExternalServiceException.class,
                () ->
                        gateway(connection).stream(
                                new DownloadGrant(20L, "https://example.com/secret"), response));
        verify(connection).disconnect();
        assertNull(response.getHeader("Location"));
    }
}
