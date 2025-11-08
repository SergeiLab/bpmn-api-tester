package ru.bankingapi.bpmntester.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestExecutionRequest {
    private Long businessProcessId;
    private ExecutionMode mode;
    private Map<String, Object> initialContext;
    private boolean generateTestData;
}