package io.github.panris.agenteval.repository;

import io.github.panris.agenteval.model.EvalDimensionConfig;
import io.github.panris.agenteval.model.EvalDimensionConfig.DimensionLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EvalDimensionConfigRepository extends JpaRepository<EvalDimensionConfig, String> {
    List<EvalDimensionConfig> findByLevel(DimensionLevel level);
    List<EvalDimensionConfig> findByLevelAndProject(DimensionLevel level, String project);
    List<EvalDimensionConfig> findByLevelAndProjectAndModule(DimensionLevel level, String project, String module);
    List<EvalDimensionConfig> findByLevelAndProjectAndModuleAndFunction(DimensionLevel level, String project, String module, String function);
    
    @Query("SELECT c FROM EvalDimensionConfig c WHERE c.level = 'GLOBAL'")
    Optional<EvalDimensionConfig> findGlobalConfig();
    
    @Query("SELECT c FROM EvalDimensionConfig c WHERE c.level = 'PROJECT' AND c.project = :project")
    Optional<EvalDimensionConfig> findProjectConfig(@Param("project") String project);
    
    @Query("SELECT c FROM EvalDimensionConfig c WHERE c.level = 'MODULE' AND c.project = :project AND c.module = :module")
    Optional<EvalDimensionConfig> findModuleConfig(@Param("project") String project, @Param("module") String module);
    
    @Query("SELECT c FROM EvalDimensionConfig c WHERE c.level = 'FUNCTION' AND c.project = :project AND c.module = :module AND c.function = :function")
    Optional<EvalDimensionConfig> findFunctionConfig(@Param("project") String project, @Param("module") String module, @Param("function") String function);
}