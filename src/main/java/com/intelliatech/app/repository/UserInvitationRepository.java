package com.intelliatech.app.repository;

import com.intelliatech.app.entity.UserInvitation;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserInvitationRepository extends JpaRepository<UserInvitation, Long> {
    @EntityGraph(attributePaths={"organization","user","resource"}) Optional<UserInvitation> findByTokenHash(String tokenHash);
    @EntityGraph(attributePaths={"organization","user","resource"}) Optional<UserInvitation> findFirstByOrganizationIdAndUserIdOrderByCreatedAtDesc(Long organizationId, Long userId);
    List<UserInvitation> findAllByOrganizationIdAndUserIdAndStatus(Long organizationId, Long userId, UserInvitation.Status status);
}
