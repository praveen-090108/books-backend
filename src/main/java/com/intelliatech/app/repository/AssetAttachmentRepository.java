package com.intelliatech.app.repository;

import com.intelliatech.app.entity.AssetAttachment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetAttachmentRepository extends JpaRepository<AssetAttachment, Long> {
    List<AssetAttachment> findAllByAssetIdAndActiveTrueOrderByCreatedAtDescIdDesc(Long assetId);
    Optional<AssetAttachment> findByIdAndAssetIdAndActiveTrue(Long id, Long assetId);
}
