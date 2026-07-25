package io.github.panris.agenteval.repository;

import io.github.panris.agenteval.model.EvalLlmConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EvalLlmConfigJpaRepository extends JpaRepository<EvalLlmConfigEntity, String> {
    List<EvalLlmConfigEntity> findByNameContaining(String name);
}