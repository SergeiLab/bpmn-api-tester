package ru.bankingapi.bpmntester.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import ru.bankingapi.bpmntester.domain.ApiEndpointInfo;

import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class ValidationService {

    private final ObjectMapper objectMapper;

    /**
     * Validate API response against expected schema
     */
    public List<String> validateResponse(
        ResponseEntity<String> response,
        ApiEndpointInfo endpointInfo
    ) {
        List<String> errors = new ArrayList<>();

        // Validate HTTP status
        if (!response.getStatusCode().is2xxSuccessful()) {
            errors.add("Expected 2xx status, got: " + response.getStatusCode());
        }

        // Validate response body
        if (response.getBody() == null || response.getBody().isBlank()) {
            if (!endpointInfo.getResponseSchema().isEmpty()) {
                errors.add("Expected response body but got empty response");
            }
            return errors;
        }

        try {
            JsonNode responseJson = objectMapper.readTree(response.getBody());
            
            // Validate against schema
            errors.addAll(validateJsonAgainstSchema(
                responseJson,
                endpointInfo.getResponseSchema()
            ));

        } catch (Exception e) {
            errors.add("Invalid JSON response: " + e.getMessage());
        }

        if (!errors.isEmpty()) {
            log.warn("Validation errors for {} {}: {}", 
                endpointInfo.getMethod(), 
                endpointInfo.getPath(), 
                errors);
        }

        return errors;
    }

    /**
     * Validate JSON against schema definition
     */
    private List<String> validateJsonAgainstSchema(
        JsonNode json,
        Map<String, Object> schema
    ) {
        List<String> errors = new ArrayList<>();

        if (schema.isEmpty()) {
            return errors;
        }

        String type = (String) schema.get("type");
        
        if ("object".equals(type)) {
            Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
            List<String> required = (List<String>) schema.get("required");

            if (properties != null) {
                properties.forEach((fieldName, fieldSchema) -> {
                    if (!json.has(fieldName)) {
                        if (required != null && required.contains(fieldName)) {
                            errors.add("Required field missing: " + fieldName);
                        }
                    } else {
                        List<String> fieldErrors = validateField(
                            json.get(fieldName),
                            (Map<String, Object>) fieldSchema,
                            fieldName
                        );
                        errors.addAll(fieldErrors);
                    }
                });
            }
        }

        return errors;
    }

    /**
     * Validate individual field
     */
    private List<String> validateField(
        JsonNode value,
        Map<String, Object> schema,
        String fieldPath
    ) {
        List<String> errors = new ArrayList<>();

        String type = (String) schema.get("type");
        if (type == null) {
            return errors;
        }

        // Type validation
        switch (type) {
            case "string":
                if (!value.isTextual()) {
                    errors.add(fieldPath + ": expected string, got " + value.getNodeType());
                } else {
                    validateStringConstraints(value.asText(), schema, fieldPath, errors);
                }
                break;
            case "integer":
                if (!value.isInt()) {
                    errors.add(fieldPath + ": expected integer, got " + value.getNodeType());
                } else {
                    validateNumericConstraints(value.asInt(), schema, fieldPath, errors);
                }
                break;
            case "number":
                if (!value.isNumber()) {
                    errors.add(fieldPath + ": expected number, got " + value.getNodeType());
                } else {
                    validateNumericConstraints(value.asDouble(), schema, fieldPath, errors);
                }
                break;
            case "boolean":
                if (!value.isBoolean()) {
                    errors.add(fieldPath + ": expected boolean, got " + value.getNodeType());
                }
                break;
            case "array":
                if (!value.isArray()) {
                    errors.add(fieldPath + ": expected array, got " + value.getNodeType());
                } else {
                    validateArrayConstraints(value, schema, fieldPath, errors);
                }
                break;
            case "object":
                if (!value.isObject()) {
                    errors.add(fieldPath + ": expected object, got " + value.getNodeType());
                } else {
                    errors.addAll(validateJsonAgainstSchema(value, schema));
                }
                break;
        }

        return errors;
    }

    /**
     * Validate string constraints
     */
    private void validateStringConstraints(
        String value,
        Map<String, Object> schema,
        String fieldPath,
        List<String> errors
    ) {
        Integer minLength = (Integer) schema.get("minLength");
        Integer maxLength = (Integer) schema.get("maxLength");
        String pattern = (String) schema.get("pattern");
        List<String> enumValues = (List<String>) schema.get("enum");

        if (minLength != null && value.length() < minLength) {
            errors.add(fieldPath + ": length " + value.length() + " < minimum " + minLength);
        }

        if (maxLength != null && value.length() > maxLength) {
            errors.add(fieldPath + ": length " + value.length() + " > maximum " + maxLength);
        }

        if (pattern != null && !value.matches(pattern)) {
            errors.add(fieldPath + ": value does not match pattern " + pattern);
        }

        if (enumValues != null && !enumValues.contains(value)) {
            errors.add(fieldPath + ": value not in allowed values " + enumValues);
        }
    }

    /**
     * Validate numeric constraints
     */
    private void validateNumericConstraints(
        Number value,
        Map<String, Object> schema,
        String fieldPath,
        List<String> errors
    ) {
        Number minimum = (Number) schema.get("minimum");
        Number maximum = (Number) schema.get("maximum");

        if (minimum != null && value.doubleValue() < minimum.doubleValue()) {
            errors.add(fieldPath + ": value " + value + " < minimum " + minimum);
        }

        if (maximum != null && value.doubleValue() > maximum.doubleValue()) {
            errors.add(fieldPath + ": value " + value + " > maximum " + maximum);
        }
    }

    /**
     * Validate array constraints
     */
    private void validateArrayConstraints(
        JsonNode array,
        Map<String, Object> schema,
        String fieldPath,
        List<String> errors
    ) {
        Integer minItems = (Integer) schema.get("minItems");
        Integer maxItems = (Integer) schema.get("maxItems");

        if (minItems != null && array.size() < minItems) {
            errors.add(fieldPath + ": array size " + array.size() + " < minimum " + minItems);
        }

        if (maxItems != null && array.size() > maxItems) {
            errors.add(fieldPath + ": array size " + array.size() + " > maximum " + maxItems);
        }

        // Validate items
        Map<String, Object> itemsSchema = (Map<String, Object>) schema.get("items");
        if (itemsSchema != null) {
            for (int i = 0; i < array.size(); i++) {
                errors.addAll(validateField(
                    array.get(i),
                    itemsSchema,
                    fieldPath + "[" + i + "]"
                ));
            }
        }
    }
}