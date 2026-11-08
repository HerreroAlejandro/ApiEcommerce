package com.api.crud;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.api.crud.controllers.DigitalDeliveryController;
import com.api.crud.exceptions.InvalidRequestException;
import com.api.crud.services.*;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

class DigitalDownloadControllerTests {

    @Test
    void headAndRangeRequestsDoNotConsumeAnAttempt() {
        DigitalDeliveryService deliveries = mock(DigitalDeliveryService.class);
        DigitalFileGateway files = mock(DigitalFileGateway.class);
        DigitalDeliveryController controller = new DigitalDeliveryController(deliveries, files);
        var auth = new UsernamePasswordAuthenticationToken("buyer@example.com", null);
        MockHttpServletRequest request =
                new MockHttpServletRequest("HEAD", "/digital-deliveries/20/download");
        assertThrows(
                InvalidRequestException.class,
                () ->
                        controller.download(
                                auth, 20L, "token", request, new MockHttpServletResponse()));
        request.setMethod("GET");
        request.addHeader("Range", "bytes=0-10");
        assertThrows(
                InvalidRequestException.class,
                () ->
                        controller.download(
                                auth, 20L, "token", request, new MockHttpServletResponse()));
        verifyNoInteractions(deliveries, files);
    }

    @Test
    void unconfiguredUntrustedAndNonHttpsSourcesAreBlockedWithoutNetworkAccess() {
        DigitalFileGateway files = new DigitalFileGateway();
        assertThrows(
                com.api.crud.exceptions.ExternalServiceException.class,
                () -> files.validateSource("https://example.com/file.zip"));
        assertThrows(
                com.api.crud.exceptions.ExternalServiceException.class,
                () -> files.validateSource("http://127.0.0.1/file.zip"));
        assertThrows(
                com.api.crud.exceptions.ExternalServiceException.class,
                () -> files.validateSource("file:///etc/passwd"));
    }
}
