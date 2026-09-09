package com.cc4c.common;

import com.cc4c.dto.ApiResponse;
import com.cc4c.security.RedisInfrastructureFailure;
import jakarta.validation.ConstraintViolationException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestCookieException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.filter.ServerHttpObservationFilter;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** 将 MVC 异常映射为统一状态和响应体，标记观测错误，并为未知异常记录不含正文的定位摘要。 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 按业务异常携带的状态、业务码、数据和提示返回响应，并标记本次观测错误。
     *
     * @param exception 当前需要分类、响应或登记的异常
     * @return 使用对应 HTTP 状态及业务码封装的响应
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Object>> handleBusinessException(BusinessException exception) {
        markHandled(exception);
        return ResponseEntity.status(exception.status())
                .body(new ApiResponse<>(exception.code(), exception.data(), exception.getMessage()));
    }

    /**
     * 返回 HTTP 429 与 Retry-After 秒数，并标记本次限流错误。
     *
     * @param exception 当前需要分类、响应或登记的异常
     * @return 使用对应 HTTP 状态及业务码封装的响应
     */
    @ExceptionHandler(RateLimitException.class)
    public ResponseEntity<ApiResponse<Boolean>> handleRateLimit(RateLimitException exception) {
        markHandled(exception);
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(exception.retryAfterSeconds()))
                .body(new ApiResponse<>(BusinessCode.RATE_LIMITED.code(), false, exception.getMessage()));
    }

    /**
     * 将 Redis 连接或系统故障映射为脱敏的 HTTP 503，不返回依赖异常正文。
     *
     * @param exception 当前需要分类、响应或登记的异常
     * @return 使用对应 HTTP 状态及业务码封装的响应
     */
    @ExceptionHandler({RedisConnectionFailureException.class, RedisSystemException.class})
    public ResponseEntity<ApiResponse<Boolean>> handleRedisUnavailable(Exception exception) {
        markHandled(exception);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiResponse<>(BusinessCode.SERVICE_UNAVAILABLE.code(), false, "安全服务暂时不可用"));
    }

    /**
     * 汇总请求体字段校验错误，每个字段只保留首条提示，并返回 HTTP 400。
     *
     * @param exception 当前需要分类、响应或登记的异常
     * @return 使用对应 HTTP 状态及业务码封装的响应
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleBodyValidation(
            MethodArgumentNotValidException exception) {
        markHandled(exception);
        Map<String, String> errors = new LinkedHashMap<>();
        exception
                .getBindingResult()
                .getFieldErrors()
                .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return ResponseEntity.badRequest()
                .body(new ApiResponse<>(BusinessCode.VALIDATION_ERROR.code(), errors, "Request validation failed"));
    }

    /**
     * 将参数缺失、绑定、类型、约束及 JSON 读取错误统一映射为 HTTP 400。
     *
     * @param exception 当前需要分类、响应或登记的异常
     * @return 使用对应 HTTP 状态及业务码封装的响应
     */
    @ExceptionHandler({
        ConstraintViolationException.class,
        BindException.class,
        MethodArgumentTypeMismatchException.class,
        MissingServletRequestParameterException.class,
        HttpMessageNotReadableException.class
    })
    public ResponseEntity<ApiResponse<Boolean>> handleInvalidRequest(Exception exception) {
        markHandled(exception);
        return ResponseEntity.badRequest()
                .body(new ApiResponse<>(BusinessCode.VALIDATION_ERROR.code(), false, "Request validation failed"));
    }

    /**
     * 将必需 Cookie 缺失映射为 HTTP 401 和登录提示。
     *
     * @param exception 当前需要分类、响应或登记的异常
     * @return 使用对应 HTTP 状态及业务码封装的响应
     */
    @ExceptionHandler(MissingRequestCookieException.class)
    public ResponseEntity<ApiResponse<Boolean>> handleMissingCookie(MissingRequestCookieException exception) {
        markHandled(exception);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(BusinessCode.UNAUTHORIZED.code(), false, "请先登录"));
    }

    /**
     * 将不存在的静态资源请求映射为 HTTP 404。
     *
     * @param exception 当前需要分类、响应或登记的异常
     * @return 使用对应 HTTP 状态及业务码封装的响应
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Boolean>> handleMissingResource(NoResourceFoundException exception) {
        markHandled(exception);
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse<>(BusinessCode.NOT_FOUND.code(), false, "Resource does not exist"));
    }

    /**
     * 将不支持的 HTTP 方法映射为 HTTP 405。
     *
     * @param exception 当前需要分类、响应或登记的异常
     * @return 使用对应 HTTP 状态及业务码封装的响应
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Boolean>> handleUnsupportedMethod(
            HttpRequestMethodNotSupportedException exception) {
        markHandled(exception);
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(new ApiResponse<>(BusinessCode.VALIDATION_ERROR.code(), false, "HTTP method is not allowed"));
    }

    /**
     * 将唯一键冲突映射为 HTTP 409，不泄露数据库约束或值。
     *
     * @param exception 当前需要分类、响应或登记的异常
     * @return 使用对应 HTTP 状态及业务码封装的响应
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ApiResponse<Boolean>> handleDuplicateKey(DuplicateKeyException exception) {
        markHandled(exception);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiResponse<>(BusinessCode.CONFLICT.code(), false, "Resource already exists"));
    }

    /**
     * 将数据完整性约束失败映射为 HTTP 422 与受控引用错误提示。
     *
     * @param exception 当前需要分类、响应或登记的异常
     * @return 使用对应 HTTP 状态及业务码封装的响应
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Boolean>> handleDataIntegrity(DataIntegrityViolationException exception) {
        markHandled(exception);
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ApiResponse<>(
                        BusinessCode.FOREIGN_KEY_CONSTRAINT_VIOLATION.code(), false, "Referenced resource is invalid"));
    }

    /**
     * 先识别包装后的 Redis 故障；其余异常记录类型、指纹及首帧位置，并返回脱敏 HTTP 500。
     *
     * @param exception 当前需要分类、响应或登记的异常
     * @return 使用对应 HTTP 状态及业务码封装的响应
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Boolean>> handleUnexpectedException(Exception exception) {
        if (RedisInfrastructureFailure.isUnavailable(exception)) {
            return handleRedisUnavailable(exception);
        }
        markHandled(exception);
        StackTraceElement top =
                exception.getStackTrace().length == 0 ? null : exception.getStackTrace()[0];
        log.atError()
                .addKeyValue("event", "unhandled_request_failure")
                .addKeyValue("exception_type", exception.getClass().getName())
                .addKeyValue("exception_fingerprint", fingerprint(exception))
                .addKeyValue("top_frame", top == null ? "unknown" : top.getClassName() + "." + top.getMethodName())
                .log("Unhandled request failure");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse<>(BusinessCode.INTERNAL_ERROR.code(), false, "Request processing failed"));
    }

    /**
     * 存在 Servlet 请求及观测上下文时登记异常，使已处理失败仍进入 HTTP 观测。
     *
     * @param exception 当前需要分类、响应或登记的异常
     */
    private static void markHandled(Exception exception) {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            ServerHttpObservationFilter.findObservationContext(attributes.getRequest())
                    .ifPresent(context -> context.setError(exception));
        }
    }

    /**
     * 对异常类型及最多八个堆栈位置计算 SHA-256，截取前八字节作为不含异常消息的定位指纹。
     *
     * @param exception 当前需要分类、响应或登记的异常
     * @return 十六位十六进制指纹；算法不可用时返回固定标记
     */
    private static String fingerprint(Exception exception) {
        StringBuilder source = new StringBuilder(exception.getClass().getName());
        StackTraceElement[] frames = exception.getStackTrace();
        for (int index = 0; index < Math.min(frames.length, 8); index++) {
            StackTraceElement frame = frames[index];
            source.append('|')
                    .append(frame.getClassName())
                    .append('.')
                    .append(frame.getMethodName())
                    .append(':')
                    .append(frame.getLineNumber());
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(source.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException impossible) {
            return "sha256-unavailable";
        }
    }
}
