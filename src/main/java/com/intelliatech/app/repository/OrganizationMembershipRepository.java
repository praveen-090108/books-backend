package com.intelliatech.app.repository;

import com.intelliatech.app.entity.OrganizationMembership;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationMembershipRepository extends JpaRepository<OrganizationMembership, Long> {
    @EntityGraph(attributePaths = {"organization", "role", "user"})
    List<OrganizationMembership> findAllByUserIdAndStatus(Long userId, OrganizationMembership.Status status);
    @EntityGraph(attributePaths = {"organization", "role", "user"})
    Optional<OrganizationMembership> findByIdAndUserId(Long id, Long userId);
    @EntityGraph(attributePaths = {"organization", "role", "user"})
    Optional<OrganizationMembership> findFirstByUserIdAndStatusOrderById(Long userId, OrganizationMembership.Status status);
    @EntityGraph(attributePaths = {"organization", "role", "user"})
    Optional<OrganizationMembership> findFirstByOrganizationPublicIdAndStatusOrderById(String publicId, OrganizationMembership.Status status);
    @EntityGraph(attributePaths = {"organization", "role", "user", "user.resource"})
    List<OrganizationMembership> findAllByOrganizationId(Long organizationId);
    @EntityGraph(attributePaths = {"organization", "role", "user", "user.resource"})
    Optional<OrganizationMembership> findByOrganizationIdAndUserId(Long organizationId, Long userId);
    long countByUserId(Long userId);
}
