package io.github.panris.agenteval.web.controller;

import io.github.panris.agenteval.model.TestCaseEntity;
import io.github.panris.agenteval.model.TestCaseGroup;
import io.github.panris.agenteval.repository.TestCaseRepository;
import org.junit.jupiter.api.*;
import org.springframework.ui.Model;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Pure unit tests for GroupController — no Spring context needed.
 */
class GroupControllerTest {

    private GroupController controller;
    private TestCaseRepository mockRepository;

    @BeforeEach
    void setUp() {
        mockRepository = mock(TestCaseRepository.class);
        controller = new GroupController(mockRepository);
    }

    // ============ POST /api/groups — create ============

    @Test
    @DisplayName("POST /api/groups with null request → BAD_REQUEST")
    void testCreateNullRequest() {
        Map<String, Object> resp = controller.createGroup(null);

        assertFalse((Boolean) resp.get("success"));
        assertEquals("分组名称不能为空", resp.get("error"));
    }

    @Test
    @DisplayName("POST /api/groups with blank name → BAD_REQUEST")
    void testCreateBlankName() {
        GroupRequest req = new GroupRequest();
        req.setName("   ");
        req.setDescription("desc");

        Map<String, Object> resp = controller.createGroup(req);

        assertFalse((Boolean) resp.get("success"));
        assertEquals("分组名称不能为空", resp.get("error"));
    }

    @Test
    @DisplayName("POST /api/groups with name too long → BAD_REQUEST")
    void testCreateNameTooLong() {
        GroupRequest req = new GroupRequest();
        req.setName("a".repeat(201));
        req.setDescription("desc");

        Map<String, Object> resp = controller.createGroup(req);

        assertFalse((Boolean) resp.get("success"));
        assertEquals("分组名称不能超过 200 字符", resp.get("error"));
    }

    @Test
    @DisplayName("POST /api/groups with valid request → success")
    void testCreateSuccess() {
        GroupRequest req = new GroupRequest();
        req.setName("Backend Tests");
        req.setDescription("API integration tests");

        TestCaseGroup saved = new TestCaseGroup("Backend Tests", "API integration tests");
        when(mockRepository.saveGroup(any(TestCaseGroup.class))).thenReturn(saved);

        Map<String, Object> resp = controller.createGroup(req);

        assertTrue((Boolean) resp.get("success"));
        assertNotNull(resp.get("group"));
        verify(mockRepository).saveGroup(any(TestCaseGroup.class));
    }

    // ============ GET /api/groups — list ============

    @Test
    @DisplayName("GET /api/groups with empty repository → empty list")
    void testListEmpty() {
        when(mockRepository.findAllGroups()).thenReturn(List.of());

        Map<String, Object> resp = controller.listGroups();

        assertTrue((Boolean) resp.get("success"));
        @SuppressWarnings("unchecked")
        List<?> groups = (List<?>) resp.get("groups");
        assertTrue(groups.isEmpty());
        assertEquals(0, resp.get("total"));
    }

    @Test
    @DisplayName("GET /api/groups with data → returns group list")
    void testListWithData() {
        TestCaseGroup g1 = new TestCaseGroup("Group A", "desc");
        TestCaseGroup g2 = new TestCaseGroup("Group B", null);
        when(mockRepository.findAllGroups()).thenReturn(List.of(g1, g2));

        Map<String, Object> resp = controller.listGroups();

        assertTrue((Boolean) resp.get("success"));
        @SuppressWarnings("unchecked")
        List<?> groups = (List<?>) resp.get("groups");
        assertEquals(2, groups.size());
        assertEquals(2, resp.get("total"));
    }

    // ============ GET /api/groups/{id} ============

    @Test
    @DisplayName("GET /api/groups/{id} with unknown id → NOT_FOUND")
    void testGetNotFound() {
        when(mockRepository.findGroupById("ghost")).thenReturn(Optional.empty());

        Map<String, Object> resp = controller.getGroup("ghost");

        assertFalse((Boolean) resp.get("success"));
        assertEquals("分组不存在", resp.get("error"));
    }

    @Test
    @DisplayName("GET /api/groups/{id} with valid id → success with test cases")
    void testGetSuccess() {
        TestCaseGroup group = new TestCaseGroup("My Group", "desc");
        TestCaseEntity tc = new TestCaseEntity("case-name", "in", "out");
        tc.setName("case-1");
        group.getTestCaseIds().add("tc-1");

        when(mockRepository.findGroupById("grp-1")).thenReturn(Optional.of(group));
        when(mockRepository.findTestCaseById("tc-1")).thenReturn(Optional.of(tc));

        Map<String, Object> resp = controller.getGroup("grp-1");

        assertTrue((Boolean) resp.get("success"));
        assertNotNull(resp.get("group"));
        @SuppressWarnings("unchecked")
        List<?> testCases = (List<?>) resp.get("testCases");
        assertEquals(1, testCases.size());
    }

    @Test
    @DisplayName("GET /api/groups/{id} with no test cases → empty test case list")
    void testGetNoTestCases() {
        TestCaseGroup group = new TestCaseGroup("Empty Group", null);
        when(mockRepository.findGroupById("grp-empty")).thenReturn(Optional.of(group));

        Map<String, Object> resp = controller.getGroup("grp-empty");

        assertTrue((Boolean) resp.get("success"));
        @SuppressWarnings("unchecked")
        List<?> testCases = (List<?>) resp.get("testCases");
        assertTrue(testCases.isEmpty());
    }

    // ============ PUT /api/groups/{id} ============

    @Test
    @DisplayName("PUT /api/groups/{id} with blank name → BAD_REQUEST")
    void testUpdateBlankName() {
        GroupRequest req = new GroupRequest();
        req.setName("   ");

        Map<String, Object> resp = controller.updateGroup("grp-1", req);

        assertFalse((Boolean) resp.get("success"));
        assertEquals("分组名称不能为空", resp.get("error"));
    }

    @Test
    @DisplayName("PUT /api/groups/{id} with name too long → BAD_REQUEST")
    void testUpdateNameTooLong() {
        GroupRequest req = new GroupRequest();
        req.setName("x".repeat(201));

        Map<String, Object> resp = controller.updateGroup("grp-1", req);

        assertFalse((Boolean) resp.get("success"));
    }

    @Test
    @DisplayName("PUT /api/groups/{id} with unknown id → NOT_FOUND")
    void testUpdateNotFound() {
        GroupRequest req = new GroupRequest();
        req.setName("Updated Name");

        when(mockRepository.findGroupById("ghost")).thenReturn(Optional.empty());

        Map<String, Object> resp = controller.updateGroup("ghost", req);

        assertFalse((Boolean) resp.get("success"));
        assertEquals("分组不存在", resp.get("error"));
    }

    @Test
    @DisplayName("PUT /api/groups/{id} with valid data → success")
    void testUpdateSuccess() {
        GroupRequest req = new GroupRequest();
        req.setName("Updated Group");
        req.setDescription("new desc");

        TestCaseGroup existing = new TestCaseGroup("Old Name", null);
        TestCaseGroup saved = new TestCaseGroup("Updated Group", "new desc");

        when(mockRepository.findGroupById("grp-1")).thenReturn(Optional.of(existing));
        when(mockRepository.saveGroup(any(TestCaseGroup.class))).thenReturn(saved);

        Map<String, Object> resp = controller.updateGroup("grp-1", req);

        assertTrue((Boolean) resp.get("success"));
        assertNotNull(resp.get("group"));
    }

    // ============ DELETE /api/groups/{id} ============

    @Test
    @DisplayName("DELETE /api/groups/{id} with unknown id → NOT_FOUND")
    void testDeleteNotFound() {
        when(mockRepository.findGroupById("ghost")).thenReturn(Optional.empty());

        Map<String, Object> resp = controller.deleteGroup("ghost");

        assertFalse((Boolean) resp.get("success"));
        assertEquals("分组不存在", resp.get("error"));
    }

    @Test
    @DisplayName("DELETE /api/groups/{id} with valid id → success")
    void testDeleteSuccess() {
        TestCaseGroup group = new TestCaseGroup("To Delete", null);
        when(mockRepository.findGroupById("grp-del")).thenReturn(Optional.of(group));

        Map<String, Object> resp = controller.deleteGroup("grp-del");

        assertTrue((Boolean) resp.get("success"));
        verify(mockRepository).deleteGroup("grp-del");
    }

    // ============ POST /api/groups/{id}/testcases ============

    @Test
    @DisplayName("POST /api/groups/{id}/testcases with null testCaseId → BAD_REQUEST")
    void testAddTestCaseNullId() {
        Map<String, Object> resp = controller.addTestCaseToGroup("grp-1", null);

        assertFalse((Boolean) resp.get("success"));
        assertTrue(((String) resp.get("error")).contains("testCaseId"));
    }

    @Test
    @DisplayName("POST /api/groups/{id}/testcases with blank testCaseId → BAD_REQUEST")
    void testAddTestCaseBlankId() {
        Map<String, Object> resp = controller.addTestCaseToGroup("grp-1", Map.of("testCaseId", "  "));

        assertFalse((Boolean) resp.get("success"));
    }

    @Test
    @DisplayName("POST /api/groups/{id}/testcases with unknown group → NOT_FOUND")
    void testAddTestCaseGroupNotFound() {
        Map<String, String> req = Map.of("testCaseId", "tc-1");
        when(mockRepository.findGroupById("ghost")).thenReturn(Optional.empty());

        Map<String, Object> resp = controller.addTestCaseToGroup("ghost", req);

        assertFalse((Boolean) resp.get("success"));
        assertEquals("分组不存在", resp.get("error"));
    }

    @Test
    @DisplayName("POST /api/groups/{id}/testcases with unknown testCase → NOT_FOUND")
    void testAddTestCaseNotFound() {
        Map<String, String> req = Map.of("testCaseId", "ghost");
        TestCaseGroup group = new TestCaseGroup("G", null);
        when(mockRepository.findGroupById("grp-1")).thenReturn(Optional.of(group));
        when(mockRepository.findTestCaseById("ghost")).thenReturn(Optional.empty());

        Map<String, Object> resp = controller.addTestCaseToGroup("grp-1", req);

        assertFalse((Boolean) resp.get("success"));
        assertEquals("测试用例不存在", resp.get("error"));
    }

    @Test
    @DisplayName("POST /api/groups/{id}/testcases with valid data → success")
    void testAddTestCaseSuccess() {
        Map<String, String> req = Map.of("testCaseId", "tc-1");
        TestCaseGroup group = new TestCaseGroup("G", null);
        TestCaseEntity tc = new TestCaseEntity("case-name", "in", "out");
        when(mockRepository.findGroupById("grp-1")).thenReturn(Optional.of(group));
        when(mockRepository.findTestCaseById("tc-1")).thenReturn(Optional.of(tc));
        when(mockRepository.addTestCaseToGroup("grp-1", "tc-1")).thenReturn(group);

        Map<String, Object> resp = controller.addTestCaseToGroup("grp-1", req);

        assertTrue((Boolean) resp.get("success"));
        assertNotNull(resp.get("group"));
        verify(mockRepository).addTestCaseToGroup("grp-1", "tc-1");
    }

    // ============ DELETE /api/groups/{id}/testcases/{testCaseId} ============

    @Test
    @DisplayName("DELETE /api/groups/{id}/testcases/{testCaseId} with unknown group → NOT_FOUND")
    void testRemoveTestCaseGroupNotFound() {
        when(mockRepository.findGroupById("ghost")).thenReturn(Optional.empty());

        Map<String, Object> resp = controller.removeTestCaseFromGroup("ghost", "tc-1");

        assertFalse((Boolean) resp.get("success"));
        assertEquals("分组不存在", resp.get("error"));
    }

    @Test
    @DisplayName("DELETE /api/groups/{id}/testcases/{testCaseId} with valid ids → success")
    void testRemoveTestCaseSuccess() {
        TestCaseGroup group = new TestCaseGroup("G", null);
        when(mockRepository.findGroupById("grp-1")).thenReturn(Optional.of(group));
        when(mockRepository.removeTestCaseFromGroup("grp-1", "tc-1")).thenReturn(group);

        Map<String, Object> resp = controller.removeTestCaseFromGroup("grp-1", "tc-1");

        assertTrue((Boolean) resp.get("success"));
        assertNotNull(resp.get("group"));
        verify(mockRepository).removeTestCaseFromGroup("grp-1", "tc-1");
    }
}
