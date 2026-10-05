package com.intelliatech.app.repository;

import com.intelliatech.app.entity.BillPayment;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillPaymentRepository extends JpaRepository<BillPayment, Long> {
    boolean existsByPaymentNumber(String paymentNumber);
    Optional<BillPayment> findByIdempotencyKey(String idempotencyKey);
    Optional<BillPayment> findByIdAndBillOrganizationId(Long id, Long organizationId);
}
