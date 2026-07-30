/**
 * Agent 管理页 JS — 表格列表版
 */

let allAgents = [];
let filteredAgents = [];
let currentSearch = '';
let currentFilter = 'all';
let currentTestAgentId = null;
let searchTimer = null;

// ============ 生命周期 ============
window.onload = async function () {
    await loadAgents();
    document.addEventListener('keydown', onKeydown);
};

function onKeydown(e) {
    if (e.key === 'Escape') {
        closeModal(); closeTestModal(); closeTemplateModal();
        return;
    }
    const tag = (e.target.tagName || '').toLowerCase();
    if (tag === 'input' || tag === 'textarea' || e.target.isContentEditable) return;
    if (e.key === 'n') { e.preventDefault(); openCreateModal(); }
    else if (e.key === '/') { e.preventDefault(); document.getElementById('agent-search')?.focus(); }
}

// ============ 加载 ============
async function loadAgents() {
    try {
        const res = await utils.api.get('/api/agents');
        if (res.success) {
            allAgents = res.agents || [];
            updateCountBadge();
            renderFilterChips();
            applyFilters();
        } else {
            renderError('加载失败');
        }
    } catch (e) {
        utils.logError('loadAgents', e);
        renderError('加载失败');
    }
}

function updateCountBadge() {
    const el = document.getElementById('agent-count-badge');
    if (el) {
        el.textContent = allAgents.length;
        el.style.display = allAgents.length > 0 ? '' : 'none';
    }
}

function renderError(msg) {
    document.getElementById('agents-list').innerHTML = `
        <div class="empty-state">
            <div class="empty-icon">⚠️</div><div>${utils.escapeHtml(msg)}</div>
            <button class="btn btn-secondary" onclick="loadAgents()">重试</button>
        </div>`;
}

// ============ 搜索 ============
function handleSearch(value) {
    clearTimeout(searchTimer);
    currentSearch = value;
    searchTimer = setTimeout(applyFilters, 150);
}

function clearSearch() {
    const input = document.getElementById('agent-search');
    if (input) input.value = '';
    currentSearch = '';
    applyFilters();
}

function applyFilters() {
    const kw = currentSearch.toLowerCase().trim();
    filteredAgents = allAgents.filter(a => {
        const matchKw = !kw ||
            a.name?.toLowerCase().includes(kw) ||
            a.type?.toLowerCase().includes(kw) ||
            a.endpoint?.toLowerCase().includes(kw) ||
            a.description?.toLowerCase().includes(kw);
        const matchType = currentFilter === 'all' || a.type === currentFilter;
        return matchKw && matchType;
    });
    filteredAgents.sort((a, b) => (a.name || '').localeCompare(b.name || '', 'zh'));
    renderList();
}

// ============ 类型筛选 ============
function renderFilterChips() {
    const el = document.getElementById('filter-chips');
    if (!el) return;
    const types = [...new Set(allAgents.map(a => a.type).filter(Boolean))];
    if (types.length <= 1) { el.innerHTML = ''; return; }
    const countByType = allAgents.reduce((acc, a) => { acc[a.type] = (acc[a.type] || 0) + 1; return acc; }, {});
    const chips = [{ key: 'all', label: '全部', count: allAgents.length },
        ...types.map(t => ({ key: t, label: TYPE_META[t]?.label || t, count: countByType[t] || 0 }))];
    el.innerHTML = chips.map(c => `
        <button class="chip ${currentFilter === c.key ? 'active' : ''}" onclick="setFilter('${c.key}')">
            ${c.label} <span class="chip-count">${c.count}</span>
        </button>`).join('');
}

function setFilter(type) {
    currentFilter = type;
    renderFilterChips();
    applyFilters();
}

// ============ 渲染表格 ============
const TYPE_META = {
    openai: { label: 'OpenAI', cls: 'type-openai', icon: '🟢' },
    claude: { label: 'Claude', cls: 'type-claude', icon: '🟠' },
    http:   { label: 'HTTP',   cls: 'type-http',   icon: '🔵' },
    custom: { label: '自定义', cls: 'type-custom', icon: '⚪' },
    intent: { label: '意图',   cls: 'type-intent', icon: '🚗' },
};

function renderList() {
    const el = document.getElementById('agents-list');

    if (filteredAgents.length === 0) {
        if (allAgents.length === 0) {
            el.innerHTML = `
                <div class="empty-state">
                    <div class="empty-icon">🤖</div>
                    <div class="empty-title">暂无 Agent</div>
                    <div class="empty-desc">点击「+ 新建」或从模板创建</div>
                    <div class="empty-actions">
                        <button class="btn btn-primary" onclick="openCreateModal()">+ 新建</button>
                        <button class="btn btn-outline" onclick="openTemplateModal()">📦 模板</button>
                    </div>
                </div>`;
        } else {
            el.innerHTML = `
                <div class="empty-state">
                    <div class="empty-icon">🔍</div>
                    <div class="empty-title">无匹配结果</div>
                    <button class="btn btn-secondary" onclick="clearSearch()">清除搜索</button>
                </div>`;
        }
        return;
    }

    el.innerHTML = `
        <table class="agents-table">
            <thead>
                <tr>
                    <th style="width:180px;">名称</th>
                    <th style="width:80px;">类型</th>
                    <th>接口地址</th>
                    <th style="width:180px;">描述</th>
                    <th style="width:200px;">操作</th>
                </tr>
            </thead>
            <tbody>
                ${filteredAgents.map(renderRow).join('')}
            </tbody>
        </table>`;
}

function renderRow(a) {
    const meta = TYPE_META[a.type] || { label: a.type || '-', cls: 'type-custom', icon: '⚪' };
    const endpoint = utils.escapeHtml(a.endpoint || '');
    const desc = utils.escapeHtml(a.description || '');
    return `
        <tr data-id="${utils.escapeHtml(a.id)}">
            <td><strong>${utils.escapeHtml(a.name)}</strong></td>
            <td><span class="agent-type-badge ${meta.cls}">${meta.icon} ${meta.label}</span></td>
            <td class="cell-endpoint" title="${endpoint}">${endpoint}</td>
            <td class="cell-desc" title="${desc}">${desc || '-'}</td>
            <td class="cell-actions">
                <button class="btn btn-sm btn-ghost" onclick="editAgent('${a.id}')">编辑</button>
                <button class="btn btn-sm btn-ghost" onclick="testAgentModal('${a.id}')">测试</button>
                <button class="btn btn-sm btn-ghost" onclick="copyAgent('${a.id}')">复制</button>
                <button class="btn btn-sm btn-danger-ghost" onclick="deleteAgent('${a.id}')">删除</button>
            </td>
        </tr>`;
}

// ============ 模板弹窗 ============
async function openTemplateModal() {
    const modal = document.getElementById('template-modal');
    modal.style.display = 'flex';
    const list = document.getElementById('template-list');
    list.innerHTML = '<div class="list-loading"><div class="spinner"></div></div>';

    try {
        const res = await utils.api.get('/api/agents/templates');
        if (res.success && res.templates?.length) {
            list.innerHTML = res.templates.map(t => `
                <div class="template-item" onclick="selectTemplate('${t.type}')">
                    <div class="template-item-name">${TYPE_META[t.type]?.icon || '⚪'} ${utils.escapeHtml(t.name)}</div>
                    <div class="template-item-desc">${utils.escapeHtml(t.description || '')}</div>
                </div>`).join('');
        } else {
            list.innerHTML = '<div class="empty-state"><div class="empty-icon">📦</div><div>暂无模板</div></div>';
        }
    } catch {
        list.innerHTML = '<div class="empty-state"><div class="empty-icon">⚠️</div><div>加载失败</div></div>';
    }
}

function closeTemplateModal() { document.getElementById('template-modal').style.display = 'none'; }

async function selectTemplate(type) {
    closeTemplateModal();
    const res = await utils.api.get(`/api/agents/templates/${type}`);
    if (res.success && res.template) openCreateModal(res.template);
}

// ============ 创建 / 编辑 ============
function openCreateModal(template = null) {
    document.getElementById('agent-modal').style.display = 'flex';
    document.getElementById('modal-title').textContent = template ? '从模板创建' : '新建 Agent';
    document.getElementById('agent-form').reset();
    clearErrors();
    document.getElementById('agent-id').value = '';
    if (template) populateForm(template);
    onTypeChange();
    setTimeout(() => document.getElementById('agent-name').focus(), 0);
}

async function editAgent(id) {
    const agent = allAgents.find(a => a.id === id);
    if (!agent) return;
    document.getElementById('agent-modal').style.display = 'flex';
    document.getElementById('modal-title').textContent = '编辑 Agent';
    clearErrors();
    document.getElementById('agent-id').value = agent.id;
    populateForm(agent);
    onTypeChange();
    setTimeout(() => document.getElementById('agent-name').focus(), 0);
}

function populateForm(agent) {
    document.getElementById('agent-name').value = agent.name || '';
    document.getElementById('agent-type').value = agent.type || 'openai';
    document.getElementById('agent-description').value = agent.description || '';
    document.getElementById('agent-endpoint').value = agent.endpoint || '';
    document.getElementById('agent-timeout').value = agent.timeout || 30000;
    document.getElementById('agent-api-key').value = agent.config?.apiKey || '';
    document.getElementById('agent-model').value = agent.config?.model || '';
    document.getElementById('agent-headers').value = agent.headers ? JSON.stringify(agent.headers, null, 2) : '';
    document.getElementById('agent-config').value = agent.config ? JSON.stringify(cleanConfig(agent.config), null, 2) : '';
    document.getElementById('agent-request-template').value = agent.requestMapping?.template || '';
    document.getElementById('agent-output-path').value = agent.responseMapping?.outputPath || '';
    document.getElementById('agent-error-path').value = agent.responseMapping?.errorPath || '';
    document.getElementById('agent-error-msg-path').value = agent.responseMapping?.errorMessagePath || '';
}

function cleanConfig(cfg) { const c = { ...cfg }; delete c.apiKey; return c; }

function onTypeChange() {
    const type = document.getElementById('agent-type').value;
    const simple = document.getElementById('type-simple');
    const advanced = document.getElementById('type-advanced');
    const endpoint = document.getElementById('agent-endpoint');

    if (type === 'openai') {
        simple.style.display = 'block'; advanced.style.display = 'none';
        if (!endpoint.value) endpoint.value = 'https://api.openai.com/v1/chat/completions';
    } else if (type === 'claude') {
        simple.style.display = 'block'; advanced.style.display = 'none';
        if (!endpoint.value) endpoint.value = 'https://api.anthropic.com/v1/messages';
    } else {
        simple.style.display = 'none'; advanced.style.display = 'block';
    }
}

function closeModal() { document.getElementById('agent-modal').style.display = 'none'; }

// ============ 表单提交 ============
function clearErrors() {
    document.querySelectorAll('.form-input.error, .form-textarea.error').forEach(el => el.classList.remove('error'));
    document.querySelectorAll('.field-error').forEach(el => el.remove());
}

function setSaving(loading) {
    const btn = document.getElementById('save-btn');
    if (!btn) return;
    btn.disabled = loading;
    btn.textContent = loading ? '保存中...' : '保存';
}

async function handleSubmit(e) {
    e.preventDefault();
    clearErrors();

    const name = document.getElementById('agent-name').value.trim();
    const endpoint = document.getElementById('agent-endpoint').value.trim();
    if (!name) { showFieldError('agent-name', '请填写名称'); return; }
    if (!endpoint) { showFieldError('agent-endpoint', '请填写接口地址'); return; }

    const type = document.getElementById('agent-type').value;
    const agentData = {
        name, type,
        description: document.getElementById('agent-description').value.trim(),
        endpoint,
        timeout: parseInt(document.getElementById('agent-timeout').value) || 30000,
    };

    if (type === 'openai' || type === 'claude') {
        const apiKey = document.getElementById('agent-api-key').value.trim();
        if (!apiKey) { showFieldError('agent-api-key', '请填写 API Key'); return; }
        agentData.config = {
            apiKey,
            model: document.getElementById('agent-model').value.trim() ||
                (type === 'openai' ? 'gpt-3.5-turbo' : 'claude-3-sonnet-20240229'),
        };
    } else {
        const headersText = document.getElementById('agent-headers').value.trim();
        const configText = document.getElementById('agent-config').value.trim();
        if (headersText) {
            try { agentData.headers = JSON.parse(headersText); }
            catch { showFieldError('agent-headers', 'JSON 格式错误'); return; }
        }
        if (configText) {
            try { agentData.config = JSON.parse(configText); }
            catch { showFieldError('agent-config', 'JSON 格式错误'); return; }
        }
        if (document.getElementById('agent-request-template').value.trim()) {
            agentData.requestMapping = { template: document.getElementById('agent-request-template').value.trim() };
        }
        if (document.getElementById('agent-output-path').value.trim()) {
            agentData.responseMapping = {
                outputPath: document.getElementById('agent-output-path').value.trim(),
                errorPath: document.getElementById('agent-error-path').value.trim(),
                errorMessagePath: document.getElementById('agent-error-msg-path').value.trim(),
            };
        }
    }

    const id = document.getElementById('agent-id').value;
    setSaving(true);
    try {
        let res;
        if (id) res = await utils.api.put(`/api/agents/${id}`, agentData);
        else res = await utils.api.post('/api/agents', agentData);

        if (res.success) {
            utils.toast.success(id ? '已更新' : '已创建');
            closeModal();
            await loadAgents();
        } else {
            utils.toast.error(res.error || '保存失败');
        }
    } catch (e) {
        utils.toast.error('保存失败: ' + e.message);
    } finally {
        setSaving(false);
    }
}

function showFieldError(id, msg) {
    const el = document.getElementById(id);
    if (!el) return;
    el.classList.add('error');
    const err = document.createElement('div');
    err.className = 'field-error';
    err.textContent = msg;
    el.parentElement.appendChild(err);
}

// ============ 测试弹窗 ============
function runQuickTest() {
    currentTestAgentId = '';
    openTestModal('🧪 快速测试');
}

function testAgentModal(id) {
    currentTestAgentId = id;
    const agent = allAgents.find(a => a.id === id);
    openTestModal(`🧪 测试：${agent?.name || 'Agent'}`);
}

function openTestModal(title) {
    document.getElementById('test-modal-title').textContent = title;
    const ctx = document.getElementById('test-context');
    if (currentTestAgentId) {
        const agent = allAgents.find(a => a.id === currentTestAgentId);
        if (agent) {
            const meta = TYPE_META[agent.type] || { label: agent.type, cls: '' };
            ctx.innerHTML = `<span class="agent-type-badge ${meta.cls}">${meta.label}</span> <span style="font-size:12px;color:var(--text-secondary)">${utils.escapeHtml(agent.endpoint || '')}</span>`;
        } else ctx.innerHTML = '';
    } else {
        ctx.innerHTML = '<span style="font-size:12px;color:var(--text-secondary)">从当前表单配置测试</span>';
    }
    document.getElementById('test-result').innerHTML = '';
    document.getElementById('test-modal').style.display = 'flex';
    document.getElementById('test-input').focus();
}

async function runTest() {
    const input = document.getElementById('test-input').value.trim();
    const resultEl = document.getElementById('test-result');
    if (!input) { resultEl.innerHTML = '<div class="test-msg error">请输入内容</div>'; return; }

    resultEl.innerHTML = '<div class="test-msg pending">⏳ 测试中...</div>';

    try {
        let res;
        if (currentTestAgentId) {
            res = await utils.api.post(`/api/agents/${currentTestAgentId}/test`, { input });
        } else {
            let payload;
            try { payload = buildConfigFromForm(); }
            catch { resultEl.innerHTML = '<div class="test-msg error">配置 JSON 格式错误</div>'; return; }
            res = await utils.api.post('/api/agents/test-config', { config: payload, input });
        }

        if (res.success) {
            resultEl.innerHTML = `
                <div class="test-msg success">✅ 成功 ${res.responseTimeMs ? `(${res.responseTimeMs}ms)` : ''}</div>
                <pre class="test-output">${utils.escapeHtml(res.output || '（无输出）')}</pre>`;
        } else {
            resultEl.innerHTML = `<div class="test-msg error">❌ ${utils.escapeHtml(res.error || '未知错误')}</div>`;
        }
    } catch (e) {
        resultEl.innerHTML = `<div class="test-msg error">❌ ${utils.escapeHtml(e.message)}</div>`;
    }
}

function buildConfigFromForm() {
    const type = document.getElementById('agent-type').value;
    const endpoint = document.getElementById('agent-endpoint').value.trim();
    const headersText = document.getElementById('agent-headers').value.trim();
    const configText = document.getElementById('agent-config').value.trim();
    const timeout = parseInt(document.getElementById('agent-timeout').value) || 30000;
    const payload = { name: '临时测试', type, endpoint, timeout };

    if (type === 'openai' || type === 'claude') {
        payload.config = {
            apiKey: document.getElementById('agent-api-key').value.trim(),
            model: document.getElementById('agent-model').value.trim() || 'gpt-3.5-turbo',
        };
    } else {
        if (headersText) payload.headers = JSON.parse(headersText);
        if (configText) payload.config = JSON.parse(configText);
        if (document.getElementById('agent-request-template').value.trim()) {
            payload.requestMapping = { template: document.getElementById('agent-request-template').value.trim() };
        }
        if (document.getElementById('agent-output-path').value.trim()) {
            payload.responseMapping = { outputPath: document.getElementById('agent-output-path').value.trim() };
        }
    }
    return payload;
}

function closeTestModal() { document.getElementById('test-modal').style.display = 'none'; }

// ============ 导入 / 导出 ============
function exportAgents() {
    if (!allAgents.length) { utils.toast.error('暂无 Agent 可导出'); return; }
    const blob = new Blob([JSON.stringify(allAgents, null, 2)], { type: 'application/json' });
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = `agents-${new Date().toISOString().slice(0, 10)}.json`;
    a.click();
    URL.revokeObjectURL(a.href);
    utils.toast.success(`已导出 ${allAgents.length} 个 Agent`);
}

function triggerImport() { document.getElementById('import-file').click(); }

async function handleImportFile(e) {
    const file = e.target.files?.[0];
    if (!file) return;
    try {
        const text = await file.text();
        const data = JSON.parse(text);
        const items = Array.isArray(data) ? data : [data];
        let ok = 0, fail = 0;
        for (const cfg of items) {
            if (!cfg.name || !cfg.type || !cfg.endpoint) { fail++; continue; }
            const { id, ...payload } = cfg;
            const res = await utils.api.post('/api/agents', payload);
            if (res.success) ok++; else fail++;
        }
        utils.toast.success(`导入完成：${ok} 成功，${fail} 失败`);
        await loadAgents();
    } catch (err) {
        utils.toast.error('导入失败: ' + err.message);
    }
    e.target.value = '';
}

// ============ 复制 / 删除 ============
async function copyAgent(id) {
    const agent = allAgents.find(a => a.id === id);
    if (!agent) return;
    const { id: _, ...payload } = { ...agent, name: agent.name + ' (副本)' };
    try {
        const res = await utils.api.post('/api/agents', payload);
        if (res.success) { utils.toast.success('已创建副本'); await loadAgents(); }
        else utils.toast.error(res.error || '复制失败');
    } catch (e) { utils.toast.error('复制失败: ' + e.message); }
}

async function deleteAgent(id) {
    if (!await utils.confirm('确定要删除这个 Agent 吗？')) return;
    try {
        const res = await utils.api.delete(`/api/agents/${id}`);
        if (res.success) { utils.toast.success('已删除'); await loadAgents(); }
        else utils.toast.error(res.error || '删除失败');
    } catch (e) { utils.toast.error('删除失败: ' + e.message); }
}
