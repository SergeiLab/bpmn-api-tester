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

        Pattern arrowPattern = Pattern.compile("^\\s*(.+?)\\s*->\\s*(.+?)\\s*:\\s*(.+)$");
        Pattern methodPattern = Pattern.compile("(GET|POST|PUT|DELETE|PATCH)\\s+([^\\s(]+)");

        for (String line : lines) {
            line = line.trim();
            
            if (line.isEmpty() || line.startsWith("@") || line.startsWith("title") || 
                line.startsWith("participant") || line.startsWith("actor") || 
                line.startsWith("//") || line.startsWith("activate") || 
                line.startsWith("deactivate")) {
                continue;
            }

            Matcher arrowMatcher = arrowPattern.matcher(line);
            if (arrowMatcher.matches()) {
                String source = arrowMatcher.group(1).trim();
                String target = arrowMatcher.group(2).trim();
                String action = arrowMatcher.group(3).trim();

                Matcher methodMatcher = methodPattern.matcher(action);
                if (methodMatcher.find()) {
                    String method = methodMatcher.group(1);
                    String endpoint = methodMatcher.group(2);
                    
                    String params = "";
                    if (action.contains("(")) {
                        int start = action.indexOf("(");
                        int end = action.lastIndexOf(")");
                        if (end > start) {
                            params = action.substring(start + 1, end);
                        }
                    }

                    ProcessStep step = ProcessStep.builder()
                        .businessProcess(process)
                        .stepId("step_" + order)
                        .stepName(method + " " + endpoint + (params.isEmpty() ? "" : " (" + params + ")"))
                        .stepOrder(order++)
                        .stepType(StepType.SERVICE_TASK)
                        .httpMethod(method)
                        .apiEndpoint(endpoint)
                        .build();

                    steps.add(step);
                    log.debug("Extracted step: {} {}", method, endpoint);
                }
            }
        }

        return steps;
    }

    public void validateSequenceDiagram(String diagramText) {
        if (diagramText == null || diagramText.trim().isEmpty()) {
            throw new IllegalArgumentException("Sequence diagram cannot be empty");
        }

        if (!diagramText.contains("->")) {
            throw new IllegalArgumentException("Invalid sequence diagram format: no arrows found");
        }

        log.info("Sequence diagram validation successful");
    }
}