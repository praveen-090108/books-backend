package com.intelliatech.app.repository;

import com.intelliatech.app.entity.InvoiceTdsDeduction;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceTdsDeductionRepository extends JpaRepository<InvoiceTdsDeduction, Long> {
    Optional<InvoiceTdsDeduction> findByPaymentId(Long paymentId);
}
