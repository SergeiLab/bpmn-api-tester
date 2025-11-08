package ru.bankingapi.bpmntester.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.*;

@Entity
@Table(name = "test_executions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TestExecution {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_process_id")
    private BusinessProcess businessProcess;
    
    @Enumerated(EnumType.STRING)
    private ExecutionMode mode;
    
    @Enumerated(EnumType.STRING)
    private ExecutionStatus status;
    
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    
    @OneToMany(mappedBy = "testExecution", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<StepExecutionResult> stepResults = new ArrayList<>();
    
    @Column(columnDefinition = "TEXT")
    private String aiAnalysis;
    
    @Column(columnDefinition = "TEXT")
    private String errorSummary;
}