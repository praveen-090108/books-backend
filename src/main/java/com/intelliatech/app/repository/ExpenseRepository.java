package com.intelliatech.app.repository;

import com.intelliatech.app.entity.Expense;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ExpenseRepository extends JpaRepository<Expense, Long>, JpaSpecificationExecutor<Expense> {
    long countByExpenseAccountMasterId(Long expenseAccountMasterId);
    Optional<Expense> findByIdAndOrganizationIdAndDeletedFalse(Long id, Long organizationId);

    boolean existsByOrganizationIdAndVendorIdAndInvoiceNumberIgnoreCaseAndDeletedFalse(
            Long organizationId, Long vendorId, String invoiceNumber);

    boolean existsByOrganizationIdAndVendorIdAndInvoiceNumberIgnoreCaseAndIdNotAndDeletedFalse(
            Long organizationId, Long vendorId, String invoiceNumber, Long id);

}
