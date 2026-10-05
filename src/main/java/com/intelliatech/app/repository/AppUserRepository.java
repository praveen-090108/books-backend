package com.intelliatech.app.repository;

import com.intelliatech.app.entity.AppUser;
import java.util.Optional;
import java.util.List;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppUserRepository extends JpaRepository<AppUser, Long>, JpaSpecificationExecutor<AppUser> {
    Optional<AppUser> findByEmailIgnoreCase(String email);
    Optional<AppUser> findByResetTokenHash(String resetTokenHash);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByResourceId(Long resourceId);
    Optional<AppUser> findByResourceId(Long resourceId);

    @Query("select u from AppUser u left join u.resource r where lower(coalesce(r.partyEmail, u.email)) = lower(:email)")
    Optional<AppUser> findForLoginEmail(@Param("email") String email);
    List<AppUser> findAllByManagerId(Long managerId);

    @Query("select u from AppUser u join u.resource r where r.reportingManager.id = :resourceId")
    List<AppUser> findAllByResourceReportingManagerId(@Param("resourceId") Long resourceId);

    @Query("select coalesce(r.partyEmail, u.email) from AppUser u left join u.resource r where u.id in :ids")
    List<String> findLoginEmailsByIdIn(@Param("ids") Collection<Long> ids);

    @Query("select u from AppUser u join u.resource r where lower(r.partyName) = lower(:name)")
    Optional<AppUser> findByResourceNameIgnoreCase(@Param("name") String name);
}
