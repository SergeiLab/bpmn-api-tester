package ru.bankingapi.bpmntester.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import ru.bankingapi.bpmntester.domain.ApiEndpointInfo;

import java.util.*;
import java.util.regex.*;

@Service
@Slf4j
public class AiTestDataGenerator {

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final Random random = new Random();

    @Value("${ai.provider:ollama}")
    private String aiProvider;

    @Value("${ai.ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${ai.ollama.model:llama3.2:3b}")
    private String ollamaModel;

    @Value("${ai.enabled:true}")
    private boolean aiEnabled;

    public AiTestDataGenerator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.restTemplate = new RestTemplate();
    }

    public Map<String, Object> generateTestData(
        ApiEndpointInfo endpointInfo,
        Map<String, Object> contextData
    ) {
        if (!aiEnabled) {
            log.info("AI disabled, using fallback generation");
            return generateFallbackData(endpointInfo, contextData);
        }

        if ("ollama".equals(aiProvider)) {
            try {
                return generateWithOllama(endpointInfo, contextData);
            } catch (Exception e) {
                log.warn("Ollama generation failed, using fallback: {}", e.getMessage());
                return generateFallbackData(endpointInfo, contextData);
            }
        }

        return generateFallbackData(endpointInfo, contextData);
    }

    private Map<String, Object> generateWithOllama(
        ApiEndpointInfo endpointInfo,
        Map<String, Object> contextData
    ) {
        try {
            String prompt = buildGenerationPrompt(endpointInfo, contextData);
            
            log.debug("Generating test data with Ollama for: {} {}", 
                endpointInfo.getMethod(), endpointInfo.getPath());

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", ollamaModel);
            requestBody.put("prompt", prompt);
            requestBody.put("stream", false);
            requestBody.put("format", "json");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                ollamaBaseUrl + "/api/generate",
                HttpMethod.POST,
                request,
                String.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode responseNode = objectMapper.readTree(response.getBody());
                String generatedText = responseNode.get("response").asText();
                
                Map<String, Object> generatedData = extractJsonFromResponse(generatedText);
                
                if (generatedData != null && !generatedData.isEmpty()) {
                    if (contextData != null && !contextData.isEmpty()) {
                        contextData.forEach(generatedData::putIfAbsent);
                    }
                    
                    log.info("Successfully generated test data with Ollama");
                    return generatedData;
                }
            }

            log.warn("Ollama returned empty data, using fallback");
            return generateFallbackData(endpointInfo, contextData);

        } catch (Exception e) {
            log.error("Ollama generation failed: {}", e.getMessage());
            return generateFallbackData(endpointInfo, contextData);
        }
    }

    private String buildGenerationPrompt(
        ApiEndpointInfo endpointInfo,
        Map<String, Object> contextData
    ) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("Generate realistic JSON test data for a banking API endpoint.\n\n");
        prompt.append("Endpoint: ").append(endpointInfo.getMethod())
              .append(" ").append(endpointInfo.getPath()).append("\n");
        
        if (endpointInfo.getDescription() != null && !endpointInfo.getDescription().isEmpty()) {
            prompt.append("Description: ").append(endpointInfo.getDescription()).append("\n");
        }
        
        if (endpointInfo.getRequestSchema() != null && !endpointInfo.getRequestSchema().isEmpty()) {
            prompt.append("\nExpected fields based on schema:\n");
            extractFieldsFromSchema(endpointInfo.getRequestSchema(), prompt, "");
        }
        
        if (contextData != null && !contextData.isEmpty()) {
            prompt.append("\nContext from previous steps (MUST use these values):\n");
            contextData.forEach((key, value) -> 
                prompt.append("  ").append(key).append(": ").append(value).append("\n")
            );
        }
        
        prompt.append("\nGenerate ONLY valid JSON data. Use realistic banking values:\n");
        prompt.append("- Account numbers: 20 digits starting with 40817\n");
        prompt.append("- Card numbers: 16 digits starting with 4276\n");
        prompt.append("- Amounts: positive numbers with 2 decimals\n");
        prompt.append("- Currency: RUB\n");
        prompt.append("- Dates: ISO 8601 format\n");
        prompt.append("\nReturn ONLY the JSON object, no explanations:");
        
        return prompt.toString();
    }

    private void extractFieldsFromSchema(Map<String, Object> schema, StringBuilder prompt, String prefix) {
        if (schema.containsKey("properties")) {
            Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
            properties.forEach((fieldName, fieldSchema) -> {
                Map<String, Object> fieldSchemaMap = (Map<String, Object>) fieldSchema;
                String type = (String) fieldSchemaMap.getOrDefault("type", "string");
                String description = (String) fieldSchemaMap.get("description");
                
                prompt.append("  ").append(prefix).append(fieldName)
                      .append(" (").append(type).append(")");
                
                if (description != null) {
                    prompt.append(": ").append(description);
                }
                prompt.append("\n");
            });
        }
    }

    private Map<String, Object> extractJsonFromResponse(String response) {
        if (response == null || response.isEmpty()) {
            return new HashMap<>();
        }

        response = response.trim();
        
        if (response.startsWith("```json")) {
            response = response.substring(7);
        } else if (response.startsWith("```")) {
            response = response.substring(3);
        }
        
        if (response.endsWith("```")) {
            response = response.substring(0, response.length() - 3);
        }
        
        response = response.trim();
        
        Pattern jsonPattern = Pattern.compile("\\{[^{}]*(?:\\{[^{}]*\\}[^{}]*)*\\}");
        Matcher matcher = jsonPattern.matcher(response);
        
        if (matcher.find()) {
            String jsonStr = matcher.group();
            try {
                return objectMapper.readValue(jsonStr, Map.class);
            } catch (Exception e) {
                log.warn("Failed to parse extracted JSON: {}", e.getMessage());
            }
        }

        try {
            return objectMapper.readValue(response, Map.class);
        } catch (Exception e) {
            log.warn("Failed to parse response as JSON: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    private Map<String, Object> generateFallbackData(
        ApiEndpointInfo endpointInfo,
        Map<String, Object> contextData
    ) {
        Map<String, Object> data = new HashMap<>();
        
        if (contextData != null && !contextData.isEmpty()) {
            data.putAll(contextData);
        }
        
        Map<String, Object> schema = endpointInfo.getRequestSchema();
        
        if (schema == null || schema.isEmpty()) {
            data.putAll(generateDefaultData(endpointInfo));
            log.info("Generated default data for {}", endpointInfo.getPath());
            return data;
        }

        if (schema.containsKey("properties")) {
            Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
            
            properties.forEach((fieldName, fieldSchema) -> {
                if (data.containsKey(fieldName)) {
                    return;
                }

                Object value = generateFieldValue(
                    fieldName, 
                    (Map<String, Object>) fieldSchema,
                    endpointInfo.getRequiredFields() != null && 
                    endpointInfo.getRequiredFields().contains(fieldName)
                );
                
                if (value != null) {
                    data.put(fieldName, value);
                }
            });
        } else {
            data.putAll(generateDefaultData(endpointInfo));
        }

        log.info("Generated fallback data for {}", endpointInfo.getPath());
        return data;
    }

    private Map<String, Object> generateDefaultData(ApiEndpointInfo endpointInfo) {
        Map<String, Object> data = new HashMap<>();
        
        if (endpointInfo == null || endpointInfo.getPath() == null) {
            return data;
        }

        String path = endpointInfo.getPath();
        
        if (path.contains("{externalAccountID}")) {
            data.put("externalAccountID", "40817810" + String.format("%012d", random.nextInt(1000000000)));
        }
        if (path.contains("{accountId}")) {
            data.put("accountId", "40817810" + String.format("%012d", random.nextInt(1000000000)));
        }
        if (path.contains("{cardId}")) {
            data.put("cardId", "4276" + String.format("%012d", random.nextInt(1000000000)));
        }
        if (path.contains("{transactionId}")) {
            data.put("transactionId", "TXN" + UUID.randomUUID().toString().replace("-", "").substring(0, 15));
        }
        
        if (endpointInfo.getMethod() != null) {
            String method = endpointInfo.getMethod().toUpperCase();
            if ("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method)) {
                data.put("amount", random.nextInt(10000) + 100);
                data.put("currency", "RUB");
                data.put("description", "Test transaction");
            }
        }
        
        return data;
    }

    private Object generateFieldValue(
        String fieldName, 
        Map<String, Object> schema,
        boolean isRequired
    ) {
        if (schema == null) {
            return generateDefaultFieldValue(fieldName);
        }

        if (!isRequired && random.nextInt(10) < 3) {
            return null;
        }

        String type = (String) schema.getOrDefault("type", "string");
        String format = (String) schema.get("format");

        return switch (type) {
            case "string" -> generateStringValue(fieldName, format, schema);
            case "integer" -> generateIntegerValue(fieldName, schema);
            case "number" -> generateNumberValue(fieldName, schema);
            case "boolean" -> random.nextBoolean();
            case "array" -> generateArrayValue(schema);
            case "object" -> generateObjectValue(schema);
            default -> generateStringValue(fieldName, format, schema);
        };
    }

    private Object generateDefaultFieldValue(String fieldName) {
        String lowerName = fieldName.toLowerCase();
        
        if (lowerName.contains("account") && lowerName.contains("id")) {
            return "40817810" + String.format("%012d", random.nextInt(1000000000));
        }
        if (lowerName.contains("card")) {
            return "4276" + String.format("%012d", random.nextInt(1000000000));
        }
        if (lowerName.contains("amount")) {
            return random.nextInt(100000) + 100;
        }
        if (lowerName.contains("currency")) {
            return "RUB";
        }
        
        return "test_" + fieldName;
    }

    private String generateStringValue(String fieldName, String format, Map<String, Object> schema) {
        String lowerName = fieldName.toLowerCase();

        if (lowerName.contains("account") && lowerName.contains("id")) {
            return "40817810" + String.format("%012d", random.nextInt(1000000000));
        }
        if (lowerName.contains("card") && lowerName.contains("number")) {
            return "4276" + String.format("%012d", random.nextInt(1000000000));
        }
        if (lowerName.contains("phone")) {
            return "+7" + String.format("%010d", random.nextInt(1000000000));
        }
        if (lowerName.contains("email")) {
            return "test" + random.nextInt(1000) + "@example.com";
        }
        if (lowerName.contains("amount") || lowerName.contains("sum")) {
            return String.valueOf(random.nextInt(100000) + 100);
        }

        if ("date".equals(format)) {
            return "2024-" + String.format("%02d", random.nextInt(12) + 1) + "-" + String.format("%02d", random.nextInt(28) + 1);
        }
        if ("date-time".equals(format)) {
            return "2024-01-15T10:30:00Z";
        }
        if ("uuid".equals(format)) {
            return UUID.randomUUID().toString();
        }

        if (schema.containsKey("enum")) {
            List<String> enumValues = (List<String>) schema.get("enum");
            return enumValues.get(random.nextInt(enumValues.size()));
        }

        return "test_" + fieldName + "_" + random.nextInt(1000);
    }

    private Integer generateIntegerValue(String fieldName, Map<String, Object> schema) {
        Integer min = (Integer) schema.get("minimum");
        Integer max = (Integer) schema.get("maximum");

        if (min != null && max != null) {
            return min + random.nextInt(max - min + 1);
        }

        return random.nextInt(10000);
    }

    private Double generateNumberValue(String fieldName, Map<String, Object> schema) {
        Number min = (Number) schema.get("minimum");
        Number max = (Number) schema.get("maximum");

        if (min != null && max != null) {
            return min.doubleValue() + (max.doubleValue() - min.doubleValue()) * random.nextDouble();
        }

        return Math.round((random.nextDouble() * 10000) * 100.0) / 100.0;
    }

    private List<Object> generateArrayValue(Map<String, Object> schema) {
        Map<String, Object> items = (Map<String, Object>) schema.get("items");
        if (items == null) {
            return Collections.emptyList();
        }

        int arraySize = random.nextInt(3) + 1;
        List<Object> array = new ArrayList<>();

        for (int i = 0; i < arraySize; i++) {
            Object item = generateFieldValue("item", items, true);
            array.add(item);
        }

        return array;
    }

    private Map<String, Object> generateObjectValue(Map<String, Object> schema) {
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        if (properties == null) {
            return Collections.emptyMap();
        }

        Map<String, Object> obj = new HashMap<>();
        properties.forEach((name, propSchema) -> {
            Object value = generateFieldValue(name, (Map<String, Object>) propSchema, false);
            if (value != null) {
                obj.put(name, value);
            }
        });

        return obj;
    }

    public boolean isAiAvailable() {
        if (!aiEnabled) {
            return false;
        }

        if ("ollama".equals(aiProvider)) {
            try {
                ResponseEntity<String> response = restTemplate.getForEntity(
                    ollamaBaseUrl + "/api/tags",
                    String.class
                );
                return response.getStatusCode().is2xxSuccessful();
            } catch (Exception e) {
                log.warn("Ollama not available: {}", e.getMessage());
                return false;
            }
        }

        return false;
    }
}