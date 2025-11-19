package ru.bankingapi.bpmntester.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import ru.bankingapi.bpmntester.domain.BusinessProcess;

import java.util.List;
import java.util.Optional;

@Repository
public interface BusinessProcessRepository extends JpaRepository<BusinessProcess, Long> {
    List<BusinessProcess> findByNameContaining(String name);
    
    // ADD THIS METHOD
    @Query("SELECT DISTINCT p FROM BusinessProcess p LEFT JOIN FETCH p.steps WHERE p.id = :id")
    Optional<BusinessProcess> findByIdWithSteps(Long id);
}