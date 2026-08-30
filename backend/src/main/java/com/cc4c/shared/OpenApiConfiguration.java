package com.cc4c.shared;

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

@Configuration
/** OpenApiConfiguration 负责组装运行时基础设施，并明确其边界和故障处理策略。 */
public class OpenApiConfiguration {

    @Bean
    OpenAPI cc4cOpenApi() {
        return new OpenAPI()
                .info(new Info().title("CC4C API").version("3").description("CC4C modular monolith HTTP API"));
    }

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
                if (requiresSession(path, method)) {
                    security.addList("CC4C_SESSION");
                }
                if (requiresCsrf(method)) {
                    security.addList("X-XSRF-TOKEN");
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

    private boolean requiresCsrf(io.swagger.v3.oas.models.PathItem.HttpMethod method) {
        return method == io.swagger.v3.oas.models.PathItem.HttpMethod.POST
                || method == io.swagger.v3.oas.models.PathItem.HttpMethod.PUT
                || method == io.swagger.v3.oas.models.PathItem.HttpMethod.DELETE
                || method == io.swagger.v3.oas.models.PathItem.HttpMethod.PATCH;
    }

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
        if (path.startsWith("/test/")) {
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

    private Schema<?> errorResponseSchema() {
        return new ObjectSchema()
                .addProperty("code", new IntegerSchema())
                .addProperty("data", new ObjectSchema())
                .addProperty("msg", new StringSchema());
    }
}
