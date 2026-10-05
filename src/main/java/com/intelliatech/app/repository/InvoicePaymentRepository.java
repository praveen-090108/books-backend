package com.intelliatech.app.repository;

import com.intelliatech.app.entity.InvoicePayment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvoicePaymentRepository extends JpaRepository<InvoicePayment, Long> {

    Optional<InvoicePayment> findByIdempotencyKey(String idempotencyKey);

    List<InvoicePayment> findAllByPrimaryInvoiceIdOrderByCreatedAtDesc(Long invoiceId);

    List<InvoicePayment> findAllByOrderByPaymentDateDescCreatedAtDesc();

    long countByPrimaryInvoiceIdAndReversedFalse(Long invoiceId);

    @Query("select count(distinct allocation.payment.id) from InvoicePaymentAllocation allocation where allocation.invoice.id = :invoiceId and allocation.payment.reversed = false")
    long countActiveByInvoiceId(@Param("invoiceId") Long invoiceId);
}
