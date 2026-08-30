package com.cc4c.shared;

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

@RestControllerAdvice
/** GlobalExceptionHandler 表示可被统一错误处理器识别的业务故障。 */
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Object>> handleBusinessException(BusinessException exception) {
        markHandled(exception);
        return ResponseEntity.status(exception.status())
                .body(new ApiResponse<>(exception.code(), exception.data(), exception.getMessage()));
    }

    @ExceptionHandler(RateLimitException.class)
    public ResponseEntity<ApiResponse<Boolean>> handleRateLimit(RateLimitException exception) {
        markHandled(exception);
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(exception.retryAfterSeconds()))
                .body(new ApiResponse<>(BusinessCode.RATE_LIMITED.code(), false, exception.getMessage()));
    }

    @ExceptionHandler({RedisConnectionFailureException.class, RedisSystemException.class})
    public ResponseEntity<ApiResponse<Boolean>> handleRedisUnavailable(Exception exception) {
        markHandled(exception);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiResponse<>(BusinessCode.SERVICE_UNAVAILABLE.code(), false, "安全服务暂时不可用"));
    }

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

    @ExceptionHandler(MissingRequestCookieException.class)
    public ResponseEntity<ApiResponse<Boolean>> handleMissingCookie(MissingRequestCookieException exception) {
        markHandled(exception);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>(BusinessCode.UNAUTHORIZED.code(), false, "请先登录"));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Boolean>> handleMissingResource(NoResourceFoundException exception) {
        markHandled(exception);
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiResponse<>(BusinessCode.NOT_FOUND.code(), false, "Resource does not exist"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Boolean>> handleUnsupportedMethod(
            HttpRequestMethodNotSupportedException exception) {
        markHandled(exception);
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(new ApiResponse<>(BusinessCode.VALIDATION_ERROR.code(), false, "HTTP method is not allowed"));
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ApiResponse<Boolean>> handleDuplicateKey(DuplicateKeyException exception) {
        markHandled(exception);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiResponse<>(BusinessCode.CONFLICT.code(), false, "Resource already exists"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Boolean>> handleDataIntegrity(DataIntegrityViolationException exception) {
        markHandled(exception);
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ApiResponse<>(
                        BusinessCode.FOREIGN_KEY_CONSTRAINT_VIOLATION.code(), false, "Referenced resource is invalid"));
    }

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

    private static void markHandled(Exception exception) {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            ServerHttpObservationFilter.findObservationContext(attributes.getRequest())
                    .ifPresent(context -> context.setError(exception));
        }
    }

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
