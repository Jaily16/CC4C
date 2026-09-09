package com.cc4c.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 适配编辑器上传协议：成功使用 success/message/url，失败使用 STATUS/MSG；空字段不输出。
 *
 * @param success 成功时为字符串 1，失败时为空
 * @param message 上传结果说明
 * @param url 上传图片的公开 URL
 * @param status 失败时输出为 STATUS，值为 ERROR
 * @param errorMessage 失败时输出为 MSG 的错误说明
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EditorUploadResponse(
        String success,
        String message,
        String url,
        @JsonProperty("STATUS") String status,
        @JsonProperty("MSG") String errorMessage) {
    /**
     * 构造编辑器成功响应，设置 success 为字符串 1 并携带图片 URL。
     *
     * @param url 上传图片的公开 URL
     * @return 仅含成功协议字段的响应
     */
    public static EditorUploadResponse success(String url) {
        return new EditorUploadResponse("1", "success", url, null, null);
    }

    /**
     * 构造编辑器失败响应，设置 STATUS 为 ERROR 并携带 MSG。
     *
     * @param message 上传结果说明
     * @return 仅含失败协议字段的响应
     */
    public static EditorUploadResponse error(String message) {
        return new EditorUploadResponse(null, null, null, "ERROR", message);
    }
}
