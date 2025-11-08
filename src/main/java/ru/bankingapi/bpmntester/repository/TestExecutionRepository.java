package ru.bankingapi.bpmntester.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import ru.bankingapi.bpmntester.domain.ExecutionStatus;
import ru.bankingapi.bpmntester.domain.TestExecution;

import java.util.List;

@Repository
public interface TestExecutionRepository extends JpaRepository<TestExecution, Long> {
    List<TestExecution> findByBusinessProcessIdOrderByStartedAtDesc(Long businessProcessId);
    
    @Query("SELECT e FROM TestExecution e WHERE e.status = ?1 ORDER BY e.startedAt DESC")
    List<TestExecution> findByStatus(ExecutionStatus status);
}