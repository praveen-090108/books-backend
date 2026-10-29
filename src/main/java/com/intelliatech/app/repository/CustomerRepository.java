package com.intelliatech.app.repository;

import com.intelliatech.app.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    List<Customer> findAllByOrganizationId(Long organizationId);
    Optional<Customer> findByOrganizationIdAndId(Long organizationId, Long id);
    boolean existsByOrganizationIdAndEmailIgnoreCase(Long organizationId, String email);
}
