package ru.bankingapi.bpmntester.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.bankingapi.bpmntester.domain.StepExecutionResult;

import java.util.List;

@Repository
public interface StepExecutionResultRepository extends JpaRepository<StepExecutionResult, Long> {
    List<StepExecutionResult> findByTestExecutionIdOrderByExecutionOrder(Long testExecutionId);
}