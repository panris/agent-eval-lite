package io.github.panris.agenteval.web.controller;

import io.github.panris.agenteval.model.TestCaseGroup;
import io.github.panris.agenteval.repository.TestCaseRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API for test case group management.
 */
@RestController
@RequestMapping("/api/groups")
public class GroupController {

    private final TestCaseRepository repository;

    public GroupController(TestCaseRepository repository) {
        this.repository = repository;
    }

    /**
     * Create a new group.
     */
    @PostMapping
    public Map<String, Object> createGroup(@RequestBody GroupRequest request) {
        TestCaseGroup group = new TestCaseGroup(
            request.getName(),
            request.getDescription()
        );

        TestCaseGroup saved = repository.saveGroup(group);

        return Map.of(
            "success", true,
            "group", saved
        );
    }

    /**
     * List all groups.
     */
    @GetMapping
    public Map<String, Object> listGroups() {
        List<TestCaseGroup> groups = repository.findAllGroups();

        return Map.of(
            "success", true,
            "groups", groups,
            "total", groups.size()
        );
    }

    /**
     * Get a specific group with test cases.
     */
    @GetMapping("/{id}")
    public Map<String, Object> getGroup(@PathVariable String id) {
        return repository.findGroupById(id)
            .map(group -> {
                List<Map<String, Object>> testCases = group.getTestCaseIds().stream()
                    .map(caseId -> repository.findTestCaseById(caseId))
                    .filter(opt -> opt.isPresent())
                    .map(opt -> Map.<String, Object>of(
                        "id", opt.get().getId(),
                        "name", opt.get().getName(),
                        "input", opt.get().getInput(),
                        "expected", opt.get().getExpected()
                    ))
                    .toList();

                return Map.<String, Object>of(
                    "success", true,
                    "group", group,
                    "testCases", testCases
                );
            })
            .orElse(Map.of(
                "success", false,
                "error", "Group not found"
            ));
    }

    /**
     * Update a group.
     */
    @PutMapping("/{id}")
    public Map<String, Object> updateGroup(
        @PathVariable String id,
        @RequestBody GroupRequest request
    ) {
        return repository.findGroupById(id)
            .map(group -> {
                group.setName(request.getName());
                group.setDescription(request.getDescription());
                TestCaseGroup saved = repository.saveGroup(group);
                return Map.<String, Object>of(
                    "success", true,
                    "group", saved
                );
            })
            .orElse(Map.of(
                "success", false,
                "error", "Group not found"
            ));
    }

    /**
     * Delete a group.
     */
    @DeleteMapping("/{id}")
    public Map<String, Object> deleteGroup(@PathVariable String id) {
        if (repository.findGroupById(id).isPresent()) {
            repository.deleteGroup(id);
            return Map.of(
                "success", true,
                "message", "Group deleted"
            );
        }
        return Map.of(
            "success", false,
            "error", "Group not found"
        );
    }

    /**
     * Add test case to group.
     */
    @PostMapping("/{id}/testcases")
    public Map<String, Object> addTestCaseToGroup(
        @PathVariable String id,
        @RequestBody Map<String, String> request
    ) {
        String testCaseId = request.get("testCaseId");
        if (testCaseId == null) {
            return Map.of(
                "success", false,
                "error", "testCaseId is required"
            );
        }

        TestCaseGroup group = repository.addTestCaseToGroup(id, testCaseId);
        if (group == null) {
            return Map.of(
                "success", false,
                "error", "Group or test case not found"
            );
        }

        return Map.of(
            "success", true,
            "group", group
        );
    }

    /**
     * Remove test case from group.
     */
    @DeleteMapping("/{id}/testcases/{testCaseId}")
    public Map<String, Object> removeTestCaseFromGroup(
        @PathVariable String id,
        @PathVariable String testCaseId
    ) {
        TestCaseGroup group = repository.removeTestCaseFromGroup(id, testCaseId);
        if (group == null) {
            return Map.of(
                "success", false,
                "error", "Group not found"
            );
        }

        return Map.of(
            "success", true,
            "group", group
        );
    }
}

class GroupRequest {
    private String name;
    private String description;

    // Getters and Setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
