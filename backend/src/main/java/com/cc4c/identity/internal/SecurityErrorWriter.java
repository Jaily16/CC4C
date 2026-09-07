package com.cc4c.identity.internal;

import com.cc4c.shared.ApiResponse;
import com.cc4c.shared.BusinessCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/**
 * SecurityErrorWriter 负责身份认证的一项明确运行职责，并保持现有外部行为不变。
 */
@Component
final class SecurityErrorWriter {
    private final ObjectMapper objectMapper;

    /**
     * 创建 SecurityErrorWriter 并保存所需协作组件；构造阶段不主动执行外部业务操作。
     *
     * @param objectMapper 应用统一配置的 JSON 映射器
     */
    SecurityErrorWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 执行认证或 Session 状态，不跨越既有角色、Cookie 与脱敏边界。
     *
     * @param response 当前 HTTP 响应，用于写入状态或安全 Cookie
     * @param status 当前对象或流程的有限状态
     * @param code 调用方提供的 {@code code} 值
     * @param message 当前处理的消息或用户提示
     * @throws IOException 当输入、数据或依赖状态不满足当前方法约束时抛出
     */
    void write(HttpServletResponse response, int status, BusinessCode code, String message) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), new ApiResponse<>(code.code(), false, message));
    }
}
