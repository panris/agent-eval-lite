package io.github.panris.agenteval.web.controller;

import io.github.panris.agenteval.model.TestCaseEntity;
import io.github.panris.agenteval.repository.TestCaseRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API for test case management.
 */
@RestController
@RequestMapping("/api/testcases")
public class TestCaseController {

    private final TestCaseRepository repository;

    public TestCaseController(TestCaseRepository repository) {
        this.repository = repository;
    }

    /**
     * Create a new test case.
     */
    @PostMapping
    public Map<String, Object> createTestCase(@RequestBody TestCaseRequest request) {
        TestCaseEntity testCase = new TestCaseEntity(
            request.getName(),
            request.getInput(),
            request.getExpected()
        );
        testCase.setGroupId(request.getGroupId());
        testCase.setMetadata(request.getMetadata());

        TestCaseEntity saved = repository.saveTestCase(testCase);

        return Map.of(
            "success", true,
            "testCase", saved
        );
    }

    /**
     * List all test cases.
     */
    @GetMapping
    public Map<String, Object> listTestCases(
        @RequestParam(required = false) String groupId
    ) {
        List<TestCaseEntity> testCases;
        if (groupId != null && !groupId.isEmpty()) {
            testCases = repository.findTestCasesByGroupId(groupId);
        } else {
            testCases = repository.findAllTestCases();
        }

        return Map.of(
            "success", true,
            "testCases", testCases,
            "total", testCases.size()
        );
    }

    /**
     * Get a specific test case.
     */
    @GetMapping("/{id}")
    public Map<String, Object> getTestCase(@PathVariable String id) {
        return repository.findTestCaseById(id)
            .map(tc -> Map.<String, Object>of(
                "success", true,
                "testCase", tc
            ))
            .orElse(Map.of(
                "success", false,
                "error", "Test case not found"
            ));
    }

    /**
     * Update a test case.
     */
    @PutMapping("/{id}")
    public Map<String, Object> updateTestCase(
        @PathVariable String id,
        @RequestBody TestCaseRequest request
    ) {
        return repository.findTestCaseById(id)
            .map(tc -> {
                tc.setName(request.getName());
                tc.setInput(request.getInput());
                tc.setExpected(request.getExpected());
                if (request.getGroupId() != null) {
                    tc.setGroupId(request.getGroupId());
                }
                if (request.getMetadata() != null) {
                    tc.setMetadata(request.getMetadata());
                }
                TestCaseEntity saved = repository.saveTestCase(tc);
                return Map.<String, Object>of(
                    "success", true,
                    "testCase", saved
                );
            })
            .orElse(Map.of(
                "success", false,
                "error", "Test case not found"
            ));
    }

    /**
     * Delete a test case.
     */
    @DeleteMapping("/{id}")
    public Map<String, Object> deleteTestCase(@PathVariable String id) {
        if (repository.findTestCaseById(id).isPresent()) {
            repository.deleteTestCase(id);
            return Map.of(
                "success", true,
                "message", "Test case deleted"
            );
        }
        return Map.of(
            "success", false,
            "error", "Test case not found"
        );
    }

    /**
     * Batch import test cases.
     */
    @PostMapping("/batch")
    public Map<String, Object> batchImport(@RequestBody List<TestCaseRequest> requests) {
        List<TestCaseEntity> testCases = requests.stream()
            .map(req -> {
                TestCaseEntity tc = new TestCaseEntity(
                    req.getName(),
                    req.getInput(),
                    req.getExpected()
                );
                tc.setGroupId(req.getGroupId());
                tc.setMetadata(req.getMetadata());
                return tc;
            })
            .toList();

        List<TestCaseEntity> saved = repository.saveAllTestCases(testCases);

        return Map.of(
            "success", true,
            "imported", saved.size(),
            "testCases", saved
        );
    }
}

class TestCaseRequest {
    private String name;
    private String input;
    private String expected;
    private String groupId;
    private Map<String, Object> metadata;

    // Getters and Setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getInput() {
        return input;
    }

    public void setInput(String input) {
        this.input = input;
    }

    public String getExpected() {
        return expected;
    }

    public void setExpected(String expected) {
        this.expected = expected;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}
