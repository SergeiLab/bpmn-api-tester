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
public class StepResultDto {
    private String stepName;
    private StepStatus status;
    private String endpoint;
    private Integer httpStatus;
    private String requestPayload;
    private String responsePayload;
    private String errorMessage;
    private List<String> validationErrors;
    private Long executionTimeMs;
}