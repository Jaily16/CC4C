package com.cc4c.dto;

import com.cc4c.common.BusinessCode;

/**
 * 统一封装业务码、数据和提示；字段内容由调用方选择，本容器不执行脱敏。
 *
 * @param <T> 数据元素类型
 * @param code 业务响应码，不等同于 HTTP 状态码
 * @param data 响应数据
 * @param msg 面向客户端的业务提示
 */
public record ApiResponse<T>(int code, T data, String msg) {

    /**
     * 使用默认成功业务码包装数据，提示置空。
     *
     * @param <T> 数据元素类型
     * @param data 响应数据
     * @return 默认成功响应
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(BusinessCode.SUCCESS.code(), data, null);
    }

    /**
     * 使用指定业务码、数据和提示创建响应，不校验或转换数据。
     *
     * @param <T> 数据元素类型
     * @param code 业务响应码，不等同于 HTTP 状态码
     * @param data 响应数据
     * @param message 上传结果说明
     * @return 封装后的业务响应
     */
    public static <T> ApiResponse<T> success(int code, T data, String message) {
        return new ApiResponse<>(code, data, message);
    }
}
