package ru.bankingapi.bpmntester.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.bankingapi.bpmntester.domain.*;

import java.util.*;
import java.util.regex.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class SequenceDiagramParser {

    public BusinessProcess parseSequenceDiagram(String diagramText, String processName) {
        try {
            BusinessProcess businessProcess = BusinessProcess.builder()
                .name(processName != null ? processName : "Sequence Diagram Process")
                .description("Parsed from sequence diagram")
                .bpmnXml(diagramText)
                .steps(new ArrayList<>())
                .build();

            List<ProcessStep> steps = extractStepsFromSequence(diagramText, businessProcess);
            businessProcess.setSteps(steps);

            log.info("Parsed sequence diagram: {} steps", steps.size());
            return businessProcess;

        } catch (Exception e) {
            log.error("Failed to parse sequence diagram", e);
            throw new RuntimeException("Cannot parse sequence diagram: " + e.getMessage(), e);
        }
    }

    private List<ProcessStep> extractStepsFromSequence(String text, BusinessProcess process) {
        List<ProcessStep> steps = new ArrayList<>();
        String[] lines = text.split("\n");
        int order = 0;

        Pattern plantUmlPattern = Pattern.compile("^\\s*(.+?)\\s*->\\s*(.+?)\\s*:\\s*(.+)$");
        Pattern methodPattern = Pattern.compile("(GET|POST|PUT|DELETE|PATCH)\\s+(/\\S+)");

        for (String line : lines) {
            line = line.trim();
            
            if (line.isEmpty() || line.startsWith("@") || line.startsWith("title") || 
                line.startsWith("participant") || line.startsWith("actor") || 
                line.startsWith("//") || line.startsWith("activate") || 
                line.startsWith("deactivate")) {
                continue;
            }

            Matcher plantUmlMatcher = plantUmlPattern.matcher(line);
            if (plantUmlMatcher.matches()) {
                String source = plantUmlMatcher.group(1).trim();
                String target = plantUmlMatcher.group(2).trim();
                String action = plantUmlMatcher.group(3).trim();

                Matcher methodMatcher = methodPattern.matcher(action);
                if (methodMatcher.find()) {
                    String method = methodMatcher.group(1);
                    String endpoint = methodMatcher.group(2);

                    ProcessStep step = ProcessStep.builder()
                        .businessProcess(process)
                        .stepId("step_" + order)
                        .stepName(action)
                        .stepOrder(order++)
                        .stepType(StepType.SERVICE_TASK)
                        .httpMethod(method)
                        .apiEndpoint(endpoint)
                        .build();

                    steps.add(step);
                    log.debug("Extracted step from sequence: {} {}", method, endpoint);
                } else {
                    ProcessStep step = ProcessStep.builder()
                        .businessProcess(process)
                        .stepId("step_" + order)
                        .stepName(action)
                        .stepOrder(order++)
                        .stepType(StepType.SERVICE_TASK)
                        .build();

                    steps.add(step);
                    log.debug("Extracted step without method: {}", action);
                }
            }
        }

        return steps;
    }

    public void validateSequenceDiagram(String diagramText) {
        if (diagramText == null || diagramText.trim().isEmpty()) {
            throw new IllegalArgumentException("Sequence diagram cannot be empty");
        }

        if (!diagramText.contains("->") && 
            !diagramText.matches("(?s).*\\b(GET|POST|PUT|DELETE|PATCH)\\b.*")) {
            throw new IllegalArgumentException("Invalid sequence diagram format");
        }

        log.info("Sequence diagram validation successful");
    }
}