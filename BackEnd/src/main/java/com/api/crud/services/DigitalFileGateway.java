package com.api.crud.services;

import com.api.crud.exceptions.ExternalServiceException;
import com.api.crud.services.DigitalDeliveryService.DownloadGrant;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.*;
import java.util.Arrays;
import java.util.Locale;

@Component
public class DigitalFileGateway {

    @Value("${digital.download.allowed-hosts:}")
    private String allowedHosts = "";

    @Value("${digital.download.max-bytes:104857600}")
    private long maxBytes = 104857600;

    public URI validateSource(String source) {
        try {
            URI uri = URI.create(source);
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || host == null
                    || uri.getRawUserInfo() != null
                    || uri.getFragment() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443)
                    || Arrays.stream(allowedHosts.split(","))
                            .map(String::trim)
                            .noneMatch(value -> !value.isEmpty() && value.equalsIgnoreCase(host))) {
                throw unavailable();
            }
            for (InetAddress address : InetAddress.getAllByName(host)) {
                byte[] bytes = address.getAddress();
                if (address.isAnyLocalAddress()
                        || address.isLoopbackAddress()
                        || address.isSiteLocalAddress()
                        || address.isLinkLocalAddress()
                        || address.isMulticastAddress()
                        || (bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc)) {
                    throw unavailable();
                }
            }
            return uri;
        } catch (IllegalArgumentException | UnknownHostException exception) {
            throw unavailable();
        }
    }

    public void stream(DownloadGrant grant, HttpServletResponse response) throws IOException {
        URI source = validateSource(grant.sourceUrl());
        HttpURLConnection connection = openConnection(source);
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(30000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Accept-Encoding", "identity");
        try {
            if (connection.getResponseCode() != 200) throw unavailable();
            String type = connection.getContentType();
            if (type != null && type.toLowerCase(Locale.ROOT).contains("text/html"))
                throw unavailable();
            long length = connection.getContentLengthLong();
            if (maxBytes < 1 || length > maxBytes) throw unavailable();
            try (InputStream input = connection.getInputStream()) {
                response.setStatus(HttpServletResponse.SC_OK);
                response.setContentType("application/octet-stream");
                String path = source.getPath();
                String extension =
                        path != null && path.matches(".*\\.[A-Za-z0-9]{1,10}$")
                                ? path.substring(path.lastIndexOf('.'))
                                : ".bin";
                response.setHeader(
                        "Content-Disposition",
                        "attachment; filename=\"download-" + grant.deliveryId() + extension + "\"");
                response.setHeader("Cache-Control", "no-store, private");
                response.setHeader("Pragma", "no-cache");
                response.setHeader("X-Content-Type-Options", "nosniff");
                if (length >= 0) response.setContentLengthLong(length);
                byte[] buffer = new byte[16384];
                long transferred = 0;
                int count;
                while ((count = input.read(buffer)) != -1) {
                    transferred += count;
                    if (transferred > maxBytes)
                        throw new IOException("Archivo supera el limite permitido");
                    response.getOutputStream().write(buffer, 0, count);
                }
                if (length >= 0 && transferred != length)
                    throw new IOException("Descarga incompleta");
            }
        } catch (IOException exception) {
            if (response.isCommitted()) throw exception;
            response.reset();
            throw unavailable();
        } finally {
            connection.disconnect();
        }
    }

    private ExternalServiceException unavailable() {
        return new ExternalServiceException(
                "La descarga no esta disponible. Contacte a soporte si el problema persiste");
    }

    protected HttpURLConnection openConnection(URI source) throws IOException {
        return (HttpURLConnection) source.toURL().openConnection();
    }
}
