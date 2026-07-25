package io.github.panris.agenteval.repository;

import io.github.panris.agenteval.model.TestCaseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
public class TestCaseRepository {

    private static final Logger logger = LoggerFactory.getLogger(TestCaseRepository.class);

    private final TestCaseJpaRepository jpaRepository;

    public TestCaseRepository(TestCaseJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    public TestCaseEntity saveTestCase(TestCaseEntity testCase) {
        if (testCase.getId() == null || testCase.getId().isEmpty()) {
            testCase.setId(UUID.randomUUID().toString());
        }
        testCase.updateTimestamp();
        TestCaseEntity saved = jpaRepository.save(testCase);
        logger.info("Saved test case: {}", saved.getId());
        return saved;
    }

    public Optional<TestCaseEntity> findTestCaseById(String id) {
        return jpaRepository.findById(id);
    }

    public List<TestCaseEntity> findAllTestCases() {
        return jpaRepository.findByDeletedFalse();
    }

    public List<TestCaseEntity> findAllTestCasesPage(int page, int size) {
        List<TestCaseEntity> all = findAllTestCases();
        int from = (page - 1) * size;
        if (from >= all.size()) return List.of();
        int to = Math.min(from + size, all.size());
        return all.subList(from, to);
    }

    public int countAllTestCases() {
        return (int) jpaRepository.count();
    }

    public List<TestCaseEntity> findTestCasesByDimensions(String project, String module, String function) {
        final String p = (project != null && !project.isBlank()) ? project.trim() : null;
        final String m = (module != null && !module.isBlank()) ? module.trim() : null;
        final String f = (function != null && !function.isBlank()) ? function.trim() : null;
        
        if (p != null && m != null && f != null) {
            return jpaRepository.findByProjectAndModuleAndFunctionAndDeletedFalse(p, m, f);
        } else if (p != null && m != null) {
            return jpaRepository.findByProjectAndModuleAndDeletedFalse(p, m);
        } else if (p != null) {
            return jpaRepository.findByProjectAndDeletedFalse(p);
        } else {
            return jpaRepository.findByDeletedFalse();
        }
    }

    public List<TestCaseEntity> findTestCasesByDimensionsPage(String project, String module, String function, int page, int size) {
        List<TestCaseEntity> all = findTestCasesByDimensions(project, module, function);
        int from = (page - 1) * size;
        if (from >= all.size()) return List.of();
        int to = Math.min(from + size, all.size());
        return all.subList(from, to);
    }

    public int countTestCasesByDimensions(String project, String module, String function) {
        return findTestCasesByDimensions(project, module, function).size();
    }

    public List<String> findDistinctProjects() {
        return jpaRepository.findDistinctProjects();
    }

    public List<String> findDistinctModules() {
        return jpaRepository.findAll().stream()
            .map(TestCaseEntity::getModule)
            .filter(v -> v != null && !v.isBlank())
            .distinct()
            .sorted()
            .collect(Collectors.toList());
    }

    public List<String> findDistinctFunctions() {
        return jpaRepository.findAll().stream()
            .map(TestCaseEntity::getFunction)
            .filter(v -> v != null && !v.isBlank())
            .distinct()
            .sorted()
            .collect(Collectors.toList());
    }

    public void deleteTestCase(String id) {
        Optional<TestCaseEntity> opt = jpaRepository.findById(id);
        if (opt.isPresent()) {
            TestCaseEntity tc = opt.get();
            tc.setDeleted(true);
            tc.setDeletedAt(LocalDateTime.now());
            tc.setUpdatedAt(LocalDateTime.now());
            jpaRepository.save(tc);
            logger.info("Soft deleted test case: {}", id);
        }
    }

    public void restoreTestCase(String id) {
        Optional<TestCaseEntity> opt = jpaRepository.findById(id);
        if (opt.isPresent()) {
            TestCaseEntity tc = opt.get();
            tc.setDeleted(false);
            tc.setDeletedAt(null);
            tc.setUpdatedAt(LocalDateTime.now());
            jpaRepository.save(tc);
            logger.info("Restored test case: {}", id);
        }
    }

    public List<TestCaseEntity> findDeletedTestCases() {
        return jpaRepository.findByDeletedTrue();
    }

    public void forceDeleteTestCase(String id) {
        jpaRepository.deleteById(id);
        logger.info("Force deleted test case: {}", id);
    }

    public List<TestCaseEntity> saveAllTestCases(List<TestCaseEntity> testCaseList) {
        List<TestCaseEntity> saved = new ArrayList<>();
        for (TestCaseEntity testCase : testCaseList) {
            if (testCase.getId() == null || testCase.getId().isEmpty()) {
                testCase.setId(UUID.randomUUID().toString());
            }
            testCase.updateTimestamp();
            saved.add(jpaRepository.save(testCase));
        }
        logger.info("Saved {} test cases in batch", saved.size());
        return saved;
    }
}