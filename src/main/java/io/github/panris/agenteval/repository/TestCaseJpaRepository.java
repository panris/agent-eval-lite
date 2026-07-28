package io.github.panris.agenteval.repository;

import io.github.panris.agenteval.model.TestCaseEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TestCaseJpaRepository extends JpaRepository<TestCaseEntity, String> {

    List<TestCaseEntity> findByDeletedFalse();

    long countByDeletedFalse();

    List<TestCaseEntity> findByDeletedTrue();

    Optional<TestCaseEntity> findByIdAndDeletedFalse(String id);

    List<TestCaseEntity> findByProjectAndDeletedFalse(String project);

    List<TestCaseEntity> findByProjectAndModuleAndDeletedFalse(String project, String module);

    List<TestCaseEntity> findByProjectAndModuleAndFunctionAndDeletedFalse(String project, String module, String function);

    @Query("SELECT DISTINCT t.project FROM TestCaseEntity t WHERE t.deleted = false AND t.project IS NOT NULL")
    List<String> findDistinctProjects();

    @Query("SELECT DISTINCT t.module FROM TestCaseEntity t WHERE t.deleted = false AND t.project = :project AND t.module IS NOT NULL")
    List<String> findDistinctModulesByProject(@Param("project") String project);

    @Query("SELECT DISTINCT t.function FROM TestCaseEntity t WHERE t.deleted = false AND t.project = :project AND t.module = :module AND t.function IS NOT NULL")
    List<String> findDistinctFunctionsByProjectAndModule(@Param("project") String project, @Param("module") String module);
}