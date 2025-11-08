package ru.bankingapi.bpmntester.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.bankingapi.bpmntester.domain.ProcessStep;

import java.util.List;

@Repository
public interface ProcessStepRepository extends JpaRepository<ProcessStep, Long> {
    List<ProcessStep> findByBusinessProcessIdOrderByStepOrder(Long businessProcessId);
}