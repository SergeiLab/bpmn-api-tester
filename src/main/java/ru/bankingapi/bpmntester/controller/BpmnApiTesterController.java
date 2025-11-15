package ru.bankingapi.bpmntester.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import io.swagger.v3.oas.models.OpenAPI;
import ru.bankingapi.bpmntester.domain.*;
import ru.bankingapi.bpmntester.repository.*;
import ru.bankingapi.bpmntester.service.*;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1")
@Slf4j
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class BpmnApiTesterController {

    private final BusinessProcessRepository processRepository;
    private final TestExecutionRepository executionRepository;
    private final BpmnParserService bpmnParser;
    private final OpenApiParserService openApiParser;
    private final TestOrchestrator testOrchestrator;
    private final SequenceDiagramParser sequenceParser;
    private final ReportExportService reportExportService;
    private final AiTestDataGenerator aiTestDataGenerator; // <-- Поле уже присутствует

    @Value("${ai.enabled:true}") // <-- Значение по умолчанию обновлено на 'true'
    private boolean aiEnabled;

    @Value("${ai.provider:none}")
    private String aiProvider;

    @PostMapping("/processes/upload")
    public ResponseEntity<?> uploadProcess(
        @RequestParam("bpmn") MultipartFile bpmnFile,
        @RequestParam(value = "name", required = false) String processName,
        @RequestParam(required = false) List<MultipartFile> openApiSpecs
    ) {
        try {
            log.info("Uploading BPMN process: {}", bpmnFile.getOriginalFilename());

            String bpmnXml = new String(bpmnFile.getBytes(), StandardCharsets.UTF_8);
            bpmnParser.validateBpmnXml(bpmnXml);
            BusinessProcess process = bpmnParser.parseBpmnXml(bpmnXml, processName);

            if (openApiSpecs != null && !openApiSpecs.isEmpty()) {
                matchOpenApiSpecs(process, openApiSpecs);
            }

            process = processRepository.save(process);
            log.info("Process uploaded: id={}, steps={}", process.getId(), process.getSteps().size());

            Map<String, Object> response = new HashMap<>();
            response.put("id", process.getId());
            response.put("name", process.getName());
            response.put("steps", process.getSteps().size());
            response.put("message", "Process uploaded successfully");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Upload failed", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        }
    }

    @PostMapping("/processes/upload-sequence")
    public ResponseEntity<?> uploadSequenceDiagram(
        @RequestParam("sequence") MultipartFile sequenceFile,
        @RequestParam(value = "name", required = false) String processName
    ) {
        try {
            log.info("Uploading sequence diagram: {}", sequenceFile.getOriginalFilename());

            String diagramText = new String(sequenceFile.getBytes(), StandardCharsets.UTF_8);
            sequenceParser.validateSequenceDiagram(diagramText);
            BusinessProcess process = sequenceParser.parseSequenceDiagram(diagramText, processName);
            process = processRepository.save(process);

            log.info("Sequence uploaded: id={}, steps={}", process.getId(), process.getSteps().size());

            Map<String, Object> response = new HashMap<>();
            response.put("id", process.getId());
            response.put("name", process.getName());
            response.put("steps", process.getSteps().size());
            response.put("message", "Sequence diagram uploaded successfully");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Sequence upload failed", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        }
    }

    @GetMapping("/processes")
    public ResponseEntity<List<Map<String, Object>>> getAllProcesses() {
        List<BusinessProcess> processes = processRepository.findAll();
        
        List<Map<String, Object>> response = processes.stream()
            .map(p -> {
                Map<String, Object> processMap = new HashMap<>();
                processMap.put("id", p.getId());
                processMap.put("name", p.getName());
                processMap.put("description", p.getDescription());
                processMap.put("steps", p.getSteps().size());
                processMap.put("createdAt", p.getCreatedAt().toString());
                return processMap;
            })
            .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/processes/{id}")
    public ResponseEntity<?> getProcess(@PathVariable Long id) {
        Optional<BusinessProcess> process = processRepository.findById(id);
        
        if (process.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        BusinessProcess p = process.get();
        
        Map<String, Object> response = new HashMap<>();
        response.put("id", p.getId());
        response.put("name", p.getName());
        response.put("description", p.getDescription());
        response.put("bpmnXml", p.getBpmnXml());
        response.put("createdAt", p.getCreatedAt().toString());
        
        List<Map<String, Object>> stepsList = p.getSteps().stream()
            .map(s -> {
                Map<String, Object> stepMap = new HashMap<>();
                stepMap.put("id", s.getId());
                stepMap.put("name", s.getStepName());
                stepMap.put("order", s.getStepOrder());
                stepMap.put("endpoint", s.getApiEndpoint() != null ? s.getApiEndpoint() : "");
                stepMap.put("method", s.getHttpMethod() != null ? s.getHttpMethod() : "");
                return stepMap;
            })
            .collect(Collectors.toList());
        
        response.put("steps", stepsList);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/processes/{id}/execute")
    public ResponseEntity<?> executeTest(
        @PathVariable Long id,
        @RequestBody TestExecutionRequest request
    ) {
        try {
            Optional<BusinessProcess> process = processRepository.findById(id);
            
            if (process.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            log.info("Executing test for process {} in {} mode", id, request.getMode());

            TestExecution execution = testOrchestrator.executeProcess(
                process.get(),
                request.getMode() != null ? request.getMode() : ExecutionMode.STANDARD,
                request.getInitialContext() != null ? request.getInitialContext() : new HashMap<>(),
                request.isGenerateTestData()
            );

            TestExecutionResponse response = buildExecutionResponse(execution);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Execution failed", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @GetMapping("/executions/{id}")
    public ResponseEntity<?> getExecution(@PathVariable Long id) {
        Optional<TestExecution> execution = executionRepository.findById(id);
        
        if (execution.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        TestExecutionResponse response = buildExecutionResponse(execution.get());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/processes/{id}/executions")
    public ResponseEntity<List<Map<String, Object>>> getProcessExecutions(@PathVariable Long id) {
        List<TestExecution> executions = executionRepository
            .findByBusinessProcessIdOrderByStartedAtDesc(id);

        List<Map<String, Object>> response = executions.stream()
            .map(e -> {
                Map<String, Object> execMap = new HashMap<>();
                execMap.put("id", e.getId());
                execMap.put("mode", e.getMode().toString());
                execMap.put("status", e.getStatus().toString());
                execMap.put("startedAt", e.getStartedAt().toString());
                execMap.put("completedAt", e.getCompletedAt() != null ? e.getCompletedAt().toString() : "");
                execMap.put("stepsCompleted", e.getStepResults().size());
                return execMap;
            })
            .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/executions/{id}/export/html")
    public ResponseEntity<String> exportHtml(@PathVariable Long id) {
        Optional<TestExecution> execution = executionRepository.findById(id);
        
        if (execution.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        String html = reportExportService.exportToHtml(execution.get());
        
        return ResponseEntity.ok()
            .header("Content-Type", "text/html; charset=UTF-8")
            .header("Content-Disposition", "attachment; filename=report-" + id + ".html")
            .body(html);
    }

    @GetMapping("/executions/{id}/export/csv")
    public ResponseEntity<String> exportCsv(@PathVariable Long id) {
        Optional<TestExecution> execution = executionRepository.findById(id);
        
        if (execution.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        String csv = reportExportService.exportToCsv(execution.get());
        
        return ResponseEntity.ok()
            .header("Content-Type", "text/csv; charset=UTF-8")
            .header("Content-Disposition", "attachment; filename=report-" + id + ".csv")
            .body(csv);
    }

    @GetMapping("/executions/{id}/export/json")
    public ResponseEntity<String> exportJson(@PathVariable Long id) {
        Optional<TestExecution> execution = executionRepository.findById(id);
        
        if (execution.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        String json = reportExportService.exportToJson(execution.get());
        
        return ResponseEntity.ok()
            .header("Content-Type", "application/json; charset=UTF-8")
            .header("Content-Disposition", "attachment; filename=report-" + id + ".json")
            .body(json);
    }

    @GetMapping("/ai/status")
    public ResponseEntity<Map<String, Object>> getAiStatus() {
        Map<String, Object> status = new HashMap<>();
        
        boolean serviceAvailable = false;
        String effectiveProvider = "disabled";

        // Проверяем, включен ли AI в конфиге
        if (aiEnabled) {
            try {
                // Проверяем, доступен ли сервис AI (Ollama, OpenAI и т.д.)
                serviceAvailable = aiTestDataGenerator.isAiAvailable();
                effectiveProvider = serviceAvailable ? aiProvider : "error (service unavailable)";
            } catch (Exception e) {
                log.error("Failed to check AI status", e);
                serviceAvailable = false;
                effectiveProvider = "error (exception)";
            }
        } else {
            serviceAvailable = false;
            effectiveProvider = "disabled (config)";
        }
        
        status.put("enabled", serviceAvailable); // true, только если включено в конфиге И сервис доступен
        status.put("provider", effectiveProvider);
        status.put("fallbackAvailable", true); 
        
        return ResponseEntity.ok(status);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "BPMN API Tester");
        response.put("version", "1.0.0");
        return ResponseEntity.ok(response);
    }

    private void matchOpenApiSpecs(BusinessProcess process, List<MultipartFile> openApiSpecs) throws Exception {
        for (MultipartFile specFile : openApiSpecs) {
            String specContent = new String(specFile.getBytes(), StandardCharsets.UTF_8);
            OpenAPI openAPI = openApiParser.parseOpenApiSpec(specContent);

            for (ProcessStep step : process.getSteps()) {
                if (step.getApiEndpoint() == null || step.getApiEndpoint().isBlank()) {
                    ApiEndpointInfo match = openApiParser.findBestMatchingEndpoint(
                        openAPI, step.getStepName(), ""
                    );

                    if (match != null) {
                        step.setApiEndpoint(match.getPath());
                        step.setHttpMethod(match.getMethod());
                        log.info("Auto-matched '{}' to {} {}", step.getStepName(), match.getMethod(), match.getPath());
                    }
                }
            }
        }
    }

    private TestExecutionResponse buildExecutionResponse(TestExecution execution) {
        List<StepResultDto> stepResults = execution.getStepResults().stream()
            .map(r -> StepResultDto.builder()
                .stepName(r.getProcessStep().getStepName())
                .status(r.getStatus())
                .endpoint(r.getProcessStep().getApiEndpoint())
                .httpStatus(r.getHttpStatusCode())
                .requestPayload(r.getRequestPayload())
                .responsePayload(r.getResponsePayload())
                .errorMessage(r.getErrorMessage())
                .validationErrors(parseValidationErrors(r.getValidationErrors()))
                .executionTimeMs(r.getExecutionTimeMs())
                .build())
            .collect(Collectors.toList());

        return TestExecutionResponse.builder()
            .executionId(execution.getId())
            .status(execution.getStatus())
            .message(execution.getStatus() == ExecutionStatus.COMPLETED 
                ? "Test execution completed successfully" 
                : "Test execution failed")
            .stepResults(stepResults)
            .aiAnalysis(execution.getAiAnalysis())
            .build();
    }

    private List<String> parseValidationErrors(String validationErrorsJson) {
        if (validationErrorsJson == null || validationErrorsJson.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return Arrays.asList(validationErrorsJson.split(","));
        } catch (Exception e) {
            return List.of(validationErrorsJson);
        }
    }
}