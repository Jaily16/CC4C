package com.cc4c.shared;

/** ApiResponse 是不可变的数据载体，保持现有字段语义和序列化契约。 */
public record ApiResponse<T>(int code, T data, String msg) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(BusinessCode.SUCCESS.code(), data, null);
    }

    public static <T> ApiResponse<T> success(int code, T data, String message) {
        return new ApiResponse<>(code, data, message);
    }
}
