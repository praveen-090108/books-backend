package com.intelliatech.app.repository;

import com.intelliatech.app.entity.ExpenseAccountMaster;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExpenseAccountMasterRepository extends JpaRepository<ExpenseAccountMaster, Long> {
    List<ExpenseAccountMaster> findAllByOrganizationIdOrderByDisplayOrderAscAccountNameAsc(Long organizationId);
    List<ExpenseAccountMaster> findAllByOrganizationIdAndActiveTrueOrderByDisplayOrderAscAccountNameAsc(Long organizationId);
    Optional<ExpenseAccountMaster> findByIdAndOrganizationId(Long id, Long organizationId);
    boolean existsByOrganizationIdAndAccountNameIgnoreCase(Long organizationId, String accountName);
    boolean existsByOrganizationIdAndAccountNameIgnoreCaseAndIdNot(Long organizationId, String accountName, Long id);
    Optional<ExpenseAccountMaster> findByOrganizationIdAndAccountNameIgnoreCase(Long organizationId, String accountName);
}
