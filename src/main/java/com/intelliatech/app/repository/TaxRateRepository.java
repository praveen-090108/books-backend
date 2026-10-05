package com.intelliatech.app.repository;

import com.intelliatech.app.entity.TaxRate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaxRateRepository extends JpaRepository<TaxRate, Long> {
    List<TaxRate> findByActiveTrueOrderByDisplayOrderAsc();
}
