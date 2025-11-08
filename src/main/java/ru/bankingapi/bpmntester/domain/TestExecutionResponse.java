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
public class TestExecutionResponse {
    private Long executionId;
    private ExecutionStatus status;
    private String message;
    private List<StepResultDto> stepResults;
    private String aiAnalysis;
}