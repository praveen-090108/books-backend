package com.intelliatech.app.repository;

import com.intelliatech.app.entity.DocumentNumberPreference;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentNumberPreferenceRepository extends JpaRepository<DocumentNumberPreference, Long> {

    Optional<DocumentNumberPreference> findByDocumentType(String documentType);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select preference from DocumentNumberPreference preference where preference.documentType = :documentType")
    Optional<DocumentNumberPreference> findByDocumentTypeForUpdate(@Param("documentType") String documentType);
}
