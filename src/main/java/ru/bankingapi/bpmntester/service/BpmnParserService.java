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
import java.util.regex.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class BpmnParserService {

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

            List<ProcessStep> steps = new ArrayList<>();
            
            steps.addAll(extractServiceTasks(bpmnProcess, businessProcess));
            
            List<ProcessStep> regularTasks = extractRegularTasks(bpmnProcess, businessProcess);
            steps.addAll(regularTasks);
            
            log.info("Extracted ServiceTasks: {}, RegularTasks: {}", 
                extractServiceTasks(bpmnProcess, businessProcess).size(),
                regularTasks.size());
            
            if (steps.isEmpty()) {
                log.warn("No tasks found in standard way, trying all FlowNodes");
                steps.addAll(extractAllFlowNodes(bpmnProcess, businessProcess));
            }
            
            steps = sortByProcessFlow(steps, bpmnProcess);
            businessProcess.setSteps(steps);

            log.info("Parsed BPMN process '{}' with {} tasks", 
                businessProcess.getName(), steps.size());

            return businessProcess;

        } catch (Exception e) {
            log.error("Failed to parse BPMN XML", e);
            throw new RuntimeException("Cannot parse BPMN XML: " + e.getMessage(), e);
        }
    }

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

            extractApiInfo(task, step);
            
            steps.add(step);
            
            log.debug("Extracted service task: {} ({})", step.getStepName(), step.getStepId());
        }

        return steps;
    }

    private List<ProcessStep> extractRegularTasks(Process process, BusinessProcess businessProcess) {
        Collection<Task> tasks = process.getModelInstance()
            .getModelElementsByType(Task.class);

        List<ProcessStep> steps = new ArrayList<>();
        int order = 0;

        for (Task task : tasks) {
            if (task instanceof ServiceTask) {
                continue;
            }

            ProcessStep step = ProcessStep.builder()
                .businessProcess(businessProcess)
                .stepId(task.getId())
                .stepName(task.getName())
                .stepOrder(order++)
                .stepType(StepType.SERVICE_TASK)
                .build();

            extractApiInfoFromTaskName(task.getName(), step);
            extractApiInfo(task, step);
            
            steps.add(step);
            
            log.debug("Extracted regular task: {} ({})", step.getStepName(), step.getStepId());
        }

        return steps;
    }

    private List<ProcessStep> extractAllFlowNodes(Process process, BusinessProcess businessProcess) {
        Collection<FlowNode> flowNodes = process.getModelInstance()
            .getModelElementsByType(FlowNode.class);

        List<ProcessStep> steps = new ArrayList<>();
        int order = 0;

        for (FlowNode node : flowNodes) {
            if (node instanceof StartEvent || node instanceof EndEvent || node instanceof Gateway) {
                continue;
            }
            
            String name = node.getName();
            if (name == null || name.isEmpty()) {
                continue;
            }

            ProcessStep step = ProcessStep.builder()
                .businessProcess(businessProcess)
                .stepId(node.getId())
                .stepName(name)
                .stepOrder(order++)
                .stepType(StepType.SERVICE_TASK)
                .build();

            extractApiInfoFromTaskName(name, step);
            
            if (node instanceof FlowNode) {
                extractApiInfo((FlowNode)node, step);
            }
            
            steps.add(step);
            log.debug("Extracted FlowNode: {} ({})", step.getStepName(), step.getStepId());
        }

        return steps;
    }

    private void extractApiInfo(FlowNode task, ProcessStep step) {
        if (task instanceof ServiceTask) {
            ServiceTask serviceTask = (ServiceTask) task;
        }
        
        Collection<Documentation> docs = task.getDocumentations();
        if (!docs.isEmpty()) {
            String docText = docs.iterator().next().getTextContent();
            parseApiInfoFromDocumentation(docText, step);
        }

        ExtensionElements extensions = task.getExtensionElements();
        if (extensions != null) {
            parseApiInfoFromExtensions(extensions, step);
        }

        log.debug("Extracted API info for task {}: endpoint={}, method={}", 
            task.getId(), step.getApiEndpoint(), step.getHttpMethod());
    }

    private void extractApiInfoFromTaskName(String taskName, ProcessStep step) {
        if (taskName == null || taskName.isEmpty()) {
            return;
        }

        Pattern colonPattern = Pattern.compile(":\\s*(GET|POST|PUT|DELETE|PATCH)\\s+(/[^\\s]*)");
        Matcher colonMatcher = colonPattern.matcher(taskName);

        if (colonMatcher.find()) {
            String method = colonMatcher.group(1);
            String endpoint = colonMatcher.group(2);
            
            step.setHttpMethod(method);
            step.setApiEndpoint(endpoint);
            
            log.debug("Extracted from task name (colon): {} {}", method, endpoint);
            return;
        }

        Pattern simplePattern = Pattern.compile("(GET|POST|PUT|DELETE|PATCH)\\s+(/[^\\s]*)");
        Matcher simpleMatcher = simplePattern.matcher(taskName);

        if (simpleMatcher.find()) {
            String method = simpleMatcher.group(1);
            String endpoint = simpleMatcher.group(2);
            
            step.setHttpMethod(method);
            step.setApiEndpoint(endpoint);
            
            log.debug("Extracted from task name (simple): {} {}", method, endpoint);
        }
    }

    private void parseApiInfoFromDocumentation(String documentation, ProcessStep step) {
        if (documentation == null || documentation.isBlank()) {
            return;
        }

        String[] lines = documentation.split("\n");
        for (String line : lines) {
            line = line.trim();
            
            if (line.matches("^(GET|POST|PUT|DELETE|PATCH)\\s+/.+")) {
                String[] parts = line.split("\\s+", 2);
                step.setHttpMethod(parts[0]);
                step.setApiEndpoint(parts[1]);
                return;
            }
            
            if (line.toLowerCase().startsWith("endpoint:")) {
                String endpoint = line.substring("endpoint:".length()).trim();
                step.setApiEndpoint(endpoint);
            }
            
            if (line.toLowerCase().startsWith("method:")) {
                String method = line.substring("method:".length()).trim();
                step.setHttpMethod(method.toUpperCase());
            }
        }
    }

    private void parseApiInfoFromExtensions(ExtensionElements extensions, ProcessStep step) {
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

    private List<ProcessStep> sortByProcessFlow(List<ProcessStep> steps, Process process) {
        if (steps.isEmpty()) {
            return steps;
        }

        Collection<SequenceFlow> flows = process.getModelInstance()
            .getModelElementsByType(SequenceFlow.class);

        Map<String, String> flowMap = new HashMap<>();
        for (SequenceFlow flow : flows) {
            if (flow.getSource() != null && flow.getTarget() != null) {
                flowMap.put(flow.getSource().getId(), flow.getTarget().getId());
            }
        }

        Collection<StartEvent> startEvents = process.getModelInstance()
            .getModelElementsByType(StartEvent.class);
        
        StartEvent startEvent = startEvents.stream().findFirst().orElse(null);

        if (startEvent == null) {
            log.warn("No start event found, returning steps as-is");
            for (int i = 0; i < steps.size(); i++) {
                steps.get(i).setStepOrder(i);
            }
            return steps;
        }

        List<ProcessStep> orderedSteps = new ArrayList<>();
        String currentId = startEvent.getId();
        Set<String> visited = new HashSet<>();
        int maxIterations = steps.size() * 2;
        int iterations = 0;

        while (currentId != null && !visited.contains(currentId) && iterations < maxIterations) {
            visited.add(currentId);
            iterations++;
            
            final String searchId = currentId;
            Optional<ProcessStep> foundStep = steps.stream()
                .filter(step -> step.getStepId().equals(searchId))
                .findFirst();
            
            if (foundStep.isPresent()) {
                orderedSteps.add(foundStep.get());
            }

            currentId = flowMap.get(currentId);
        }

        for (ProcessStep step : steps) {
            if (!orderedSteps.contains(step)) {
                orderedSteps.add(step);
            }
        }

        for (int i = 0; i < orderedSteps.size(); i++) {
            orderedSteps.get(i).setStepOrder(i);
        }

        return orderedSteps;
    }

    private String extractProcessDescription(Process process) {
        Collection<Documentation> docs = process.getDocumentations();
        if (!docs.isEmpty()) {
            return docs.iterator().next().getTextContent();
        }
        return "No description provided";
    }

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