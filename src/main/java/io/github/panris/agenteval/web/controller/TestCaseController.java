package io.github.panris.agenteval.web.controller;

import io.github.panris.agenteval.model.TestCaseEntity;
import io.github.panris.agenteval.repository.TestCaseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * REST API for test case management.
 */
@RestController
@RequestMapping("/api/testcases")
public class TestCaseController {

    private static final Logger log = LoggerFactory.getLogger(TestCaseController.class);

    private final TestCaseRepository repository;

    public TestCaseController(TestCaseRepository repository) {
        this.repository = repository;
    }

    /**
     * Create a new test case.
     */
    @PostMapping
    public Map<String, Object> createTestCase(@RequestBody TestCaseRequest request) {
        if (request.getInput() == null || request.getInput().isBlank()) {
            log.warn("createTestCase: input is required");
            return Map.of("success", false, "error", "Input is required");
        }
        if (request.getExpected() == null || request.getExpected().isBlank()) {
            log.warn("createTestCase: expected is required");
            return Map.of("success", false, "error", "Expected output is required");
        }
        if (request.getInput().length() > 10000 || request.getExpected().length() > 10000) {
            log.warn("createTestCase: input or expected exceeds 10000 characters");
            return Map.of("success", false, "error", "Input or expected output exceeds 10000 characters");
        }
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
        if (request.getInput() == null || request.getInput().isBlank()) {
            log.warn("updateTestCase [{}]: input is required", id);
            return Map.of("success", false, "error", "Input is required");
        }
        if (request.getExpected() == null || request.getExpected().isBlank()) {
            log.warn("updateTestCase [{}]: expected is required", id);
            return Map.of("success", false, "error", "Expected output is required");
        }
        if (request.getInput().length() > 10000 || request.getExpected().length() > 10000) {
            log.warn("updateTestCase [{}]: input or expected exceeds 10000 chars", id);
            return Map.of("success", false, "error", "Input or expected output exceeds 10000 characters");
        }
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
     * Update test case tags.
     */
    @PutMapping("/{id}/tags")
    public Map<String, Object> updateTags(
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        Optional<TestCaseEntity> opt = repository.findTestCaseById(id);
        if (opt.isEmpty()) {
            return Map.of("success", false, "error", "Test case not found");
        }
        TestCaseEntity tc = opt.get();
        tc.getMetadata().put("tags", body.get("tags"));
        tc.updateTimestamp();
        repository.saveTestCase(tc);
        return Map.of("success", true, "tags", tc.getMetadata().get("tags"));
    }
    
    /**
     * Batch import test cases.
     */
    @PostMapping("/batch")
    public Map<String, Object> batchImport(@RequestBody List<TestCaseRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            log.warn("batchImport: request list is empty");
            return Map.of("success", false, "error", "Request list is empty");
        }
        if (requests.size() > 100) {
            log.warn("batchImport: batch size {} exceeds 100 items", requests.size());
            return Map.of("success", false, "error", "Batch size exceeds 100 items");
        }
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
