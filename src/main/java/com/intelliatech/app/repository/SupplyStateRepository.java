package com.intelliatech.app.repository;

import com.intelliatech.app.entity.SupplyState;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupplyStateRepository extends JpaRepository<SupplyState, String> {
    List<SupplyState> findAllByOrderByNameAsc();
    Optional<SupplyState> findFirstByNameIgnoreCase(String name);
}
