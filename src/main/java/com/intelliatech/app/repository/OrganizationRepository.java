package com.intelliatech.app.repository;

import com.intelliatech.app.entity.Organization;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationRepository extends JpaRepository<Organization, Long> {
    Optional<Organization> findByPublicId(String publicId);
    boolean existsBySlug(String slug);
    boolean existsByGstinIgnoreCase(String gstin);
}
