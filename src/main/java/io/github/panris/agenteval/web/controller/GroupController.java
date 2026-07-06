package io.github.panris.agenteval.web.controller;

import io.github.panris.agenteval.model.TestCaseGroup;
import io.github.panris.agenteval.repository.TestCaseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API for test case group management.
 */
@RestController
@RequestMapping("/api/groups")
public class GroupController {

    private static final Logger log = LoggerFactory.getLogger(GroupController.class);

    private final TestCaseRepository repository;

    public GroupController(TestCaseRepository repository) {
        this.repository = repository;
    }

    /**
     * Create a new group.
     */
    @PostMapping
    public Map<String, Object> createGroup(@RequestBody GroupRequest request) {
        if (request == null || request.getName() == null || request.getName().isBlank()) {
            log.warn("createGroup: name is required");
            return Map.of("success", false, "error", "分组名称不能为空");
        }
        if (request.getName().length() > 200) {
            log.warn("createGroup: name too long, length={}", request.getName().length());
            return Map.of("success", false, "error", "分组名称不能超过 200 字符");
        }
        TestCaseGroup group = new TestCaseGroup(
            request.getName().trim(),
            request.getDescription()
        );
        TestCaseGroup saved = repository.saveGroup(group);
        log.info("Created group: id={}, name={}", saved.getId(), saved.getName());
        return Map.of("success", true, "group", saved);
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
        if (request == null || request.getName() == null || request.getName().isBlank()) {
            log.warn("updateGroup: name is required, id={}", id);
            return Map.of("success", false, "error", "分组名称不能为空");
        }
        if (request.getName().length() > 200) {
            log.warn("updateGroup: name too long, id={}, length={}", id, request.getName().length());
            return Map.of("success", false, "error", "分组名称不能超过 200 字符");
        }
        return repository.findGroupById(id)
            .map(group -> {
                group.setName(request.getName().trim());
                group.setDescription(request.getDescription());
                TestCaseGroup saved = repository.saveGroup(group);
                log.info("Updated group: id={}, name={}", saved.getId(), saved.getName());
                return Map.<String, Object>of("success", true, "group", saved);
            })
            .orElse(Map.of("success", false, "error", "分组不存在"));
    }

    /**
     * Delete a group.
     */
    @DeleteMapping("/{id}")
    public Map<String, Object> deleteGroup(@PathVariable String id) {
        if (repository.findGroupById(id).isPresent()) {
            repository.deleteGroup(id);
            log.info("Deleted group: id={}", id);
            return Map.of("success", true, "message", "Group deleted");
        }
        return Map.of("success", false, "error", "Group not found");
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
