package ru.bankingapi.bpmntester.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.bankingapi.bpmntester.domain.BusinessProcess;

import java.util.List;

@Repository
public interface BusinessProcessRepository extends JpaRepository<BusinessProcess, Long> {
    List<BusinessProcess> findByNameContaining(String name);
}