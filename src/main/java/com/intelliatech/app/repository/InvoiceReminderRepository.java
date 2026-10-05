package com.intelliatech.app.repository;

import com.intelliatech.app.entity.InvoiceReminder;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceReminderRepository extends JpaRepository<InvoiceReminder, Long> {
    List<InvoiceReminder> findAllByInvoiceIdOrderByCreatedAtDesc(Long invoiceId);
    List<InvoiceReminder> findAllByDeliveryStatusAndScheduledAtLessThanEqual(String status, LocalDateTime dueAt);
}
