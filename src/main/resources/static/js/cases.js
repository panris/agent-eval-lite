function addCaseTag() {
    const input = document.getElementById('case-tags-input');
    const tag = input.value.trim();
    if (tag && !currentCaseTags.includes(tag)) {
        currentCaseTags.push(tag);
        input.value = '';
        renderCaseTags();
    }
}

async function addSelectedToGroup() {
    const groupId = window.currentGroupId;
    const checkboxes = document.querySelectorAll('#modal-cases-tbody input:checked');
    const testCaseIds = Array.from(checkboxes).map(cb => cb.value);

    if (testCaseIds.length === 0) {
        showToast('请至少选择一个测试用例', 'error');
        return;
    }

    try {
        await fetch(`/api/groups/${groupId}/testcases`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ testCaseIds })
        });

        showToast(`成功添加 ${testCaseIds.length} 个用例到分组`, 'success');
        closeAddToGroupModal();
        loadGroups();
    } catch (error) {
        logError('Failed to add test cases to group:', error);
        showToast('添加用例到分组失败', 'error');
    }
}

async function applyBatchEdit() {
    const checked = document.querySelectorAll('#cases-tbody .case-checkbox:checked');
    const ids = Array.from(checked).map(cb => cb.value);

    if (ids.length === 0) {
        showToast('请先选择测试用例', 'info');
        return;
    }

    const updates = [];

    // 收集需要更新的字段
    if (document.getElementById('batch-edit-name-enabled')?.checked) {
        const name = document.getElementById('batch-edit-name')?.value;
        if (name) updates.push({ field: 'name', value: name });
    }
    if (document.getElementById('batch-edit-expected-enabled')?.checked) {
        const expected = document.getElementById('batch-edit-expected')?.value;
        if (expected) updates.push({ field: 'expected', value: expected });
    }
    if (document.getElementById('batch-edit-group-enabled')?.checked) {
        const groupId = document.getElementById('batch-edit-group')?.value;
        updates.push({ field: 'groupId', value: groupId || null });
    }
    if (document.getElementById('batch-edit-tags-enabled')?.checked) {
        const action = document.getElementById('batch-edit-tags-action')?.value;
        const tagsStr = document.getElementById('batch-edit-tags')?.value || '';
        const tags = tagsStr.split(',').map(t => t.trim()).filter(t => t);
        updates.push({ field: 'tags', value: { action, tags } });
    }
    if (document.getElementById('batch-edit-project-enabled')?.checked) {
        const project = document.getElementById('batch-edit-project')?.value.trim();
        updates.push({ field: 'project', value: project || null });
    }
    if (document.getElementById('batch-edit-module-enabled')?.checked) {
        const moduleDim = document.getElementById('batch-edit-module')?.value.trim();
        updates.push({ field: 'module', value: moduleDim || null });
    }
    if (document.getElementById('batch-edit-function-enabled')?.checked) {
        const functionDim = document.getElementById('batch-edit-function')?.value.trim();
        updates.push({ field: 'function', value: functionDim || null });
    }
    if (document.getElementById('batch-edit-description-enabled')?.checked) {
        const description = document.getElementById('batch-edit-description')?.value || '';
        updates.push({ field: 'description', value: description || null });
    }

    if (updates.length === 0) {
        showToast('请至少选择一项进行修改', 'info');
        return;
    }

    // 逐个更新用例
    let successCount = 0;
    for (const id of ids) {
        try {
            // 获取当前用例
            const tc = window._testCases?.find(t => t.id === id);
            if (!tc) continue;

            let updateData = { ...tc };

            for (const update of updates) {
                if (update.field === 'name') {
                    updateData.name = update.value;
                } else if (update.field === 'expected') {
                    updateData.expected = update.value;
                } else if (update.field === 'groupId') {
                    updateData.groupId = update.value;
                } else if (update.field === 'project') {
                    updateData.project = update.value;
                } else if (update.field === 'module') {
                    updateData.module = update.value;
                } else if (update.field === 'function') {
                    updateData.function = update.value;
                } else if (update.field === 'description') {
                    updateData.description = update.value;
                } else if (update.field === 'tags') {
                    const currentTags = tc.metadata?.tags || [];
                    if (update.value.action === 'add') {
                        updateData.metadata = updateData.metadata || {};
                        updateData.metadata.tags = [...new Set([...currentTags, ...update.value.tags])];
                    } else if (update.value.action === 'remove') {
                        updateData.metadata = updateData.metadata || {};
                        updateData.metadata.tags = currentTags.filter(t => !update.value.tags.includes(t));
                    } else if (update.value.action === 'replace') {
                        updateData.metadata = updateData.metadata || {};
                        updateData.metadata.tags = update.value.tags;
                    }
                }
            }

            await fetch(`/api/testcases/${id}`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(updateData)
            });
            successCount++;
        } catch (e) {
            logError(`Failed to update case ${id}:`, e);
        }
    }

    closeBatchEditModal();
    showToast(`成功更新 ${successCount} 个用例`, 'success');
    loadTestCases();
    loadDimensions();
}

async function batchDeleteCases() {
    const checked = document.querySelectorAll('#cases-tbody .case-checkbox:checked');
    const ids = Array.from(checked).map(cb => cb.value);

    if (ids.length === 0) {
        showToast('请先选择测试用例', 'info');
        return;
    }

    showConfirm('批量删除', `确定要删除选中的 ${ids.length} 个测试用例吗?`, async () => {
        let successCount = 0;
        for (const id of ids) {
            try {
                await fetch(`/api/testcases/${id}`, { method: 'DELETE' });
                successCount++;
            } catch (e) {
                logError(`Failed to delete case ${id}:`, e);
            }
        }
        showToast(`成功删除 ${successCount} 个用例`, 'success');
        loadTestCases();
    });
}

function closeAddToGroupModal() {
    document.getElementById('add-to-group-modal').style.display = 'none';
}

function closeBatchEditModal() {
    document.getElementById('batch-edit-modal').classList.remove('show');
    // 重置表单
    document.getElementById('batch-edit-name-enabled').checked = false;
    document.getElementById('batch-edit-expected-enabled').checked = false;
    document.getElementById('batch-edit-group-enabled').checked = false;
    document.getElementById('batch-edit-tags-enabled').checked = false;
    document.getElementById('batch-edit-project-enabled').checked = false;
    document.getElementById('batch-edit-module-enabled').checked = false;
    document.getElementById('batch-edit-function-enabled').checked = false;
    toggleBatchEditField('name');
    toggleBatchEditField('expected');
    toggleBatchEditField('group');
    toggleBatchEditField('tags');
    toggleBatchEditField('project');
    toggleBatchEditField('module');
    toggleBatchEditField('function');
}

function closeCaseTagsModal() {
    document.getElementById('case-tags-modal').style.display = 'none';
}

function closeImportExcelModal() {
    document.getElementById('import-excel-modal').style.display = 'none';
}

function closeImportModal() {
    document.getElementById('import-modal').style.display = 'none';
    document.getElementById('import-data').value = '';
}

async function createGroup() {
    const name = document.getElementById('group-name').value;
    const description = document.getElementById('group-desc').value;

    if (!name) {
        showToast('请填写分组名称', 'error');
        return;
    }

    try {
        await fetch('/api/groups', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ name, description })
        });
        showToast('分组已创建', 'success');
        loadGroups();
        document.getElementById('group-name').value = '';
        document.getElementById('group-desc').value = '';
    } catch (error) {
        logError('Failed to create group:', error);
        showToast('创建失败', 'error');
    }
}

async function createTestCase() {
    const name = document.getElementById('case-name').value;
    const input = document.getElementById('case-input').value;
    const expected = document.getElementById('case-expected').value;
    const description = document.getElementById('case-description').value;
    const project = document.getElementById('case-project').value.trim();
    const moduleDim = document.getElementById('case-module').value.trim();
    const functionDim = document.getElementById('case-function').value.trim();

    if (!input || !expected) {
        showToast('请填写输入和期望输出', 'error');
        return;
    }

    try {
        await fetch('/api/testcases', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ name, input, expected, description, project, module: moduleDim, function: functionDim })
        });
        showToast('测试用例已添加', 'success');
        loadTestCases();
        loadDimensions();
        document.getElementById('case-name').value = '';
        document.getElementById('case-input').value = '';
        document.getElementById('case-expected').value = '';
        document.getElementById('case-description').value = '';
        document.getElementById('case-expected').value = '';
        document.getElementById('case-project').value = '';
        document.getElementById('case-module').value = '';
        document.getElementById('case-function').value = '';
    } catch (error) {
        logError('Failed to create test case:', error);
        showToast('添加失败', 'error');
    }
}

async function deleteGroup(id) {
    showConfirm('删除分组', '确定要删除该分组吗?此操作不可恢复。', async () => {
        try {
            await fetch(`/api/groups/${id}`, { method: 'DELETE' });
            showToast('分组已删除', 'success');
            loadGroups();
        } catch (error) {
            logError('Failed to delete group:', error);
            showToast('删除失败', 'error');
        }
    });
}

async function deleteTestCase(id) {
    showConfirm('删除测试用例', '确定要删除该测试用例吗?此操作不可恢复。', async () => {
        try {
            await fetch(`/api/testcases/${id}`, { method: 'DELETE' });
            showToast('测试用例已删除', 'success');
            loadTestCases();
        } catch (error) {
            logError('Failed to delete test case:', error);
            showToast('删除失败', 'error');
        }
    });
}

function deselectAllCases() {
    document.querySelectorAll('#cases-tbody .case-checkbox').forEach(cb => cb.checked = false);
    document.getElementById('select-all-cases').checked = false;
    updateSelectedCaseCount();
}

async function ensureGroups() {
    if (groups.length > 0) return;
    try {
        const response = await fetch('/api/groups');
        const data = await response.json();
        groups = data.groups || [];
        // 更新评测分组选择器
        const evalSelect = document.getElementById('eval-group');
        if (evalSelect) {
            evalSelect.innerHTML = '<option value="">选择分组...</option>' +
                groups.map(g => `<option value="${utils.escapeHtml(g.id)}">${utils.escapeHtml(g.name)}</option>`).join('');
        }
        // 更新历史记录分组下拉框
        const histSelect = document.getElementById('history-group');
        if (histSelect) {
            histSelect.innerHTML = '<option value="">全部分组</option>' +
                groups.map(g => `<option value="${utils.escapeHtml(g.name)}">${utils.escapeHtml(g.name)}</option>`).join('');
        }
    } catch (e) {
        logError('Failed to load groups for filter:', e);
    }
}

function exportAllTestCases(format) {
    const testCases = window._testCases || [];
    if (testCases.length === 0) {
        showToast('没有可导出的测试用例', 'info');
        return;
    }

    let content, filename, mimeType;

    if (format === 'json') {
        content = JSON.stringify(testCases, null, 2);
        filename = `testcases_${Date.now()}.json`;
        mimeType = 'application/json';
    } else {
        // CSV 格式
        const headers = ['name', 'input', 'expected', 'groupId', 'project', 'module', 'function', 'description'];
        const rows = [headers.join(',')];
        testCases.forEach(tc => {
            rows.push([
                `"${(tc.name || '').replace(/"/g, '""')}"`,
                `"${(tc.input || '').replace(/"/g, '""')}"`,
                `"${(tc.expected || '').replace(/"/g, '""')}"`,
                `"${tc.groupId || ''}"`,
                `"${tc.project || ''}"`,
                `"${tc.module || ''}"`,
                `"${tc.function || ''}"`,
                `"${(tc.description || '').replace(/"/g, '""')}"`
            ].join(','));
        });
        content = rows.join('\n');
        filename = `testcases_${Date.now()}.csv`;
        mimeType = 'text/csv';
    }

    const blob = new Blob([content], { type: mimeType });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    a.click();
    URL.revokeObjectURL(url);
    showToast(`已导出 ${testCases.length} 条测试用例`, 'success');
}

async function exportTestCasesExcel() {
    try {
        showToast('正在导出 Excel...', 'info');
        const response = await fetch('/api/testcases/export/excel');
        if (!response.ok) throw new Error('导出失败');

        const blob = await response.blob();
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = response.headers.get('Content-Disposition')?.match(/filename="(.+)"/)?.[1] || 'testcases.xlsx';
        a.click();
        URL.revokeObjectURL(url);
        showToast('Excel 导出成功', 'success');
    } catch (error) {
        logError('Excel export failed:', error);
        showToast('Excel 导出失败', 'error');
    }
}

async function importTestCasesExcel() {
    const fileInput = document.getElementById('excel-file-input');
    const file = fileInput.files[0];

    if (!file) {
        showToast('请选择文件', 'error');
        return;
    }

    const isCsv = file.name.toLowerCase().endsWith('.csv');
    const endpoint = isCsv ? '/api/testcases/import/csv' : '/api/testcases/import/excel';

    const formData = new FormData();
    formData.append('file', file);

    try {
        showToast('正在导入...', 'info');
        const response = await fetch(endpoint, {
            method: 'POST',
            body: formData
        });

        const result = await response.json();

        if (result.success) {
            showToast(`成功导入 ${result.imported} 条测试用例`, 'success');
            closeImportExcelModal();
            loadTestCases();
        } else {
            showToast(result.message || '导入失败', 'error');
        }
    } catch (error) {
        logError('Import failed:', error);
        showToast(isCsv ? 'CSV 导入失败' : 'Excel 导入失败', 'error');
    }
}

async function importTestCases() {
    const data = document.getElementById('import-data').value.trim();
    if (!data) {
        showToast('请输入导入数据', 'error');
        return;
    }

    let testCases = [];
    let format = '';

    // 检测格式并解析
    try {
        if (data.startsWith('[') || data.startsWith('{')) {
            // JSON 格式
            format = 'json';
            const parsed = JSON.parse(data);
            testCases = Array.isArray(parsed) ? parsed : [parsed];
        } else {
            // CSV 格式
            format = 'csv';
            const lines = data.split('\n').filter(l => l.trim());
            if (lines.length < 2) {
                showToast('CSV 格式错误:需要表头和数据行', 'error');
                return;
            }

            const headers = lines[0].split(',').map(h => h.trim().toLowerCase());
            const nameIdx = headers.indexOf('name');
            const inputIdx = headers.indexOf('input');
            const expectedIdx = headers.indexOf('expected');

            if (inputIdx === -1 || expectedIdx === -1) {
                showToast('CSV 格式错误:需要 input 和 expected 列', 'error');
                return;
            }

            for (let i = 1; i < lines.length; i++) {
                const values = lines[i].split(',').map(v => v.trim());
                testCases.push({
                    name: nameIdx !== -1 ? values[nameIdx] : `用例${i}`,
                    input: values[inputIdx],
                    expected: values[expectedIdx]
                });
            }
        }

        // 过滤无效数据
        testCases = testCases.filter(tc => tc.input && tc.expected);

        if (testCases.length === 0) {
            showToast('没有找到有效的测试用例数据', 'error');
            return;
        }

        // 创建测试用例
        let successCount = 0;
        for (const tc of testCases) {
            await fetch('/api/testcases', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(tc)
            });
            successCount++;
        }

        showToast(`成功导入 ${successCount} 条测试用例`, 'success');
        closeImportModal();
        loadTestCases();

    } catch (e) {
        showToast(`解析失败: ${e.message}`, 'error');
    }
}

async function loadGroups() {
    document.getElementById('groups-loading').classList.add('show');
    try {
        const response = await fetch('/api/groups');
        const data = await response.json();
        groups = data.groups || [];

        const tbody = document.getElementById('groups-tbody');
        if (groups.length === 0) {
            tbody.innerHTML = '<tr><td colspan="4" style="text-align: center; color: var(--text-secondary);">暂无分组</td></tr>';
        } else {
            tbody.innerHTML = groups.map(g => `
                <tr>
                    <td>${utils.escapeHtml(g.name)}</td>
                    <td>${utils.escapeHtml(g.description) || '-'}</td>
                    <td>${g.testCaseIds ? g.testCaseIds.length : 0}</td>
                    <td class="actions">
                        <button class="btn btn-primary btn-sm" onclick="viewGroupCases('${utils.escapeHtml(g.id)}')">查看用例</button>
                        <button class="btn btn-success btn-sm" onclick="showAddToGroupModal('${utils.escapeHtml(g.id)}')">批量添加</button>
                        <button class="btn btn-danger btn-sm" onclick="deleteGroup('${utils.escapeHtml(g.id)}')">删除</button>
                    </td>
                </tr>
            `).join('');
        }

        // 更新评测分组选择器
        const evalSelect = document.getElementById('eval-group');
        if (evalSelect) {
            evalSelect.innerHTML = '<option value="">选择分组...</option>' +
                groups.map(g => `<option value="${utils.escapeHtml(g.id)}">${utils.escapeHtml(g.name)}</option>`).join('');
        }
        // 同时更新历史记录分组下拉框
        const histSelect = document.getElementById('history-group');
        if (histSelect) {
            histSelect.innerHTML = '<option value="">全部分组</option>' +
                groups.map(g => `<option value="${utils.escapeHtml(g.name)}">${utils.escapeHtml(g.name)}</option>`).join('');
        }
    } catch (error) {
        logError('Failed to load groups:', error);
    } finally {
        document.getElementById('groups-loading').classList.remove('show');
    }
}

async function loadGroupsForBatchEdit() {
    try {
        const response = await fetch('/api/groups');
        const data = await response.json();
        const groups = data.groups || [];

        const select = document.getElementById('batch-edit-group');
        select.innerHTML = '<option value="">移除分组</option>';
        groups.forEach(g => {
            select.innerHTML += `<option value="${utils.escapeHtml(g.id)}">${utils.escapeHtml(g.name)}</option>`;
        });
    } catch (e) {
        logError('Failed to load groups:', e);
    }
}

async function loadTestCases() {
    document.getElementById('cases-loading').classList.add('show');
    try {
        const params = new URLSearchParams({ page: tcPage, size: tcPageSize });
        if (tcKeyword) params.set('keyword', tcKeyword);
        if (tcGroupId) params.set('groupId', tcGroupId);
        const response = await fetch('/api/testcases?' + params.toString());
        const data = await response.json();
        const list = data.testCases || [];
        tcTotal = data.total || 0;
        tcTotalPages = data.totalPages || 1;
        if (tcPage > tcTotalPages) tcPage = tcTotalPages || 1;
        testCases = list;
        window._testCases = testCases;  // 保存用于导出

        const tbody = document.getElementById('cases-tbody');
        tbody.innerHTML = testCases.map(tc => {
            const _id = utils.escapeHtml(tc.id);
            const _name = utils.escapeHtml(tc.name);
            const _input = utils.escapeHtml(tc.input);
            const _expected = utils.escapeHtml(tc.expected);
            const _tags = (tc.metadata?.tags || []).map(t => utils.escapeHtml(t));
            const _desc = utils.escapeHtml(tc.description || '');
            const _descShort = _desc.length > 30 ? _desc.substring(0, 30) + '…' : _desc;
            return `
            <tr>
                <td class="checkbox-cell"><input type="checkbox" class="case-checkbox" value="${_id}"></td>
                <td>${_name}</td>
                <td>${_input}</td>
                <td>${_expected}</td>
                <td><span class="badge badge-info">${tc.groupId || '未分组'}</span></td>
                <td>
                    <div style="max-width: 150px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 12px; color: var(--text-secondary);"
                        title="${_desc}">${_desc ? _descShort : '<span style="color: var(--text-secondary);">—</span>'}</div>
                </td>
                <td>
                    <div style="display: flex; gap: 4px; flex-wrap: wrap;">
                        ${tc.project ? `<span class="badge" style="background: #2563eb;">${utils.escapeHtml(tc.project)}</span>` : ''}
                        ${tc.module ? `<span class="badge" style="background: #7c3aed;">${utils.escapeHtml(tc.module)}</span>` : ''}
                        ${tc.function ? `<span class="badge" style="background: #db2777;">${utils.escapeHtml(tc.function)}</span>` : ''}
                        ${(!tc.project && !tc.module && !tc.function) ? '<span style="color: var(--text-secondary); font-size: 12px;">-</span>' : ''}
                    </div>
                </td>
                <td>
                    <div style="display: flex; gap: 4px; flex-wrap: wrap; align-items: center;">
                        ${_tags.map(tag => `<span class="badge" style="background: #667eea; padding: 2px 8px; font-size: 11px;">${tag}</span>`).join('')}
                        <button class="btn btn-sm" style="padding: 2px 6px; font-size: 11px;" onclick="openCaseTagsModal('${_id}', ${JSON.stringify(tc.metadata?.tags || []).replace(/"/g, '&quot;')})">+标签</button>
                    </div>
                </td>
                <td class="actions">
                    <button class="btn btn-danger btn-sm" onclick="deleteTestCase('${_id}')">删除</button>
                </td>
            </tr>
            `;
        }).join('');

        // 更新评测表格
        const evalTbody = document.getElementById('eval-tbody');
        evalTbody.innerHTML = testCases.map(tc => {
            const _name = utils.escapeHtml(tc.name);
            const _input = utils.escapeHtml(tc.input);
            const _expected = utils.escapeHtml(tc.expected);
            return `
            <tr>
                <td class="checkbox-cell"><input type="checkbox" class="eval-checkbox" value="${utils.escapeHtml(tc.id)}"></td>
                <td>${_name}</td>
                <td>${_input}</td>
                <td>${_expected}</td>
            </tr>
            `;
        }).join('');

        renderTcPagination();
    } catch (error) {
        logError('Failed to load test cases:', error);
        showToast('加载测试用例失败', 'error');
    } finally {
        document.getElementById('cases-loading').classList.remove('show');
    }
}

async function loadTestCasesForModal() {
    try {
        const response = await fetch('/api/testcases');
        const allTestCases = await response.json();

        const tbody = document.getElementById('modal-cases-tbody');
        tbody.innerHTML = allTestCases.map(tc => `
            <tr>
                <td class="checkbox-cell"><input type="checkbox" value="${utils.escapeHtml(tc.id)}"></td>
                <td>${utils.escapeHtml(tc.name)}</td>
                <td>${utils.escapeHtml(tc.input)}</td>
                <td>${utils.escapeHtml(tc.expected)}</td>
            </tr>
        `).join('');
    } catch (error) {
        logError('Failed to load test cases for modal:', error);
    }
}

function openBatchEditModal() {
    const checked = document.querySelectorAll('#cases-tbody .case-checkbox:checked');
    if (checked.length === 0) {
        showToast('请先选择测试用例', 'info');
        return;
    }

    document.getElementById('batch-edit-count').textContent = checked.length;
    document.getElementById('batch-edit-modal').classList.add('show');

    // 加载分组列表
    loadGroupsForBatchEdit();
}

function openCaseTagsModal(caseId, existingTags) {
    currentCaseId = caseId;
    currentCaseTags = [...(existingTags || [])];
    document.getElementById('case-tags-report-id').value = caseId;
    renderCaseTags();
    document.getElementById('case-tags-modal').style.display = 'flex';
    document.getElementById('case-tags-input').focus();
}

function openImportExcelModal() {
    document.getElementById('import-excel-modal').style.display = 'block';
    document.getElementById('excel-file-input').value = '';
}

function openImportModal() {
    document.getElementById('import-modal').style.display = 'block';
}

function removeCaseTag(index) {
    currentCaseTags.splice(index, 1);
    renderCaseTags();
}

function renderCaseTags() {
    const container = document.getElementById('case-tags-list');
    container.innerHTML = currentCaseTags.map((tag, i) => `
        <span class="badge" style="background: #667eea; padding: 5px 10px; margin: 3px; display: inline-flex; align-items: center; gap: 5px;">
            ${utils.escapeHtml(tag)}
            <span style="cursor: pointer; font-weight: bold;" onclick="removeCaseTag(${i})">×</span>
        </span>
    `).join('') || '<span style="color: var(--text-secondary);">暂无标签</span>';
}

function renderTcPagination() {
    const info = document.getElementById('cases-pagination-info');
    const prevBtn = document.getElementById('tc-prev-btn');
    const nextBtn = document.getElementById('tc-next-btn');
    const pageNumbers = document.getElementById('tc-page-numbers');

    info.textContent = tcTotal === 0
        ? '暂无数据'
        : `共 ${tcTotal} 条,第 ${tcPage}/${tcTotalPages} 页`;

    prevBtn.disabled = tcPage <= 1;
    nextBtn.disabled = tcPage >= tcTotalPages;

    // 页码按钮(最多显示7个,两端省略)
    const maxVisible = 7;
    let pages = [];
    if (tcTotalPages <= maxVisible) {
        for (let i = 1; i <= tcTotalPages; i++) pages.push(i);
    } else {
        pages.push(1);
        const start = Math.max(2, tcPage - 2);
        const end = Math.min(tcTotalPages - 1, tcPage + 2);
        if (start > 2) pages.push('...');
        for (let i = start; i <= end; i++) pages.push(i);
        if (end < tcTotalPages - 1) pages.push('...');
        pages.push(tcTotalPages);
    }

    pageNumbers.innerHTML = pages.map(p => {
        if (p === '...') {
            return `<span style="padding: 4px 8px; color: var(--text-secondary);">...</span>`;
        }
        const active = p === tcPage ? 'background: var(--accent); color: #fff; border-color: var(--accent);' : '';
        return `<button class="btn btn-secondary btn-sm" style="padding: 4px 8px; font-size: 13px; ${active}" onclick="tcGoToPage(${p})">${p}</button>`;
    }).join('');
}

async function saveCaseTags() {
    try {
        const response = await fetch(`/api/testcases/${currentCaseId}/tags`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ tags: currentCaseTags })
        });
        const data = await response.json();
        if (data.success) {
            showToast('标签已保存', 'success');
            closeCaseTagsModal();
            loadTestCases();
        }
    } catch (e) {
        showToast('保存失败', 'error');
    }
}

function selectAllCases() {
    document.querySelectorAll('#cases-tbody .case-checkbox').forEach(cb => cb.checked = true);
    updateSelectedCaseCount();
}

function showAddToGroupModal(groupId) {
    window.currentGroupId = groupId;
    loadTestCasesForModal();
    document.getElementById('add-to-group-modal').style.display = 'block';
}

function tcGoToPage(p) {
    tcPageSize = parseInt(document.getElementById('cases-page-size').value, 10);
    tcPage = p;
    loadTestCases();
}

function tcNextPage() {
    if (tcPage < tcTotalPages) { tcPage++; loadTestCases(); }
}

function tcPrevPage() {
    if (tcPage > 1) { tcPage--; loadTestCases(); }
}

function toggleBatchEditField(field) {
    const checkbox = document.getElementById(`batch-edit-${field}-enabled`);
    const input = document.getElementById(`batch-edit-${field}`);
    if (checkbox && input) {
        input.disabled = !checkbox.checked;
    }
}

function toggleBatchTagsAction() {
    const action = document.getElementById('batch-edit-tags-action')?.value;
    const input = document.getElementById('batch-edit-tags');
    if (input) {
        input.disabled = document.getElementById('batch-edit-tags-enabled')?.checked !== true;
    }
}

function toggleModalSelectAll() {
    const checked = document.getElementById('modal-select-all').checked;
    document.querySelectorAll('#modal-cases-tbody input[type="checkbox"]').forEach(cb => {
        cb.checked = checked;
    });
}

function updateSelectedCaseCount() {
    const checked = document.querySelectorAll('#cases-tbody .case-checkbox:checked').length;
    document.getElementById('selected-case-count').textContent = checked;
}

async function viewGroupCases(groupId) {
    try {
        const response = await fetch(`/api/groups/${groupId}`);
        const group = await response.json();

        // 加载所有测试用例
        const tcResponse = await fetch('/api/testcases');
        const tcData = await tcResponse.json();
        const allTestCases = tcData.testCases || [];

        const testCaseList = group.testCaseIds.map(id => {
            const tc = allTestCases.find(t => t.id === id);
            return tc ? `- ${tc.name}: ${tc.input} → ${tc.expected}` : `- ${id} (未找到)`;
        }).join('\n');

        showConfirm(`分组: ${group.name}`, `用例列表:\n${testCaseList || '无'}`, () => {});

    } catch (error) {
        logError('Failed to load group cases:', error);
        showToast('加载分组用例失败', 'error');
    }
}

// ===== 从需求文档生成测试用例 =====
let parsedCases = [];

function openRequirementModal() {
    parsedCases = [];
    document.getElementById('req-text').value = '';
    document.getElementById('req-preview').style.display = 'none';
    document.getElementById('req-preview-tbody').innerHTML = '';
    document.getElementById('req-count').textContent = '';
    document.getElementById('req-save-btn').disabled = true;
    document.getElementById('req-parse-btn').disabled = false;
    document.getElementById('req-loading').style.display = 'none';
    // 重置维度默认值
    document.getElementById('req-project').value = '';
    document.getElementById('req-module').value = '';
    document.getElementById('req-function').value = '';
    document.getElementById('req-group').value = '';
    document.getElementById('requirement-modal').style.display = 'flex';
    setTimeout(() => document.getElementById('req-text').focus(), 0);
}

function closeRequirementModal() {
    document.getElementById('requirement-modal').style.display = 'none';
}

async function parseRequirement() {
    const text = document.getElementById('req-text').value.trim();
    if (!text) {
        showToast('请粘贴需求文档内容', 'error');
        return;
    }

    const project = document.getElementById('req-project').value.trim() || null;
    const moduleDim = document.getElementById('req-module').value.trim() || null;
    const functionDim = document.getElementById('req-function').value.trim() || null;
    const group = document.getElementById('req-group').value.trim() || null;

    document.getElementById('req-loading').style.display = 'block';
    document.getElementById('req-parse-btn').disabled = true;
    document.getElementById('req-save-btn').disabled = true;

    try {
        const response = await fetch('/api/testcases/parse-from-requirements', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                text,
                groupId: group,
                project,
                module: moduleDim,
                function: functionDim
            })
        });
        const data = await response.json();

        if (!data.success) {
            showToast(data.error || '解析失败', 'error');
            return;
        }

        parsedCases = (data.cases || []).map((c, i) => ({
            ...c,
            selected: true,
            _index: i
        }));

        renderParsedPreview();
        document.getElementById('req-count').textContent = `已解析 ${parsedCases.length} 个测试用例`;
        document.getElementById('req-save-btn').disabled = parsedCases.length === 0;
        showToast(`成功解析 ${parsedCases.length} 个测试用例`, 'success');
    } catch (error) {
        logError('Parse failed:', error);
        showToast('解析失败：请求错误', 'error');
    } finally {
        document.getElementById('req-loading').style.display = 'none';
        document.getElementById('req-parse-btn').disabled = false;
    }
}

function renderParsedPreview() {
    const tbody = document.getElementById('req-preview-tbody');
    const preview = document.getElementById('req-preview');

    if (parsedCases.length === 0) {
        tbody.innerHTML = '';
        preview.style.display = 'none';
        return;
    }

    tbody.innerHTML = parsedCases.map((tc, i) => {
        const _name = utils.escapeHtml(tc.name || '');
        const _input = utils.escapeHtml(tc.input || '');
        const _expected = utils.escapeHtml(tc.expected || '');
        const _desc = utils.escapeHtml(tc.description || '');
        const checked = tc.selected ? 'checked' : '';
        return `
            <tr>
                <td class="checkbox-cell"><input type="checkbox" class="req-case-cb" data-index="${i}" ${checked} onchange="toggleReqCase(${i})"></td>
                <td><input type="text" class="form-control" value="${_name}" onchange="updateParsedCase(${i}, 'name', this.value)" style="font-size: 13px; padding: 4px 8px; min-width: 80px;"></td>
                <td><input type="text" class="form-control" value="${_input}" onchange="updateParsedCase(${i}, 'input', this.value)" style="font-size: 13px; padding: 4px 8px; min-width: 120px;"></td>
                <td><input type="text" class="form-control" value="${_expected}" onchange="updateParsedCase(${i}, 'expected', this.value)" style="font-size: 13px; padding: 4px 8px; min-width: 120px;"></td>
                <td><input type="text" class="form-control" value="${_desc}" onchange="updateParsedCase(${i}, 'description', this.value)" style="font-size: 13px; padding: 4px 8px; min-width: 100px;"></td>
            </tr>
        `;
    }).join('');

    preview.style.display = 'block';
    updateReqSelectAll();
}

function toggleReqCase(index) {
    const cb = document.querySelector(`.req-case-cb[data-index="${index}"]`);
    if (cb) {
        parsedCases[index].selected = cb.checked;
    }
    updateReqSelectAll();
    updateReqSaveBtn();
}

function updateParsedCase(index, field, value) {
    if (parsedCases[index]) {
        parsedCases[index][field] = value;
    }
}

function toggleReqSelectAll() {
    const checked = document.getElementById('req-select-all').checked;
    parsedCases.forEach((tc, i) => {
        tc.selected = checked;
        const cb = document.querySelector(`.req-case-cb[data-index="${i}"]`);
        if (cb) cb.checked = checked;
    });
    updateReqSaveBtn();
}

function updateReqSelectAll() {
    const allCbs = document.querySelectorAll('.req-case-cb');
    const checkedCbs = document.querySelectorAll('.req-case-cb:checked');
    const selectAll = document.getElementById('req-select-all');
    if (selectAll) {
        selectAll.checked = allCbs.length > 0 && allCbs.length === checkedCbs.length;
    }
}

function updateReqSaveBtn() {
    const selected = parsedCases.filter(tc => tc.selected);
    document.getElementById('req-save-btn').disabled = selected.length === 0;
}

async function saveParsedCases() {
    const selected = parsedCases
        .filter(tc => tc.selected)
        .map(tc => ({
            name: tc.name || '',
            input: tc.input || '',
            expected: tc.expected || '',
            description: tc.description || '',
            groupId: tc.groupId || null,
            project: tc.project || null,
            module: tc.module || null,
            function: tc.function || null
        }))
        .filter(tc => tc.input && tc.expected);

    if (selected.length === 0) {
        showToast('请至少选择一个包含输入和期望输出的测试用例', 'error');
        return;
    }

    document.getElementById('req-save-btn').disabled = true;

    try {
        const response = await fetch('/api/testcases/save-parsed', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(selected)
        });
        const data = await response.json();

        if (data.success) {
            showToast(`成功保存 ${data.saved} 个测试用例`, 'success');
            closeRequirementModal();
            loadTestCases();
            if (typeof loadDimensions === 'function') loadDimensions();
        } else {
            showToast(data.error || '保存失败', 'error');
        }
    } catch (error) {
        logError('Save failed:', error);
        showToast('保存失败：请求错误', 'error');
    } finally {
        document.getElementById('req-save-btn').disabled = false;
    }
}
