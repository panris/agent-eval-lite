package io.github.panris.agenteval.repository;

import io.github.panris.agenteval.model.EvalModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EvalModelRepository extends JpaRepository<EvalModel, String> {
    Optional<EvalModel> findByIsDefaultTrue();
    List<EvalModel> findByProvider(String provider);
    List<EvalModel> findByNameContaining(String name);
}