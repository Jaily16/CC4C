package com.cc4c.shared;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.Set;

@Configuration
public class OpenApiConfiguration {

    @Bean
    OpenAPI cc4cOpenApi() {
        return new OpenAPI()
                .info(new Info()
                .title("CC4C API")
                .version("3")
                .description("CC4C modular monolith HTTP API"));
    }

    @Bean
    OpenApiCustomizer cc4cResponseDocumentation() {
        Set<String> createdPaths = Set.of(
                "/users/register",
                "/courses/module",
                "/courses/add",
                "/blogs/submit",
                "/courses/star/{userId}/{courseId}",
                "/blogs/collect/{uid}/{bid}",
                "/comments/course",
                "/comments/blog",
                "/comments/indirect");
        Map<String, String> errorDescriptions = Map.of(
                "400", "Invalid DTO, path or paging parameter",
                "401", "Authentication or cookie validation failed",
                "404", "Resource does not exist",
                "409", "Unique or state conflict",
                "422", "Referenced resource or business state is invalid",
                "500", "Unexpected server error");
        return openApi -> {
            Components components = openApi.getComponents();
            if (components == null) {
                components = new Components();
                openApi.setComponents(components);
            }
            components.addSchemas("ApiErrorResponse", errorResponseSchema());

            openApi.getPaths().forEach((path, item) ->
                    item.readOperationsMap().forEach((method, operation) -> {
                        if (method == io.swagger.v3.oas.models.PathItem.HttpMethod.POST
                                && createdPaths.contains(path)) {
                            ApiResponse createdResponse = operation.getResponses().remove("200");
                            if (createdResponse == null) {
                                createdResponse = new ApiResponse();
                            }
                            createdResponse.setDescription("Resource created");
                            operation.getResponses().addApiResponse("201", createdResponse);
                        }
                        errorDescriptions.forEach((status, description) ->
                                operation.getResponses().addApiResponse(
                                        status,
                                        new ApiResponse()
                                                .description(description)
                                                .content(new Content().addMediaType(
                                                        "application/json",
                                                        new io.swagger.v3.oas.models.media.MediaType()
                                                                .schema(new Schema<>().$ref(
                                                                        "#/components/schemas/ApiErrorResponse"))))));
                    }));
        };
    }

    private Schema<?> errorResponseSchema() {
        return new ObjectSchema()
                .addProperty("code", new IntegerSchema())
                .addProperty("data", new ObjectSchema())
                .addProperty("msg", new StringSchema());
    }
}