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
public class ApiEndpointInfo {
    private String path;
    private String method;
    private String operationId;
    private String summary;
    private String description;
    private Map<String, Object> requestSchema;
    private Map<String, Object> responseSchema;
    private List<String> requiredFields;
}