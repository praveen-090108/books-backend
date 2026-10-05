package com.intelliatech.app.repository;

import com.intelliatech.app.entity.AssetDepreciationEntry;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetDepreciationEntryRepository extends JpaRepository<AssetDepreciationEntry, Long> {
    List<AssetDepreciationEntry> findAllByScheduleIdOrderByPeriodNumber(Long scheduleId);
    List<AssetDepreciationEntry> findAllByAssetIdOrderByStartDate(Long assetId);
    void deleteAllByScheduleId(Long scheduleId);
}
