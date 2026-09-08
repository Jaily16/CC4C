package com.cc4c.shared;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * EditorUploadResponse 是不可变的数据载体，保持现有字段语义和序列化契约。
 *
 * @param success 调用方提供的 {@code success} 值
 * @param message 待处理的消息及其属性
 * @param url 调用方提供的 {@code url} 值
 * @param status 调用方提供的 {@code status} 值
 * @param errorMessage 调用方提供的 {@code errorMessage} 值
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EditorUploadResponse(
        String success,
        String message,
        String url,
        @JsonProperty("STATUS") String status,
        @JsonProperty("MSG") String errorMessage) {
    /**
     * 执行 EditorUploadResponse 中的 success 职责，并保持既有权限、事务与副作用边界。
     *
     * @param url 调用方提供的 {@code url} 值
     * @return 按当前声明计算、查询或转换得到的结果
     */
    public static EditorUploadResponse success(String url) {
        return new EditorUploadResponse("1", "success", url, null, null);
    }

    /**
     * 执行 EditorUploadResponse 中的 error 职责，并保持既有权限、事务与副作用边界。
     *
     * @param message 待处理的消息及其属性
     * @return 按当前声明计算、查询或转换得到的结果
     */
    public static EditorUploadResponse error(String message) {
        return new EditorUploadResponse(null, null, null, "ERROR", message);
    }
}
