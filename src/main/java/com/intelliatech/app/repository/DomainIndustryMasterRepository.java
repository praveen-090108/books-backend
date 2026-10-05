package com.intelliatech.app.repository;

import com.intelliatech.app.entity.DomainIndustryMaster;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DomainIndustryMasterRepository extends JpaRepository<DomainIndustryMaster,Long> {
    List<DomainIndustryMaster> findByOrganizationIdOrderByDisplayOrderAscNameAsc(Long organizationId);
    List<DomainIndustryMaster> findByOrganizationIdAndActiveTrueOrderByDisplayOrderAscNameAsc(Long organizationId);
    Optional<DomainIndustryMaster> findByOrganizationIdAndNameIgnoreCase(Long organizationId,String name);
}
