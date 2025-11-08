package ru.bankingapi.bpmntester.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "step_execution_results")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StepExecutionResult {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "test_execution_id")
    private TestExecution testExecution;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "process_step_id")
    private ProcessStep processStep;
    
    private Integer executionOrder;
    
    @Enumerated(EnumType.STRING)
    private StepStatus status;
    
    @Column(columnDefinition = "TEXT")
    private String requestPayload;
    
    @Column(columnDefinition = "TEXT")
    private String responsePayload;
    
    private Integer httpStatusCode;
    
    @Column(columnDefinition = "TEXT")
    private String errorMessage;
    
    @Column(columnDefinition = "TEXT")
    private String validationErrors;
    
    private Long executionTimeMs;
    private LocalDateTime executedAt;
}