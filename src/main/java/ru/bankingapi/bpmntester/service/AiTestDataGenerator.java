package ru.bankingapi.bpmntester.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.bankingapi.bpmntester.domain.ApiEndpointInfo;

import java.util.*;

@Service
@Slf4j
public class AiTestDataGenerator {

    private final ChatLanguageModel chatModel;
    private final ObjectMapper objectMapper;
    private final Random random = new Random();

    @Value("${ai.openai.api-key}")
    private String openAiApiKey;

    @Value("${ai.openai.model:gpt-4-turbo-preview}")
    private String model;

    public AiTestDataGenerator(ObjectMapper objectMapper,
                               @Value("${ai.openai.api-key}") String apiKey) {
        this.objectMapper = objectMapper;
        
        // Initialize AI model (can be replaced with Ollama for local)
        if (apiKey != null && !apiKey.isBlank()) {
            this.chatModel = OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName("gpt-4-turbo-preview")
                .temperature(0.7)
                .build();
            log.info("AI test data generator initialized with OpenAI");
        } else {
            this.chatModel = null;
            log.warn("AI model not configured, will use fallback data generation");
        }
    }

    /**
     * Generate test data for an endpoint using AI
     */
    public Map<String, Object> generateTestData(
        ApiEndpointInfo endpointInfo,
        Map<String, Object> contextData
    ) {
        if (chatModel != null) {
            return generateWithAi(endpointInfo, contextData);
        } else {
            return generateFallbackData(endpointInfo, contextData);
        }
    }

    /**
     * Generate test data using AI language model
     */
    private Map<String, Object> generateWithAi(
        ApiEndpointInfo endpointInfo,
        Map<String, Object> contextData
    ) {
        try {
            String prompt = buildGenerationPrompt(endpointInfo, contextData);
            
            log.debug("Generating test data with AI for endpoint: {} {}", 
                endpointInfo.getMethod(), endpointInfo.getPath());

            String response = chatModel.generate(prompt);
            
            // Extract JSON from response (AI might wrap it in markdown)
            String jsonContent = extractJsonFromResponse(response);
            
            Map<String, Object> generatedData = objectMapper.readValue(
                jsonContent, 
                Map.class
            );

            log.info("Successfully generated test data with AI for {}", endpointInfo.getPath());
            return generatedData;

        } catch (Exception e) {
            log.error("AI generation failed, falling back to rule-based generation", e);
            return generateFallbackData(endpointInfo, contextData);
        }
    }

    /**
     * Build prompt for AI model
     */
    private String buildGenerationPrompt(
        ApiEndpointInfo endpointInfo,
        Map<String, Object> contextData
    ) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("Generate realistic test data for a banking API endpoint.\n\n");
        prompt.append("Endpoint: ").append(endpointInfo.getMethod())
              .append(" ").append(endpointInfo.getPath()).append("\n");
        prompt.append("Description: ").append(endpointInfo.getDescription()).append("\n\n");
        
        if (!endpointInfo.getRequestSchema().isEmpty()) {
            prompt.append("Request Schema:\n");
            prompt.append(formatSchema(endpointInfo.getRequestSchema())).append("\n\n");
        }
        
        if (!endpointInfo.getRequiredFields().isEmpty()) {
            prompt.append("Required Fields: ")
                  .append(String.join(", ", endpointInfo.getRequiredFields()))
                  .append("\n\n");
        }
        
        if (!contextData.isEmpty()) {
            prompt.append("Context from previous steps (use these values when applicable):\n");
            contextData.forEach((key, value) -> 
                prompt.append("  ").append(key).append(": ").append(value).append("\n")
            );
            prompt.append("\n");
        }
        
        prompt.append("Generate ONLY valid JSON data that matches the schema. ");
        prompt.append("Use realistic banking data (account numbers, amounts, dates, etc.). ");
        prompt.append("If context data contains IDs or references, use them. ");
        prompt.append("Return ONLY the JSON object, no explanations.\n");
        
        return prompt.toString();
    }

    /**
     * Fallback data generation without AI
     */
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
            if (contextData != null && contextData.containsKey(fieldName)) {
                data.put(fieldName, contextData.get(fieldName));
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

    log.info("Generated fallback test data for {}", endpointInfo.getPath());
    return data;
}

private Map<String, Object> generateDefaultData(ApiEndpointInfo endpointInfo) {
    Map<String, Object> data = new HashMap<>();
    
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
        data.put("transactionId", "TXN" + String.format("%015d", random.nextInt(1000000000)));
    }
    
    if ("POST".equals(endpointInfo.getMethod()) || "PUT".equals(endpointInfo.getMethod())) {
        data.put("amount", random.nextInt(10000) + 100);
        data.put("currency", "RUB");
        data.put("description", "Test transaction");
    }
    
    return data;
}

    /**
     * Generate value for a specific field
     */
    private Object generateFieldValue(
        String fieldName, 
        Map<String, Object> schema,
        boolean isRequired
    ) {
        if (!isRequired && random.nextInt(10) < 3) {
            return null; // 30% chance of null for optional fields
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

    /**
     * Generate string value based on field name and format
     */
    private String generateStringValue(String fieldName, String format, Map<String, Object> schema) {
        String lowerName = fieldName.toLowerCase();

        // Banking-specific patterns
        if (lowerName.contains("account") && lowerName.contains("id")) {
            return "40817810" + "%012d".formatted(random.nextInt(1000000000));
        }
        if (lowerName.contains("card") && lowerName.contains("number")) {
            return "4276" + "%012d".formatted(random.nextInt(1000000000));
        }
        if (lowerName.contains("phone")) {
            return "+7" + "%010d".formatted(random.nextInt(1000000000));
        }
        if (lowerName.contains("email")) {
            return "test" + random.nextInt(1000) + "@example.com";
        }
        if (lowerName.contains("amount") || lowerName.contains("sum")) {
            return String.valueOf(random.nextInt(100000) + 100);
        }

        // Format-based generation
        if ("date".equals(format)) {
            return "2024-01-" + "%02d".formatted(random.nextInt(28) + 1);
        }
        if ("date-time".equals(format)) {
            return "2024-01-15T10:30:00Z";
        }
        if ("uuid".equals(format)) {
            return UUID.randomUUID().toString();
        }

        // Check enum values
        if (schema.containsKey("enum")) {
            List<String> enumValues = (List<String>) schema.get("enum");
            return enumValues.get(random.nextInt(enumValues.size()));
        }

        // Default
        return "test_" + fieldName + "_" + random.nextInt(1000);
    }

    /**
     * Generate integer value
     */
    private Integer generateIntegerValue(String fieldName, Map<String, Object> schema) {
        Integer min = (Integer) schema.get("minimum");
        Integer max = (Integer) schema.get("maximum");

        if (min != null && max != null) {
            return min + random.nextInt(max - min + 1);
        }

        return random.nextInt(10000);
    }

    /**
     * Generate number value
     */
    private Double generateNumberValue(String fieldName, Map<String, Object> schema) {
        Double min = (Double) schema.get("minimum");
        Double max = (Double) schema.get("maximum");

        if (min != null && max != null) {
            return min + (max - min) * random.nextDouble();
        }

        return random.nextDouble() * 10000;
    }

    /**
     * Generate array value
     */
    private List<Object> generateArrayValue(Map<String, Object> schema) {
        Map<String, Object> items = (Map<String, Object>) schema.get("items");
        if (items == null) {
            return Collections.emptyList();
        }

        int arraySize = random.nextInt(3) + 1; // 1-3 items
        List<Object> array = new ArrayList<>();

        for (int i = 0; i < arraySize; i++) {
            Object item = generateFieldValue("item", items, true);
            array.add(item);
        }

        return array;
    }

    /**
     * Generate object value
     */
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

    /**
     * Extract JSON from AI response (may be wrapped in markdown)
     */
    private String extractJsonFromResponse(String response) {
        response = response.trim();
        
        // Remove markdown code blocks
        if (response.startsWith("```json")) {
            response = response.substring(7);
        } else if (response.startsWith("```")) {
            response = response.substring(3);
        }
        
        if (response.endsWith("```")) {
            response = response.substring(0, response.length() - 3);
        }
        
        return response.trim();
    }

    /**
     * Format schema for prompt
     */
    private String formatSchema(Map<String, Object> schema) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(schema);
        } catch (Exception e) {
            return schema.toString();
        }
    }
}