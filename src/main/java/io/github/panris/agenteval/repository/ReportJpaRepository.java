package io.github.panris.agenteval.repository;

import io.github.panris.agenteval.model.ReportEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReportJpaRepository extends JpaRepository<ReportEntity, String> {

    List<ReportEntity> findByFavoriteTrue();

    List<ReportEntity> findByProject(String project);

    List<ReportEntity> findByProjectAndModule(String project, String module);

    List<ReportEntity> findByProjectAndModuleAndFunction(String project, String module, String function);

    List<ReportEntity> findByTimestampBetween(Long since, Long until);

    @Query("SELECT r FROM ReportEntity r ORDER BY r.timestamp DESC")
    List<ReportEntity> findAllOrderByTimestampDesc();

    @Query("SELECT r FROM ReportEntity r WHERE r.favorite = true ORDER BY r.timestamp DESC")
    List<ReportEntity> findFavoritesOrderByTimestampDesc();
}