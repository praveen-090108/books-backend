package com.intelliatech.app.repository;

import com.intelliatech.app.entity.BankAccountMaster;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BankAccountMasterRepository extends JpaRepository<BankAccountMaster, Long> {
    List<BankAccountMaster> findAllByOrganizationIdOrderByAccountNameAsc(Long organizationId);
    List<BankAccountMaster> findAllByOrganizationIdAndActiveTrueOrderByAccountNameAsc(Long organizationId);
    Optional<BankAccountMaster> findByIdAndOrganizationId(Long id, Long organizationId);
    boolean existsByOrganizationIdAndAccountNameIgnoreCase(Long organizationId, String accountName);
    boolean existsByOrganizationIdAndAccountNameIgnoreCaseAndIdNot(Long organizationId, String accountName, Long id);
}
