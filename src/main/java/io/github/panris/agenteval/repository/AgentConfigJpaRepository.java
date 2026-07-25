package io.github.panris.agenteval.repository;

import io.github.panris.agenteval.model.AgentConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AgentConfigJpaRepository extends JpaRepository<AgentConfigEntity, String> {
    List<AgentConfigEntity> findByType(String type);
    List<AgentConfigEntity> findByNameContaining(String name);
}