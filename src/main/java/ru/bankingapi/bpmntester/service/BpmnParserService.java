package ru.bankingapi.bpmntester.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.*;
import org.camunda.bpm.model.bpmn.instance.Process;
import org.camunda.bpm.model.bpmn.instance.camunda.CamundaProperty;
import org.springframework.stereotype.Service;
import ru.bankingapi.bpmntester.domain.*;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class BpmnParserService {

    /**
     * Parse BPMN XML and extract process steps
     */
    public BusinessProcess parseBpmnXml(String bpmnXml, String processName) {
        try {
            BpmnModelInstance modelInstance = Bpmn.readModelFromStream(
                new ByteArrayInputStream(bpmnXml.getBytes(StandardCharsets.UTF_8))
            );

            Collection<Process> processes = modelInstance.getModelElementsByType(Process.class);
            
            if (processes.isEmpty()) {
                throw new IllegalArgumentException("No BPMN process found in XML");
            }

            Process bpmnProcess = processes.iterator().next();
            
            BusinessProcess businessProcess = BusinessProcess.builder()
                .name(processName != null ? processName : bpmnProcess.getName())
                .description(extractProcessDescription(bpmnProcess))
                .bpmnXml(bpmnXml)
                .steps(new ArrayList<>())
                .build();

            // Extract service tasks (API calls)
            List<ProcessStep> steps = extractServiceTasks(bpmnProcess, businessProcess);
            businessProcess.setSteps(steps);

            log.info("Parsed BPMN process '{}' with {} service tasks", 
                businessProcess.getName(), steps.size());

            return businessProcess;

        } catch (Exception e) {
            log.error("Failed to parse BPMN XML", e);
            throw new RuntimeException("Cannot parse BPMN XML: " + e.getMessage(), e);
        }
    }

    /**
     * Extract service tasks from BPMN process
     */
    private List<ProcessStep> extractServiceTasks(Process process, BusinessProcess businessProcess) {
        Collection<ServiceTask> serviceTasks = process.getModelInstance()
            .getModelElementsByType(ServiceTask.class);

        List<ProcessStep> steps = new ArrayList<>();
        int order = 0;

        for (ServiceTask task : serviceTasks) {
            ProcessStep step = ProcessStep.builder()
                .businessProcess(businessProcess)
                .stepId(task.getId())
                .stepName(task.getName())
                .stepOrder(order++)
                .stepType(StepType.SERVICE_TASK)
                .build();

            // Extract API endpoint info from task attributes
            extractApiInfo(task, step);
            
            steps.add(step);
            
            log.debug("Extracted service task: {} ({})", step.getStepName(), step.getStepId());
        }

        // Sort by flow sequence
        return sortByProcessFlow(steps, process);
    }

    /**
     * Extract API endpoint information from BPMN task
     */
    private void extractApiInfo(ServiceTask task, ProcessStep step) {
        // Check for custom properties/extensions
        String topic = task.getCamundaTopic();
        String implementation = task.getImplementation();
        
        // Try to extract from documentation
        Collection<Documentation> docs = task.getDocumentations();
        if (!docs.isEmpty()) {
            String docText = docs.iterator().next().getTextContent();
            parseApiInfoFromDocumentation(docText, step);
        }

        // Try to extract from extension elements
        ExtensionElements extensions = task.getExtensionElements();
        if (extensions != null) {
            parseApiInfoFromExtensions(extensions, step);
        }

        log.debug("Extracted API info for task {}: endpoint={}, method={}", 
            task.getId(), step.getApiEndpoint(), step.getHttpMethod());
    }

    /**
     * Parse API info from task documentation
     * Expected format: "GET /api/path" or "endpoint: /api/path, method: POST"
     */
    private void parseApiInfoFromDocumentation(String documentation, ProcessStep step) {
        if (documentation == null || documentation.isBlank()) {
            return;
        }

        String[] lines = documentation.split("\n");
        for (String line : lines) {
            line = line.trim();
            
            // Pattern: "GET /api/path"
            if (line.matches("^(GET|POST|PUT|DELETE|PATCH)\\s+/.+")) {
                String[] parts = line.split("\\s+", 2);
                step.setHttpMethod(parts[0]);
                step.setApiEndpoint(parts[1]);
                return;
            }
            
            // Pattern: "endpoint: /api/path"
            if (line.toLowerCase().startsWith("endpoint:")) {
                String endpoint = line.substring("endpoint:".length()).trim();
                step.setApiEndpoint(endpoint);
            }
            
            // Pattern: "method: POST"
            if (line.toLowerCase().startsWith("method:")) {
                String method = line.substring("method:".length()).trim();
                step.setHttpMethod(method.toUpperCase());
            }
        }
    }

    /**
     * Parse API info from BPMN extension elements
     */
    private void parseApiInfoFromExtensions(ExtensionElements extensions, ProcessStep step) {
        // Look for custom properties
        extensions.getElementsQuery()
            .filterByType(CamundaProperty.class)
            .list()
            .forEach(prop -> {
                String name = prop.getCamundaName();
                String value = prop.getCamundaValue();
                
                if ("api.endpoint".equals(name)) {
                    step.setApiEndpoint(value);
                } else if ("api.method".equals(name)) {
                    step.setHttpMethod(value);
                } else if ("api.spec".equals(name)) {
                    step.setOpenApiSpec(value);
                }
            });
    }

    /**
     * Sort steps by process flow sequence
     */
    private List<ProcessStep> sortByProcessFlow(List<ProcessStep> steps, Process process) {
        // Get all sequence flows
        Collection<SequenceFlow> flows = process.getModelInstance()
            .getModelElementsByType(SequenceFlow.class);

        // Build adjacency map
        Map<String, String> flowMap = flows.stream()
            .collect(Collectors.toMap(
                flow -> flow.getSource().getId(),
                flow -> flow.getTarget().getId(),
                (a, b) -> a // Keep first in case of duplicates
            ));

        // Find start event
        StartEvent startEvent = process.getModelInstance()
            .getModelElementsByType(StartEvent.class)
            .stream()
            .findFirst()
            .orElse(null);

        if (startEvent == null) {
            log.warn("No start event found, returning steps as-is");
            return steps;
        }

        // Traverse flow and reorder steps
        List<ProcessStep> orderedSteps = new ArrayList<>();
        String currentId = startEvent.getId();
        Set<String> visited = new HashSet<>();
        java.util.concurrent.atomic.AtomicInteger order = new java.util.concurrent.atomic.AtomicInteger(0);

        while (currentId != null && !visited.contains(currentId)) {
            visited.add(currentId);
            
            final String searchId = currentId;
            steps.stream()
                .filter(step -> step.getStepId().equals(searchId))
                .findFirst()
                .ifPresent(step -> {
                    step.setStepOrder(order.get());
                    orderedSteps.add(step);
                });

            currentId = flowMap.get(currentId);
            order.incrementAndGet();
        }

        return orderedSteps.isEmpty() ? steps : orderedSteps;
    }

    /**
     * Extract process description
     */
    private String extractProcessDescription(Process process) {
        Collection<Documentation> docs = process.getDocumentations();
        if (!docs.isEmpty()) {
            return docs.iterator().next().getTextContent();
        }
        return "No description provided";
    }

    /**
     * Validate BPMN XML structure
     */
    public void validateBpmnXml(String bpmnXml) {
        try {
            BpmnModelInstance modelInstance = Bpmn.readModelFromStream(
                new ByteArrayInputStream(bpmnXml.getBytes(StandardCharsets.UTF_8))
            );

            Bpmn.validateModel(modelInstance);
            
            log.info("BPMN XML validation successful");
            
        } catch (Exception e) {
            log.error("BPMN XML validation failed", e);
            throw new IllegalArgumentException("Invalid BPMN XML: " + e.getMessage(), e);
        }
    }
}