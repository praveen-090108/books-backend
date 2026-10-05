package com.intelliatech.app.repository;

import com.intelliatech.app.entity.InvoiceCounter;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvoiceCounterRepository extends JpaRepository<InvoiceCounter, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select counter from InvoiceCounter counter where counter.key = :key")
    Optional<InvoiceCounter> findByKeyForUpdate(@Param("key") String key);
}
