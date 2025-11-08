package ru.bankingapi.bpmntester.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.bankingapi.bpmntester.domain.ApiEndpointInfo;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class OpenApiParserService {

    private final ObjectMapper objectMapper;

    public OpenAPI parseOpenApiSpec(String specContent) {
        try {
            ParseOptions options = new ParseOptions();
            options.setResolve(true);
            options.setResolveFully(true);

            OpenAPI openAPI = new OpenAPIV3Parser().readContents(specContent, null, options).getOpenAPI();

            if (openAPI == null) {
                throw new IllegalArgumentException("Failed to parse OpenAPI specification");
            }

            log.info("Successfully parsed OpenAPI spec: {} v{}",
                openAPI.getInfo().getTitle(),
                openAPI.getInfo().getVersion());

            return openAPI;

        } catch (Exception e) {
            log.error("Failed to parse OpenAPI spec", e);
            throw new RuntimeException("Cannot parse OpenAPI specification: " + e.getMessage(), e);
        }
    }

    public ApiEndpointInfo extractEndpointInfo(OpenAPI openAPI, String path, String method) {
        if (openAPI == null || openAPI.getPaths() == null) {
            log.warn("OpenAPI or paths is null");
            return createDefaultEndpointInfo(path, method);
        }

        PathItem pathItem = openAPI.getPaths().get(path);
        if (pathItem == null) {
            log.warn("Path not found: {}", path);
            return createDefaultEndpointInfo(path, method);
        }

        Operation operation = getOperation(pathItem, method);
        if (operation == null) {
            log.warn("Method {} not found for path {}", method, path);
            return createDefaultEndpointInfo(path, method);
        }

        return ApiEndpointInfo.builder()
            .path(path)
            .method(method.toUpperCase())
            .operationId(operation.getOperationId())
            .summary(operation.getSummary())
            .description(operation.getDescription())
            .requestSchema(extractRequestSchema(operation))
            .responseSchema(extractResponseSchema(operation))
            .requiredFields(extractRequiredFields(operation))
            .build();
    }

    public ApiEndpointInfo findBestMatchingEndpoint(
        OpenAPI openAPI, 
        String taskName, 
        String taskDescription
    ) {
        if (openAPI == null || openAPI.getPaths() == null) {
            log.warn("OpenAPI or paths is null");
            return null;
        }

        List<ApiEndpointInfo> allEndpoints = extractAllEndpoints(openAPI);
        
        if (allEndpoints.isEmpty()) {
            return null;
        }

        String searchText = (taskName + " " + taskDescription).toLowerCase();
        
        return allEndpoints.stream()
            .max(Comparator.comparingInt(endpoint -> 
                calculateSimilarityScore(searchText, endpoint)
            ))
            .orElse(null);
    }

    public List<ApiEndpointInfo> extractAllEndpoints(OpenAPI openAPI) {
        if (openAPI == null || openAPI.getPaths() == null) {
            log.warn("OpenAPI or paths is null");
            return Collections.emptyList();
        }

        List<ApiEndpointInfo> endpoints = new ArrayList<>();

        openAPI.getPaths().forEach((path, pathItem) -> {
            if (pathItem == null || pathItem.readOperationsMap() == null) {
                return;
            }

            pathItem.readOperationsMap().forEach((httpMethod, operation) -> {
                try {
                    ApiEndpointInfo info = ApiEndpointInfo.builder()
                        .path(path)
                        .method(httpMethod.name())
                        .operationId(operation.getOperationId())
                        .summary(operation.getSummary())
                        .description(operation.getDescription())
                        .requestSchema(extractRequestSchema(operation))
                        .responseSchema(extractResponseSchema(operation))
                        .requiredFields(extractRequiredFields(operation))
                        .build();
                    
                    endpoints.add(info);
                } catch (Exception e) {
                    log.warn("Failed to extract endpoint info for {} {}", httpMethod, path, e);
                }
            });
        });

        log.info("Extracted {} endpoints from OpenAPI spec", endpoints.size());
        return endpoints;
    }

    private Operation getOperation(PathItem pathItem, String method) {
        if (pathItem == null || method == null) {
            return null;
        }

        return switch (method.toUpperCase()) {
            case "GET" -> pathItem.getGet();
            case "POST" -> pathItem.getPost();
            case "PUT" -> pathItem.getPut();
            case "DELETE" -> pathItem.getDelete();
            case "PATCH" -> pathItem.getPatch();
            default -> null;
        };
    }

    private Map<String, Object> extractRequestSchema(Operation operation) {
        if (operation == null || operation.getRequestBody() == null) {
            return new HashMap<>();
        }

        Content content = operation.getRequestBody().getContent();
        if (content == null) {
            return new HashMap<>();
        }

        MediaType mediaType = content.get("application/json");
        if (mediaType == null || mediaType.getSchema() == null) {
            return new HashMap<>();
        }

        return schemaToMap(mediaType.getSchema());
    }

    private Map<String, Object> extractResponseSchema(Operation operation) {
        if (operation == null || operation.getResponses() == null) {
            return new HashMap<>();
        }

        ApiResponse response = operation.getResponses().get("200");
        if (response == null) {
            response = operation.getResponses().get("201");
        }

        if (response == null || response.getContent() == null) {
            return new HashMap<>();
        }

        MediaType mediaType = response.getContent().get("application/json");
        if (mediaType == null || mediaType.getSchema() == null) {
            return new HashMap<>();
        }

        return schemaToMap(mediaType.getSchema());
    }

    private List<String> extractRequiredFields(Operation operation) {
        List<String> required = new ArrayList<>();

        if (operation == null) {
            return required;
        }

        if (operation.getRequestBody() != null && 
            operation.getRequestBody().getContent() != null) {
            
            MediaType mediaType = operation.getRequestBody()
                .getContent()
                .get("application/json");
            
            if (mediaType != null && mediaType.getSchema() != null) {
                Schema<?> schema = mediaType.getSchema();
                if (schema.getRequired() != null) {
                    required.addAll(schema.getRequired());
                }
            }
        }

        if (operation.getParameters() != null) {
            required.addAll(
                operation.getParameters().stream()
                    .filter(p -> p != null && Boolean.TRUE.equals(p.getRequired()))
                    .map(Parameter::getName)
                    .collect(Collectors.toList())
            );
        }

        return required;
    }

    private Map<String, Object> schemaToMap(Schema<?> schema) {
        if (schema == null) {
            return new HashMap<>();
        }

        try {
            String json = objectMapper.writeValueAsString(schema);
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            log.warn("Failed to convert schema to map", e);
            return new HashMap<>();
        }
    }

    private int calculateSimilarityScore(String searchText, ApiEndpointInfo endpoint) {
        int score = 0;
        
        String endpointText = String.format("%s %s %s %s",
            endpoint.getPath() != null ? endpoint.getPath() : "",
            endpoint.getOperationId() != null ? endpoint.getOperationId() : "",
            endpoint.getSummary() != null ? endpoint.getSummary() : "",
            endpoint.getDescription() != null ? endpoint.getDescription() : ""
        ).toLowerCase();

        String[] searchWords = searchText.split("\\s+");
        for (String word : searchWords) {
            if (word.length() > 3 && endpointText.contains(word)) {
                score += 10;
            }
        }

        if (endpoint.getOperationId() != null && 
            searchText.contains(endpoint.getOperationId().toLowerCase())) {
            score += 50;
        }

        return score;
    }

    private ApiEndpointInfo createDefaultEndpointInfo(String path, String method) {
        return ApiEndpointInfo.builder()
            .path(path != null ? path : "/unknown")
            .method(method != null ? method.toUpperCase() : "GET")
            .operationId("unknown")
            .summary("")
            .description("")
            .requestSchema(new HashMap<>())
            .responseSchema(new HashMap<>())
            .requiredFields(new ArrayList<>())
            .build();
    }
}