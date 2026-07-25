package io.github.panris.agenteval.repository;

import io.github.panris.agenteval.model.SharedReportEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SharedReportJpaRepository extends JpaRepository<SharedReportEntity, String> {

    List<SharedReportEntity> findByReportId(String reportId);
}