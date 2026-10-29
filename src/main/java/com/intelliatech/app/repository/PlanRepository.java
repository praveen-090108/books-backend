package com.intelliatech.app.repository;

import com.intelliatech.app.entity.Plan;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanRepository extends JpaRepository<Plan, Long> {
    Optional<Plan> findByCodeAndActiveTrue(String code);
}
