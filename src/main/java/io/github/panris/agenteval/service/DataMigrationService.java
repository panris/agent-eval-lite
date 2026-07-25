package io.github.panris.agenteval.service;

import io.github.panris.agenteval.model.AgentConfig;
import io.github.panris.agenteval.model.AgentConfigEntity;
import io.github.panris.agenteval.model.EvalLlmConfig;
import io.github.panris.agenteval.model.EvalLlmConfigEntity;
import io.github.panris.agenteval.model.ReportEntity;
import io.github.panris.agenteval.model.TestCaseEntity;
import io.github.panris.agenteval.repository.AgentConfigJpaRepository;
import io.github.panris.agenteval.repository.EvalLlmConfigJpaRepository;
import io.github.panris.agenteval.repository.ReportJpaRepository;
import io.github.panris.agenteval.repository.TestCaseJpaRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

@Service
public class DataMigrationService {

    private static final Logger log = LoggerFactory.getLogger(DataMigrationService.class);

    private final String dataDir;
    private final TestCaseJpaRepository testCaseJpaRepository;
    private final ReportJpaRepository reportJpaRepository;
    private final AgentConfigJpaRepository agentConfigJpaRepository;
    private final EvalLlmConfigJpaRepository evalLlmConfigJpaRepository;
    private final ObjectMapper objectMapper;

    public DataMigrationService(@Value("${data.dir:data}") String dataDir,
                                TestCaseJpaRepository testCaseJpaRepository,
                                ReportJpaRepository reportJpaRepository,
                                AgentConfigJpaRepository agentConfigJpaRepository,
                                EvalLlmConfigJpaRepository evalLlmConfigJpaRepository) {
        this.dataDir = dataDir;
        this.testCaseJpaRepository = testCaseJpaRepository;
        this.reportJpaRepository = reportJpaRepository;
        this.agentConfigJpaRepository = agentConfigJpaRepository;
        this.evalLlmConfigJpaRepository = evalLlmConfigJpaRepository;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
    }

    @PostConstruct
    public void migrateData() {
        migrateTestCases();
        migrateReports();
        migrateAgentConfigs();
        migrateEvalLlmConfigs();
    }

    private void migrateTestCases() {
        Path testCasesFile = Paths.get(dataDir, "testcases.json");
        if (!Files.exists(testCasesFile)) {
            log.info("No testcases.json file found, skipping migration");
            return;
        }

        if (testCaseJpaRepository.count() > 0) {
            log.info("Test cases already exist in database, skipping migration");
            return;
        }

        try {
            String content = Files.readString(testCasesFile);
            List<TestCaseEntity> testCases = objectMapper.readValue(content, new TypeReference<List<TestCaseEntity>>() {});

            for (TestCaseEntity tc : testCases) {
                if (tc.getMetadata() != null && !tc.getMetadata().isEmpty()) {
                    tc.setMetadata(tc.getMetadata());
                }
                testCaseJpaRepository.save(tc);
            }

            log.info("Migrated {} test cases from JSON to database", testCases.size());

            Path backupFile = Paths.get(dataDir, "backup", "testcases_migrated_" + System.currentTimeMillis() + ".json");
            Files.createDirectories(backupFile.getParent());
            Files.copy(testCasesFile, backupFile);
            log.info("Backed up testcases.json to {}", backupFile);

        } catch (Exception e) {
            log.error("Failed to migrate test cases: {}", e.getMessage(), e);
        }
    }

    private void migrateReports() {
        Path reportsFile = Paths.get(dataDir, "reports.json");
        if (!Files.exists(reportsFile)) {
            log.info("No reports.json file found, skipping migration");
            return;
        }

        if (reportJpaRepository.count() > 0) {
            log.info("Reports already exist in database, skipping migration");
            return;
        }

        try {
            String content = Files.readString(reportsFile);
            Map<String, Map<String, Object>> reports = objectMapper.readValue(content,
                objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Map.class));

            int migrated = 0;
            for (Map.Entry<String, Map<String, Object>> entry : reports.entrySet()) {
                String reportId = entry.getKey();
                Map<String, Object> reportData = entry.getValue();

                ReportEntity entity = new ReportEntity(reportId);

                Object summary = reportData.get("summary");
                if (summary != null) {
                    entity.setSummaryJson(objectMapper.writeValueAsString(summary));
                }

                Object evaluations = reportData.get("evaluations");
                if (evaluations != null) {
                    entity.setEvaluationsJson(objectMapper.writeValueAsString(evaluations));
                }

                entity.setTotalTestCases(getIntValue(reportData, "totalTestCases", "total_test_cases"));
                entity.setPassedTestCases(getIntValue(reportData, "passedTestCases", "passed_test_cases"));
                entity.setFailedTestCases(getIntValue(reportData, "failedTestCases", "failed_test_cases"));
                entity.setExecutionTimeMs(getLongValue(reportData, "executionTimeMs", "execution_time_ms"));
                entity.setTimestamp(getLongValue(reportData, "timestamp"));
                entity.setFavorite((Boolean) reportData.getOrDefault("favorite", false));
                entity.setNote((String) reportData.get("note"));
                entity.setGroup((String) reportData.get("group"));
                entity.setProject((String) reportData.get("project"));
                entity.setModule((String) reportData.get("module"));
                entity.setFunction((String) reportData.get("function"));

                Object tags = reportData.get("tags");
                if (tags != null) {
                    entity.setTagsJson(objectMapper.writeValueAsString(tags));
                }

                reportJpaRepository.save(entity);
                migrated++;
            }

            log.info("Migrated {} reports from JSON to database", migrated);

            Path backupFile = Paths.get(dataDir, "backup", "reports_migrated_" + System.currentTimeMillis() + ".json");
            Files.createDirectories(backupFile.getParent());
            Files.copy(reportsFile, backupFile);
            log.info("Backed up reports.json to {}", backupFile);

        } catch (Exception e) {
            log.error("Failed to migrate reports: {}", e.getMessage(), e);
        }
    }

    private void migrateAgentConfigs() {
        Path agentsFile = Paths.get(dataDir, "agents.json");
        if (!Files.exists(agentsFile)) {
            log.info("No agents.json file found, skipping migration");
            return;
        }

        if (agentConfigJpaRepository.count() > 0) {
            log.info("Agent configs already exist in database, skipping migration");
            return;
        }

        try {
            String content = Files.readString(agentsFile);
            Map<String, AgentConfig> agents = objectMapper.readValue(content,
                objectMapper.getTypeFactory().constructMapType(Map.class, String.class, AgentConfig.class));

            int migrated = 0;
            for (Map.Entry<String, AgentConfig> entry : agents.entrySet()) {
                AgentConfig config = entry.getValue();
                AgentConfigEntity entity = new AgentConfigEntity();
                entity.setId(config.getId());
                entity.setName(config.getName());
                entity.setType(config.getType());
                entity.setDescription(config.getDescription());
                entity.setEndpoint(config.getEndpoint());
                entity.setTimeout(config.getTimeout());
                entity.setCreatedAt(config.getCreatedAt());
                entity.setUpdatedAt(config.getUpdatedAt());

                if (config.getHeaders() != null) {
                    entity.setHeadersJson(objectMapper.writeValueAsString(config.getHeaders()));
                }
                if (config.getRequestMapping() != null) {
                    entity.setRequestMappingJson(objectMapper.writeValueAsString(config.getRequestMapping()));
                }
                if (config.getResponseMapping() != null) {
                    entity.setResponseMappingJson(objectMapper.writeValueAsString(config.getResponseMapping()));
                }
                if (config.getConfig() != null) {
                    entity.setConfigJson(objectMapper.writeValueAsString(config.getConfig()));
                }

                agentConfigJpaRepository.save(entity);
                migrated++;
            }

            log.info("Migrated {} agent configs from JSON to database", migrated);

            Path backupFile = Paths.get(dataDir, "backup", "agents_migrated_" + System.currentTimeMillis() + ".json");
            Files.createDirectories(backupFile.getParent());
            Files.copy(agentsFile, backupFile);
            log.info("Backed up agents.json to {}", backupFile);

        } catch (Exception e) {
            log.error("Failed to migrate agent configs: {}", e.getMessage(), e);
        }
    }

    private void migrateEvalLlmConfigs() {
        Path llmConfigsFile = Paths.get(dataDir, "eval-llm-configs.json");
        if (!Files.exists(llmConfigsFile)) {
            log.info("No eval-llm-configs.json file found, skipping migration");
            return;
        }

        if (evalLlmConfigJpaRepository.count() > 0) {
            log.info("Eval LLM configs already exist in database, skipping migration");
            return;
        }

        try {
            String content = Files.readString(llmConfigsFile);
            Map<String, EvalLlmConfig> configs = objectMapper.readValue(content,
                objectMapper.getTypeFactory().constructMapType(Map.class, String.class, EvalLlmConfig.class));

            int migrated = 0;
            for (Map.Entry<String, EvalLlmConfig> entry : configs.entrySet()) {
                EvalLlmConfig config = entry.getValue();
                EvalLlmConfigEntity entity = new EvalLlmConfigEntity();
                entity.setId(config.getId());
                entity.setName(config.getName());
                entity.setBaseUrl(config.getBaseUrl());
                entity.setApiKey(config.getApiKey());
                entity.setModel(config.getModel());
                entity.setTemperature(config.getTemperature());
                entity.setMaxTokens(config.getMaxTokens());
                entity.setTimeout(config.getTimeout());
                entity.setPassThreshold(config.getPassThreshold());
                entity.setSystemPrompt(config.getSystemPrompt());
                entity.setCreatedAt(config.getCreatedAt());
                entity.setUpdatedAt(config.getUpdatedAt());

                evalLlmConfigJpaRepository.save(entity);
                migrated++;
            }

            log.info("Migrated {} eval LLM configs from JSON to database", migrated);

            Path backupFile = Paths.get(dataDir, "backup", "eval-llm-configs_migrated_" + System.currentTimeMillis() + ".json");
            Files.createDirectories(backupFile.getParent());
            Files.copy(llmConfigsFile, backupFile);
            log.info("Backed up eval-llm-configs.json to {}", backupFile);

        } catch (Exception e) {
            log.error("Failed to migrate eval LLM configs: {}", e.getMessage(), e);
        }
    }

    private Integer getIntValue(Map<String, Object> data, String... keys) {
        for (String key : keys) {
            Object value = data.get(key);
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
        }
        return null;
    }

    private Long getLongValue(Map<String, Object> data, String... keys) {
        for (String key : keys) {
            Object value = data.get(key);
            if (value instanceof Number) {
                return ((Number) value).longValue();
            }
        }
        return null;
    }
}