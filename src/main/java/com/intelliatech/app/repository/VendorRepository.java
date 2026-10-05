package com.intelliatech.app.repository;

import com.intelliatech.app.entity.Vendor;
import com.intelliatech.app.entity.VendorStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VendorRepository extends JpaRepository<Vendor, Long>, JpaSpecificationExecutor<Vendor> {

    Optional<Vendor> findByIdAndOrganizationId(Long id, Long organizationId);

    boolean existsByOrganizationIdAndVendorNumber(Long organizationId, String vendorNumber);

    boolean existsByOrganizationIdAndVendorNameIgnoreCase(Long organizationId, String vendorName);

    boolean existsByOrganizationIdAndVendorNameIgnoreCaseAndIdNot(Long organizationId, String vendorName, Long id);

    long countByOrganizationId(Long organizationId);

    long countByOrganizationIdAndStatus(Long organizationId, VendorStatus status);

    @Query("""
            select max(v.vendorNumber)
            from Vendor v
            where v.organizationId = :organizationId
              and v.vendorNumber like 'VEN-%'
            """)
    String findLatestVendorNumber(@Param("organizationId") Long organizationId);
}
