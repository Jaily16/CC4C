package com.cc4c.identity.internal;

import com.cc4c.shared.ApiResponse;
import com.cc4c.shared.BusinessCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
final class SecurityErrorWriter {
    private final ObjectMapper objectMapper;

    SecurityErrorWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    void write(HttpServletResponse response, int status, BusinessCode code, String message)
            throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), new ApiResponse<>(code.code(), false, message));
    }
}
