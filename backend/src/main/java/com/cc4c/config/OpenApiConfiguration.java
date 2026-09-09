package com.cc4c.config;

import com.cc4c.common.CorrelationIds;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 补充 OpenAPI 的错误响应、创建状态码及业务和观测安全要求；仅生成文档描述。 */
@Configuration
public class OpenApiConfiguration {

    /**
     * 创建接口描述的标题、描述和已有契约版本元数据。
     *
     * @return OpenAPI 基础描述对象
     */
    @Bean
    OpenAPI cc4cOpenApi() {
        return new OpenAPI()
                .info(new Info().title("CC4C API").version("3").description("CC4C modular monolith HTTP API"));
    }

    /**
     * 按路径补充 201、通用错误、两套 Cookie/CSRF 要求及请求关联响应头。
     *
     * @return 接口描述生成时使用的定制器
     */
    @Bean
    OpenApiCustomizer cc4cResponseDocumentation() {
        Set<String> createdPaths = Set.of(
                "/users",
                "/courses/module",
                "/courses/add",
                "/blogs/submit",
                "/courses/star/{courseId}",
                "/blogs/collect/{blogId}",
                "/comments/course",
                "/comments/blog",
                "/comments/indirect");
        Map<String, String> errorDescriptions = Map.of(
                "400", "Invalid DTO, path or paging parameter",
                "401", "Authentication or session validation failed",
                "403", "Role, ownership or CSRF validation failed",
                "404", "Resource does not exist",
                "409", "Unique or state conflict",
                "422", "Referenced resource or business state is invalid",
                "429", "Request rate limit exceeded",
                "503", "Security infrastructure is unavailable",
                "500", "Unexpected server error");
        return openApi -> {
            Components components = openApi.getComponents();
            if (components == null) {
                components = new Components();
                openApi.setComponents(components);
            }
            components.addSchemas("ApiErrorResponse", errorResponseSchema());
            components.addSecuritySchemes(
                    "CC4C_SESSION",
                    new SecurityScheme()
                            .type(SecurityScheme.Type.APIKEY)
                            .in(SecurityScheme.In.COOKIE)
                            .name("CC4C_SESSION"));
            components.addSecuritySchemes(
                    "X-XSRF-TOKEN",
                    new SecurityScheme()
                            .type(SecurityScheme.Type.APIKEY)
                            .in(SecurityScheme.In.HEADER)
                            .name("X-XSRF-TOKEN"));
            components.addSecuritySchemes(
                    "CC4C_OBSERVABILITY_SESSION",
                    new SecurityScheme()
                            .type(SecurityScheme.Type.APIKEY)
                            .in(SecurityScheme.In.COOKIE)
                            .name("CC4C_OBSERVABILITY_SESSION"));
            components.addSecuritySchemes(
                    "X-CC4C-OBSERVABILITY-CSRF",
                    new SecurityScheme()
                            .type(SecurityScheme.Type.APIKEY)
                            .in(SecurityScheme.In.HEADER)
                            .name("X-CC4C-OBSERVABILITY-CSRF"));

            openApi.getPaths().forEach((path, item) -> item.readOperationsMap().forEach((method, operation) -> {
                if (method == io.swagger.v3.oas.models.PathItem.HttpMethod.POST && createdPaths.contains(path)) {
                    ApiResponse createdResponse = operation.getResponses().remove("200");
                    if (createdResponse == null) {
                        createdResponse = new ApiResponse();
                    }
                    createdResponse.setDescription("Resource created");
                    operation.getResponses().addApiResponse("201", createdResponse);
                }
                SecurityRequirement security = new SecurityRequirement();
                if (path.startsWith("/observability/")) {
                    if (requiresObservabilitySession(path)) {
                        security.addList("CC4C_OBSERVABILITY_SESSION");
                    }
                    if (requiresObservabilityCsrf(path, method)) {
                        security.addList("X-CC4C-OBSERVABILITY-CSRF");
                    }
                } else {
                    if (requiresSession(path, method)) {
                        security.addList("CC4C_SESSION");
                    }
                    if (requiresCsrf(method)) {
                        security.addList("X-XSRF-TOKEN");
                    }
                }
                if (!security.isEmpty()) {
                    operation.setSecurity(List.of(security));
                }
                errorDescriptions.forEach(
                        (status, description) -> operation
                                .getResponses()
                                .addApiResponse(
                                        status,
                                        new ApiResponse()
                                                .description(description)
                                                .content(
                                                        new Content()
                                                                .addMediaType(
                                                                        "application/json",
                                                                        new io.swagger.v3.oas.models.media.MediaType()
                                                                                .schema(
                                                                                        new Schema<>()
                                                                                                .$ref(
                                                                                                        "#/components/schemas/ApiErrorResponse"))))));
                operation
                        .getResponses()
                        .values()
                        .forEach(response -> response.addHeaderObject(
                                CorrelationIds.HEADER,
                                new Header()
                                        .description("Request correlation identifier")
                                        .schema(new StringSchema())));
            }));
        };
    }

    /**
     * 判断文档中的业务写方法是否需声明 CSRF 请求头。
     *
     * @param method OpenAPI 中的 HTTP 方法
     * @return POST、PUT、DELETE 或 PATCH 时为 true
     */
    private boolean requiresCsrf(io.swagger.v3.oas.models.PathItem.HttpMethod method) {
        return method == io.swagger.v3.oas.models.PathItem.HttpMethod.POST
                || method == io.swagger.v3.oas.models.PathItem.HttpMethod.PUT
                || method == io.swagger.v3.oas.models.PathItem.HttpMethod.DELETE
                || method == io.swagger.v3.oas.models.PathItem.HttpMethod.PATCH;
    }

    /**
     * 排除观测 CSRF、登录和会话探测入口，其余观测路径声明独立会话要求。
     *
     * @param path OpenAPI 接口路径
     * @return 该路径是否需在文档中标记观测会话
     */
    private boolean requiresObservabilitySession(String path) {
        return !Set.of("/observability/auth/csrf", "/observability/auth/login", "/observability/auth/session")
                .contains(path);
    }

    /**
     * 仅为观测登录和退出的 POST 请求声明独立 CSRF 要求。
     *
     * @param path OpenAPI 接口路径
     * @param method OpenAPI 中的 HTTP 方法
     * @return 该操作是否需观测 CSRF 请求头
     */
    private boolean requiresObservabilityCsrf(String path, io.swagger.v3.oas.models.PathItem.HttpMethod method) {
        return method == io.swagger.v3.oas.models.PathItem.HttpMethod.POST
                && Set.of("/observability/auth/login", "/observability/auth/logout")
                        .contains(path);
    }

    /**
     * 按业务路径及方法判断会话文档要求，排除公开读取和注册、登录、重置等入口。
     *
     * @param path OpenAPI 接口路径
     * @param method OpenAPI 中的 HTTP 方法
     * @return 该操作是否需在文档中标记业务会话
     */
    private boolean requiresSession(String path, io.swagger.v3.oas.models.PathItem.HttpMethod method) {
        if (method == io.swagger.v3.oas.models.PathItem.HttpMethod.GET
                && (path.equals("/csrf") || path.equals("/auth/session"))) {
            return false;
        }
        if (method == io.swagger.v3.oas.models.PathItem.HttpMethod.POST
                && Set.of("/users", "/users/login", "/users/email", "/admin/login")
                        .contains(path)) {
            return false;
        }
        if (method == io.swagger.v3.oas.models.PathItem.HttpMethod.PUT
                && (path.equals("/users/password/forget") || path.startsWith("/blogs/click/"))) {
            return false;
        }
        if (method == io.swagger.v3.oas.models.PathItem.HttpMethod.GET
                && path.startsWith("/courses/")
                && !path.startsWith("/courses/star")) {
            return false;
        }
        if (method == io.swagger.v3.oas.models.PathItem.HttpMethod.GET
                && path.startsWith("/blogs/")
                && !path.startsWith("/blogs/examine")
                && !path.startsWith("/blogs/myBlogs")
                && !path.startsWith("/blogs/draft")
                && !path.startsWith("/blogs/collect")) {
            return false;
        }
        return method != io.swagger.v3.oas.models.PathItem.HttpMethod.GET || !path.startsWith("/comments/");
    }

    /**
     * 声明通用错误响应的 code、data 和 msg 字段结构。
     *
     * @return 用于 ApiErrorResponse 组件的对象模式
     */
    private Schema<?> errorResponseSchema() {
        return new ObjectSchema()
                .addProperty("code", new IntegerSchema())
                .addProperty("data", new ObjectSchema())
                .addProperty("msg", new StringSchema());
    }
}
