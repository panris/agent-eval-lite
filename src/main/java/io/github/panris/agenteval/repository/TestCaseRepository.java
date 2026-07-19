package io.github.panris.agenteval.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.panris.agenteval.model.TestCaseEntity;
import io.github.panris.agenteval.model.TestCaseGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Repository for managing test cases and groups using JSON file storage.
 */
@Repository
public class TestCaseRepository {

    private static final Logger logger = LoggerFactory.getLogger(TestCaseRepository.class);

    private final ObjectMapper objectMapper;
    private final File testCasesFile;
    private final File groupsFile;

    private final Map<String, TestCaseEntity> testCases = new ConcurrentHashMap<>();
    private final Map<String, TestCaseGroup> groups = new ConcurrentHashMap<>();

    public TestCaseRepository(@Value("${data.dir:data}") String dataDir) {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);

        File dataDirFile = new File(dataDir);
        if (!dataDirFile.exists()) {
            dataDirFile.mkdirs();
        }

        this.testCasesFile = new File(dataDir, "testcases.json");
        this.groupsFile = new File(dataDir, "groups.json");

        loadData();
    }

    // ============ Test Case Operations ============

    public TestCaseEntity saveTestCase(TestCaseEntity testCase) {
        if (testCase.getId() == null || testCase.getId().isEmpty()) {
            testCase.setId(UUID.randomUUID().toString());
        }
        testCase.updateTimestamp();
        testCases.put(testCase.getId(), testCase);
        saveData();
        logger.info("Saved test case: {}", testCase.getId());
        return testCase;
    }

    public Optional<TestCaseEntity> findTestCaseById(String id) {
        return Optional.ofNullable(testCases.get(id));
    }

    public List<TestCaseEntity> findAllTestCases() {
        return new ArrayList<>(testCases.values());
    }

    public List<TestCaseEntity> findAllTestCasesPage(int page, int size) {
        List<TestCaseEntity> all = new ArrayList<>(testCases.values());
        int from = (page - 1) * size;
        if (from >= all.size()) return List.of();
        int to = Math.min(from + size, all.size());
        return all.subList(from, to);
    }

    public int countAllTestCases() {
        return testCases.size();
    }

    public List<TestCaseEntity> findTestCasesByGroupId(String groupId) {
        return testCases.values().stream()
            .filter(tc -> groupId.equals(tc.getGroupId()))
            .collect(Collectors.toList());
    }

    public List<TestCaseEntity> findTestCasesByGroupIdPage(String groupId, int page, int size) {
        List<TestCaseEntity> all = testCases.values().stream()
            .filter(tc -> groupId.equals(tc.getGroupId()))
            .collect(Collectors.toList());
        int from = (page - 1) * size;
        if (from >= all.size()) return List.of();
        int to = Math.min(from + size, all.size());
        return all.subList(from, to);
    }

    public int countTestCasesByGroupId(String groupId) {
        return (int) testCases.values().stream()
            .filter(tc -> groupId.equals(tc.getGroupId()))
            .count();
    }

    /**
     * 按三维分组筛选测试用例。任一维度为 null 或空表示不限制该维度。
     * 三个维度之间是 AND 关系。
     */
    public List<TestCaseEntity> findTestCasesByDimensions(String project, String module, String function) {
        final String p = (project != null && !project.isBlank()) ? project.trim() : null;
        final String m = (module != null && !module.isBlank()) ? module.trim() : null;
        final String f = (function != null && !function.isBlank()) ? function.trim() : null;
        return testCases.values().stream()
            .filter(tc -> p == null || p.equalsIgnoreCase(nullToEmpty(tc.getProject())))
            .filter(tc -> m == null || m.equalsIgnoreCase(nullToEmpty(tc.getModule())))
            .filter(tc -> f == null || f.equalsIgnoreCase(nullToEmpty(tc.getFunction())))
            .collect(Collectors.toList());
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
        return distinctValues(TestCaseEntity::getProject);
    }

    public List<String> findDistinctModules() {
        return distinctValues(TestCaseEntity::getModule);
    }

    public List<String> findDistinctFunctions() {
        return distinctValues(TestCaseEntity::getFunction);
    }

    private List<String> distinctValues(java.util.function.Function<TestCaseEntity, String> getter) {
        return testCases.values().stream()
            .map(getter)
            .filter(v -> v != null && !v.isBlank())
            .distinct()
            .sorted()
            .collect(Collectors.toList());
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    public void deleteTestCase(String id) {
        TestCaseEntity removed = testCases.remove(id);
        if (removed != null) {
            // Remove from groups
            groups.values().forEach(group -> group.removeTestCaseId(id));
            saveData();
            logger.info("Deleted test case: {}", id);
        }
    }

    public List<TestCaseEntity> saveAllTestCases(List<TestCaseEntity> testCaseList) {
        List<TestCaseEntity> saved = new ArrayList<>();
        for (TestCaseEntity testCase : testCaseList) {
            if (testCase.getId() == null || testCase.getId().isEmpty()) {
                testCase.setId(UUID.randomUUID().toString());
            }
            testCase.updateTimestamp();
            testCases.put(testCase.getId(), testCase);
            saved.add(testCase);
        }
        saveData();
        logger.info("Saved {} test cases in batch", saved.size());
        return saved;
    }

    // ============ Group Operations ============

    public TestCaseGroup saveGroup(TestCaseGroup group) {
        if (group.getId() == null || group.getId().isEmpty()) {
            group.setId(UUID.randomUUID().toString());
        }
        group.updateTimestamp();
        groups.put(group.getId(), group);
        saveData();
        logger.info("Saved group: {}", group.getId());
        return group;
    }

    public Optional<TestCaseGroup> findGroupById(String id) {
        return Optional.ofNullable(groups.get(id));
    }

    public List<TestCaseGroup> findAllGroups() {
        return new ArrayList<>(groups.values());
    }

    public void deleteGroup(String id) {
        TestCaseGroup removed = groups.remove(id);
        if (removed != null) {
            // Remove group reference from test cases
            removed.getTestCaseIds().forEach(caseId -> {
                TestCaseEntity testCase = testCases.get(caseId);
                if (testCase != null) {
                    testCase.setGroupId(null);
                }
            });
            saveData();
            logger.info("Deleted group: {}", id);
        }
    }

    public TestCaseGroup addTestCaseToGroup(String groupId, String testCaseId) {
        TestCaseGroup group = groups.get(groupId);
        if (group != null && testCases.containsKey(testCaseId)) {
            group.addTestCaseId(testCaseId);
            TestCaseEntity testCase = testCases.get(testCaseId);
            testCase.setGroupId(groupId);
            saveData();
            logger.info("Added test case {} to group {}", testCaseId, groupId);
        }
        return group;
    }

    public TestCaseGroup removeTestCaseFromGroup(String groupId, String testCaseId) {
        TestCaseGroup group = groups.get(groupId);
        if (group != null) {
            group.removeTestCaseId(testCaseId);
            TestCaseEntity testCase = testCases.get(testCaseId);
            if (testCase != null && groupId.equals(testCase.getGroupId())) {
                testCase.setGroupId(null);
            }
            saveData();
            logger.info("Removed test case {} from group {}", testCaseId, groupId);
        }
        return group;
    }

    // ============ Data Persistence ============

    private void loadData() {
        try {
            if (testCasesFile.exists()) {
                TestCaseEntity[] casesArray = objectMapper.readValue(testCasesFile, TestCaseEntity[].class);
                for (TestCaseEntity testCase : casesArray) {
                    testCases.put(testCase.getId(), testCase);
                }
                logger.info("Loaded {} test cases", testCases.size());
            }

            if (groupsFile.exists()) {
                TestCaseGroup[] groupsArray = objectMapper.readValue(groupsFile, TestCaseGroup[].class);
                for (TestCaseGroup group : groupsArray) {
                    groups.put(group.getId(), group);
                }
                logger.info("Loaded {} groups", groups.size());
            }
        } catch (IOException e) {
            logger.error("Failed to load data", e);
        }
    }

    private void saveData() {
        try {
            objectMapper.writeValue(testCasesFile, testCases.values());
            objectMapper.writeValue(groupsFile, groups.values());
            logger.debug("Data saved successfully");
        } catch (IOException e) {
            logger.error("Failed to save data", e);
        }
    }
}
