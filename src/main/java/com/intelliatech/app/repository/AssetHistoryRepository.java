package com.intelliatech.app.repository;

import com.intelliatech.app.entity.AssetHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetHistoryRepository extends JpaRepository<AssetHistory, Long> {
    List<AssetHistory> findAllByAssetIdOrderByCreatedAtDesc(Long assetId);
}
