package com.intelliatech.app.repository;

import com.intelliatech.app.entity.InvoiceCommunication;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceCommunicationRepository extends JpaRepository<InvoiceCommunication, Long> {
    List<InvoiceCommunication> findAllByInvoiceIdOrderByCreatedAtDesc(Long invoiceId);
    List<InvoiceCommunication> findAllByDeliveryStatusAndScheduledAtLessThanEqual(String status, LocalDateTime dueAt);
}
