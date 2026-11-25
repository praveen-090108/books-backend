package com.intelliatech.app.repository;

import com.intelliatech.app.entity.LeadSourceMaster;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeadSourceMasterRepository extends JpaRepository<LeadSourceMaster, Long> {
    List<LeadSourceMaster> findByOrganizationIdOrderByDisplayOrderAscNameAsc(Long organizationId);
    List<LeadSourceMaster> findByOrganizationIdAndActiveTrueOrderByDisplayOrderAscNameAsc(Long organizationId);
    Optional<LeadSourceMaster> findByOrganizationIdAndNameIgnoreCase(Long organizationId, String name);
}
