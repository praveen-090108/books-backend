package com.intelliatech.app.repository;

import com.intelliatech.app.entity.*;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface IrpConfigurationRepository extends JpaRepository<IrpConfiguration,Long> {
    List<IrpConfiguration> findAllByCompanyIdOrderByEnvironment(Long companyId);
    Optional<IrpConfiguration> findByCompanyIdAndProviderAndEnvironment(Long companyId,String provider,IrpEnvironment environment);
    Optional<IrpConfiguration> findByCompanyIdAndActiveTrue(Long companyId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from IrpConfiguration c where c.companyId=:companyId")
    List<IrpConfiguration> lockCompanyConfigurations(@Param("companyId") Long companyId);
}
