package com.cc4c.security;

import com.cc4c.common.BusinessCode;
import com.cc4c.dto.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/** 将安全错误写为 UTF-8 JSON 业务响应，避免在已提交响应后重复写入。 */
@Component
public final class SecurityErrorWriter {
    private final ObjectMapper objectMapper;

    /**
     * 接入应用 JSON 映射器。
     *
     * @param objectMapper 应用 JSON 映射器
     */
    SecurityErrorWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 响应尚未提交时设置 HTTP 状态与 JSON 类型，并写入 code、false 和提示；已提交时直接返回。
     *
     * @param response 当前 HTTP 响应
     * @param status HTTP 响应状态码
     * @param code 业务错误码
     * @param message 可向客户端展示的错误提示
     * @throws IOException 后续链或响应输出发生 I/O 错误时抛出
     */
    public void write(HttpServletResponse response, int status, BusinessCode code, String message) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), new ApiResponse<>(code.code(), false, message));
    }
}
