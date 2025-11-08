package ru.bankingapi.bpmntester.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "process_steps")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessStep {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_process_id")
    private BusinessProcess businessProcess;
    
    private String stepId;
    private String stepName;
    private Integer stepOrder;
    
    @Enumerated(EnumType.STRING)
    private StepType stepType;
    
    private String apiEndpoint;
    private String httpMethod;
    
    @Column(columnDefinition = "TEXT")
    private String openApiSpec;
    
    @Column(columnDefinition = "TEXT")
    private String dataMapping;
}