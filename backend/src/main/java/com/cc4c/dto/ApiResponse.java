package com.cc4c.dto;

import com.cc4c.common.BusinessCode;

/**
 * 承载共享基础设施接口的脱敏响应字段，不暴露内部凭据或异常。
 *
 * @param <T> 类型参数
 * @param code 调用方提供的 {@code code} 值
 * @param data 调用方提供的 {@code data} 值
 * @param msg 调用方提供的 {@code msg} 值
 */
public record ApiResponse<T>(int code, T data, String msg) {

    /**
     * 执行当前组件负责的数据或状态，并把失败交由既有异常边界处理。
     *
     * @param <T> 方法使用的类型参数
     * @param data 调用方提供的 {@code data} 值
     * @return 统一封装且可安全返回客户端的响应
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(BusinessCode.SUCCESS.code(), data, null);
    }

    /**
     * 执行当前组件负责的数据或状态，并把失败交由既有异常边界处理。
     *
     * @param <T> 方法使用的类型参数
     * @param code 调用方提供的 {@code code} 值
     * @param data 调用方提供的 {@code data} 值
     * @param message 当前处理的消息或用户提示
     * @return 统一封装且可安全返回客户端的响应
     */
    public static <T> ApiResponse<T> success(int code, T data, String message) {
        return new ApiResponse<>(code, data, message);
    }
}
