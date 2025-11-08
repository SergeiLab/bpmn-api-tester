package ru.bankingapi.bpmntester.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiSpecificationDto {
    private String serviceId;
    private String openApiUrl;
    private String openApiSpec;
}