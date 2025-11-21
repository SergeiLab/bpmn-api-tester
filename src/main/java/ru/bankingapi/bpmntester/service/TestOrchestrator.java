package ru.bankingapi.bpmntester.service;

import java.util.UUID;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import ru.bankingapi.bpmntester.domain.*;
import ru.bankingapi.bpmntester.repository.*;

import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class TestOrchestrator {

    @Qualifier("standardRestTemplate")
    private final RestTemplate standardRestTemplate;
    
    @Qualifier("gostRestTemplate")
    private final RestTemplate gostRestTemplate;
    
    private final OAuth2Service oauth2Service;
    private final AiTestDataGenerator aiTestDataGenerator;
    private final ValidationService validationService;
    private final TestExecutionRepository executionRepository;
    private final StepExecutionResultRepository stepResultRepository;
    private final ObjectMapper objectMapper;
    private final EndpointMappingService endpointMappingService;

    @Value("${banking-api.base-url}")
    private String baseUrl;

    @Value("${banking-api.gost-base-url}")
    private String gostBaseUrl;

    @Value("${test-execution.timeout-seconds:30}")
    private int timeoutSeconds;

    public TestExecution executeProcess(
        BusinessProcess process,
        ExecutionMode mode,
        Map<String, Object> initialContext,
        boolean generateTestData
    ) {
        log.info("Starting test execution for process '{}' in {} mode", 
            process.getName(), mode);

        TestExecution execution = TestExecution.builder()
            .businessProcess(process)
            .mode(mode)
            .status(ExecutionStatus.RUNNING)
            .startedAt(LocalDateTime.now())
            .stepResults(new ArrayList<>())
            .build();

        execution = executionRepository.save(execution);

        try {
            Map<String, Object> executionContext = new HashMap<>();
            if (initialContext != null) {
                executionContext.putAll(initialContext);
            }

            RestTemplate restTemplate = selectRestTemplate(mode);
            String apiBaseUrl = selectBaseUrl(mode);

            for (ProcessStep step : process.getSteps()) {
                StepExecutionResult result = executeStep(
                    step,
                    restTemplate,
                    apiBaseUrl,
                    executionContext,
                    generateTestData
                );

                result.setTestExecution(execution);
                result = stepResultRepository.save(result);
                execution.getStepResults().add(result);

                if (result.getStatus() != StepStatus.SUCCESS) {
                    log.warn("Step {} failed, stopping execution", step.getStepName());
                    break;
                }

                extractContextData(result, executionContext);
            }

            boolean allSuccess = execution.getStepResults().stream()
                .allMatch(r -> r.getStatus() == StepStatus.SUCCESS);

            execution.setStatus(allSuccess ? ExecutionStatus.COMPLETED : ExecutionStatus.FAILED);
            execution.setCompletedAt(LocalDateTime.now());

            if (!allSuccess) {
                execution.setErrorSummary(generateErrorSummary(execution));
            }

            log.info("Test execution completed with status: {}", execution.getStatus());

        } catch (Exception e) {
            log.error("Test execution failed with exception", e);
            execution.setStatus(ExecutionStatus.FAILED);
            execution.setErrorSummary("Execution failed: " + e.getMessage());
            execution.setCompletedAt(LocalDateTime.now());
        }

        return executionRepository.save(execution);
    }

    private StepExecutionResult executeStep(
        ProcessStep step,
        RestTemplate restTemplate,
        String apiBaseUrl,
        Map<String, Object> context,
        boolean generateTestData
    ) {
        log.info("Executing step: {} - {} {}", 
            step.getStepName(), step.getHttpMethod(), step.getApiEndpoint());

        long startTime = System.currentTimeMillis();

        StepExecutionResult result = StepExecutionResult.builder()
            .processStep(step)
            .executionOrder(step.getStepOrder())
            .status(StepStatus.SUCCESS)
            .executedAt(LocalDateTime.now())
            .build();

        try {
            if (isOAuth2Endpoint(step)) {
                return handleOAuth2Authentication(step, context, startTime);
            }

            // --- ОБНОВЛЕННЫЙ БЛОК ГЕНЕРАЦИИ ДАННЫХ ---
            ApiEndpointInfo endpointInfo = parseEndpointInfo(step);

            String originalEndpoint = step.getApiEndpoint();
            String mappedEndpoint = endpointMappingService.mapEndpoint(originalEndpoint);

            if (!originalEndpoint.equals(mappedEndpoint)) {
                log.info("Endpoint mapped: {} -> {}", originalEndpoint, mappedEndpoint);
            }

            boolean isRedemption = mappedEndpoint.contains("redemption") && "POST".equals(step.getHttpMethod());

            log.info(">>> Step: {}, isRedemption: {}, generateTestData: {}", 
                step.getStepName(), isRedemption, generateTestData);

            Map<String, Object> requestData = new HashMap<>();

            // CRITICAL: Always generate for redemption POST
            if (isRedemption) {
                log.info(">>> Generating redemption data (forced)");
                
                Map<String, Object> innerData = new HashMap<>();
                innerData.put("redemptionReferenceNumber", UUID.randomUUID().toString());
                innerData.put("redemptionAmount", 50.0);
                innerData.put("valuePerPoint", 0.01);
                innerData.put("programId", "A7DV56B");
                innerData.put("catalogId", "C9AP78DS9K");
                
                requestData.put("data", innerData);
                
                log.info(">>> Generated redemption body: {}", requestData);
                
            } else if (generateTestData) {
                log.info(">>> Calling AI generator for non-redemption request");
                Map<String, Object> generatedData = aiTestDataGenerator.generateTestData(endpointInfo, context);
                if (generatedData != null && !generatedData.isEmpty()) {
                    requestData.putAll(generatedData);
                    log.info("Added generated data with keys: {}", generatedData.keySet());
                }
            }

            // Only add context for NON-redemption requests
            if (context != null && !isRedemption) {
                requestData.putAll(context);
                log.debug("Added context data");
            }

            String url = buildUrl(apiBaseUrl, mappedEndpoint, requestData);
            // --- КОНЕЦ ОБНОВЛЕННОГО БЛОКА ГЕНЕРАЦИИ ДАННЫХ ---

            HttpHeaders headers = oauth2Service.createAuthHeaders();

            // Add required headers for Rewards Pay API
            if (mappedEndpoint.contains("/api/rb/rewardsPay")) {
                headers.set("Correlation-ID", UUID.randomUUID().toString());
                if ("POST".equals(step.getHttpMethod())) {
                    headers.set("Content-Type", "application/json");
                }
                log.debug("Added Rewards API headers: Correlation-ID={}", headers.getFirst("Correlation-ID"));
            }
            
            String requestBody = null;
            if ("POST".equals(step.getHttpMethod()) || "PUT".equals(step.getHttpMethod()) || "PATCH".equals(step.getHttpMethod())) {
                requestBody = objectMapper.writeValueAsString(requestData);
            }
            
            // ДОБАВЛЕННЫЙ БЛОК ЛОГА
            log.info("=== REQUEST DEBUG ===");
            log.info("URL: {}", url);
            log.info("Method: {}", step.getHttpMethod());
            log.info("Request Body: {}", requestBody);
            log.info("Request Data Map: {}", requestData);
            log.info("====================");
            
            HttpEntity<String> request = new HttpEntity<>(requestBody, headers);
            result.setRequestPayload(requestBody != null ? requestBody : "{}");

            log.debug("Sending {} request to {} with Authorization: Bearer ***", step.getHttpMethod(), url);

            ResponseEntity<String> response = executeHttpRequest(
                restTemplate,
                url,
                HttpMethod.valueOf(step.getHttpMethod()),
                request
            );

            result.setHttpStatusCode(response.getStatusCode().value());
            result.setResponsePayload(response.getBody());

            List<String> validationErrors = validationService.validateResponse(
                response,
                endpointInfo
            );

            if (!validationErrors.isEmpty()) {
                result.setStatus(StepStatus.VALIDATION_ERROR);
                result.setValidationErrors(objectMapper.writeValueAsString(validationErrors));
            }

        } catch (HttpClientErrorException | HttpServerErrorException e) {
            result.setStatus(StepStatus.FAILED);
            result.setHttpStatusCode(e.getStatusCode().value());
            result.setErrorMessage(e.getMessage());
            result.setResponsePayload(e.getResponseBodyAsString());
            log.error("HTTP error in step {}: {} - {}", step.getStepName(), e.getStatusCode(), e.getMessage());

        } catch (Exception e) {
            result.setStatus(StepStatus.FAILED);
            result.setErrorMessage(e.getMessage());
            log.error("Error executing step {}", step.getStepName(), e);
        }

        result.setExecutionTimeMs(System.currentTimeMillis() - startTime);
        return result;
    }

    private boolean isOAuth2Endpoint(ProcessStep step) {
        if (step.getApiEndpoint() == null) {
            return false;
        }
        
        String endpoint = step.getApiEndpoint().toLowerCase();
        String name = step.getStepName() != null ? step.getStepName().toLowerCase() : "";
        
        return endpoint.contains("/auth/") ||
               endpoint.equals("/auth/bank-token") ||
               name.contains("authentication") ||
               name.contains("auth");
    }

    private StepExecutionResult handleOAuth2Authentication(
        ProcessStep step,
        Map<String, Object> context,
        long startTime
    ) {
        log.info("Handling OAuth2 authentication step");

        StepExecutionResult result = StepExecutionResult.builder()
            .processStep(step)
            .executionOrder(step.getStepOrder())
            .status(StepStatus.SUCCESS)
            .executedAt(LocalDateTime.now())
            .build();

        try {
            String accessToken = oauth2Service.getAccessToken();
            
            Map<String, Object> requestData = new HashMap<>();
            requestData.put("grant_type", "client_credentials");
            requestData.put("client_id", "team112");
            requestData.put("client_secret", "***hidden***");
            
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("access_token", accessToken.substring(0, Math.min(50, accessToken.length())) + "...");
            responseData.put("token_type", "Bearer");
            responseData.put("expires_in", 3600);
            
            result.setRequestPayload(objectMapper.writeValueAsString(requestData));
            result.setResponsePayload(objectMapper.writeValueAsString(responseData));
            result.setHttpStatusCode(200);
            
            log.info("OAuth2 authentication successful, token cached in OAuth2Service");
            
        } catch (Exception e) {
            result.setStatus(StepStatus.FAILED);
            result.setErrorMessage("OAuth2 authentication failed: " + e.getMessage());
            log.error("OAuth2 authentication failed", e);
        }

        result.setExecutionTimeMs(System.currentTimeMillis() - startTime);
        return result;
    }

    private ResponseEntity<String> executeHttpRequest(
        RestTemplate restTemplate,
        String url,
        HttpMethod method,
        HttpEntity<String> request
    ) {
        log.debug("Executing {} request to: {}", method, url);
        return restTemplate.exchange(url, method, request, String.class);
    }

    private void extractContextData(
        StepExecutionResult result,
        Map<String, Object> context
    ) {
        if (result.getResponsePayload() == null || result.getResponsePayload().isBlank()) {
            return;
        }

        try {
            Map<String, Object> responseData = objectMapper.readValue(
                result.getResponsePayload(),
                Map.class
            );

            extractField(responseData, "id", context);
            extractField(responseData, "accountId", context);
            extractField(responseData, "orderId", context);
            extractField(responseData, "transactionId", context);
            extractField(responseData, "externalAccountId", context);
            extractField(responseData, "externalAccountID", context);

            String stepPrefix = result.getProcessStep().getStepId() + "_";
            responseData.forEach((key, value) -> 
                context.put(stepPrefix + key, value)
            );

        } catch (Exception e) {
            log.warn("Failed to extract context from response", e);
        }
    }

    private void extractField(Map<String, Object> data, String field, Map<String, Object> context) {
        if (data != null && data.containsKey(field)) {
            context.put(field, data.get(field));
            log.debug("Extracted field '{}' = {}", field, data.get(field));
        }
    }

    private ApiEndpointInfo parseEndpointInfo(ProcessStep step) {
        try {
            if (step.getOpenApiSpec() != null && !step.getOpenApiSpec().isBlank()) {
                return objectMapper.readValue(step.getOpenApiSpec(), ApiEndpointInfo.class);
            }
        } catch (Exception e) {
            log.warn("Failed to parse endpoint info from step", e);
        }

        return ApiEndpointInfo.builder()
            .path(step.getApiEndpoint() != null ? step.getApiEndpoint() : "/unknown")
            .method(step.getHttpMethod() != null ? step.getHttpMethod() : "GET")
            .operationId(step.getStepId() != null ? step.getStepId() : "unknown")
            .summary(step.getStepName() != null ? step.getStepName() : "")
            .description("")
            .requestSchema(new HashMap<>())
            .responseSchema(new HashMap<>())
            .requiredFields(new ArrayList<>())
            .build();
    }

    private String buildUrl(
        String baseUrl,
        String path,
        Map<String, Object> data
    ) {
        if (path == null || path.isEmpty()) {
            return baseUrl;
        }
        String url = baseUrl + path;
        // Replace path variables with data from context
        if (data != null && !data.isEmpty()) {
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                String placeholder = "{" + entry.getKey() + "}";
                if (url.contains(placeholder)) {
                    url = url.replace(placeholder, String.valueOf(entry.getValue()));
                    log.debug("Replaced {} with {}", placeholder, entry.getValue());
                }
            }
        }
        
        // If there are still unresolved path variables, generate UUIDs for them
        Pattern pattern = Pattern.compile("\\{([^}]+)\\}");
        Matcher matcher = pattern.matcher(url);
        
        String tempUrl = url;
        List<String> toReplace = new ArrayList<>();
        
        // Find all unresolved variables first
        Matcher findMatcher = pattern.matcher(tempUrl);
        while (findMatcher.find()) {
            toReplace.add(findMatcher.group(1));
        }
        
        for (String varName : toReplace) {
            if (tempUrl.contains("{" + varName + "}")) {
                String replacement;
                
                // Generate appropriate value based on variable name
                if (varName.contains("account") || varName.contains("Account")) {
                    replacement = UUID.randomUUID().toString();
                    log.debug("Auto-generated UUID for {}: {}", varName, replacement);
                } else if (varName.contains("id") || varName.contains("ID")) {
                    replacement = UUID.randomUUID().toString();
                    log.debug("Auto-generated UUID for {}: {}", varName, replacement);
                } else {
                    replacement = "auto-" + UUID.randomUUID().toString().substring(0, 8);
                    log.warn("Unknown path variable {}, using: {}", varName, replacement);
                }
                
                tempUrl = tempUrl.replace("{" + varName + "}", replacement);
                
                // Also store in context for potential future use
                if (data != null) {
                    data.put(varName, replacement);
                }
            }
        }
        
        return tempUrl;
    }

    private RestTemplate selectRestTemplate(ExecutionMode mode) {
        return mode == ExecutionMode.GOST ? gostRestTemplate : standardRestTemplate;
    }

    private String selectBaseUrl(ExecutionMode mode) {
        return mode == ExecutionMode.GOST ? gostBaseUrl : baseUrl;
    }

    private String generateErrorSummary(TestExecution execution) {
        StringBuilder summary = new StringBuilder();
        summary.append("Execution failed with the following errors:\n\n");

        execution.getStepResults().stream()
            .filter(r -> r.getStatus() != StepStatus.SUCCESS)
            .forEach(r -> {
                summary.append("Step: ").append(r.getProcessStep().getStepName()).append("\n");
                summary.append("Status: ").append(r.getStatus()).append("\n");
                if (r.getErrorMessage() != null) {
                    summary.append("Error: ").append(r.getErrorMessage()).append("\n");
                }
                if (r.getValidationErrors() != null) {
                    summary.append("Validation Errors: ").append(r.getValidationErrors()).append("\n");
                }
                summary.append("\n");
            });

        return summary.toString();
    }
}