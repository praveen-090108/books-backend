package com.intelliatech.app.repository;

import com.intelliatech.app.entity.FixedCostProjectMilestone;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface FixedCostProjectMilestoneRepository extends JpaRepository<FixedCostProjectMilestone,Long>{
 @EntityGraph(attributePaths={"invoice"}) List<FixedCostProjectMilestone> findByProjectIdAndActiveTrueOrderByDueDateAscIdAsc(Long projectId);
 @EntityGraph(attributePaths={"invoice"}) Optional<FixedCostProjectMilestone> findByIdAndProjectIdAndActiveTrue(Long id,Long projectId);
 @Query("select coalesce(sum(m.weightage),0) from FixedCostProjectMilestone m where m.project.id=:projectId and m.active=true and (:excludeId is null or m.id<>:excludeId)") BigDecimal allocatedWeightage(@Param("projectId") Long projectId,@Param("excludeId") Long excludeId);
 @Query("select coalesce(sum(m.amount),0) from FixedCostProjectMilestone m where m.project.id=:projectId and m.active=true and (:excludeId is null or m.id<>:excludeId)") BigDecimal allocatedAmount(@Param("projectId") Long projectId,@Param("excludeId") Long excludeId);
}
