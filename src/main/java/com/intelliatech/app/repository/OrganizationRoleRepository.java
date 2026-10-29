package com.intelliatech.app.repository;

import com.intelliatech.app.entity.OrganizationRole;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationRoleRepository extends JpaRepository<OrganizationRole, Long> {
    Optional<OrganizationRole> findByOrganizationIdAndCode(Long organizationId, String code);
    Optional<OrganizationRole> findByOrganizationIdAndNameIgnoreCase(Long organizationId, String name);
}
