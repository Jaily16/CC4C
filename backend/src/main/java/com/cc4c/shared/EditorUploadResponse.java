package com.cc4c.shared;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
/** EditorUploadResponse 是不可变的数据载体，保持现有字段语义和序列化契约。 */
public record EditorUploadResponse(
        String success,
        String message,
        String url,
        @JsonProperty("STATUS") String status,
        @JsonProperty("MSG") String errorMessage) {
    public static EditorUploadResponse success(String url) {
        return new EditorUploadResponse("1", "success", url, null, null);
    }

    public static EditorUploadResponse error(String message) {
        return new EditorUploadResponse(null, null, null, "ERROR", message);
    }
}
