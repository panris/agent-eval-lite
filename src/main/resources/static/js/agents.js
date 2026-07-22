/**
 * Agent 管理页 JS
 * 核心：列表展示 + 创建/编辑 + 模板 + 搜索 + 测试
 */

// ============ 状态 ============
let allAgents = [];
let filteredAgents = [];
let currentTestAgentId = null; // '' = 来自表单（未保存），id = 已保存的 Agent
let searchTimer = null;

// ============ 生命周期 ============
window.onload = async function () {
    await loadAgents();
    document.addEventListener('keydown', onKeydown);
};

// ============ 键盘 ============
function onKeydown(e) {
    if (e.key === 'Escape') {
        closeModal();
        closeTestModal();
        closeTemplateModal();
        return;
    }
    // 快捷键：n 新建，/ 聚焦搜索（输入框内不触发）
    const tag = (e.target.tagName || '').toLowerCase();
    const inField = tag === 'input' || tag === 'textarea' || e.target.isContentEditable;
    if (inField) return;
    if (e.key === 'n') { e.preventDefault(); openCreateModal(); }
    else if (e.key === '/') { e.preventDefault(); document.getElementById('agent-search')?.focus(); }
}

// ============ 加载列表 ============
async function loadAgents() {
    try {
        const res = await utils.api.get('/api/agents');
        if (res.success) {
            allAgents = res.agents || [];
            applySearch(document.getElementById('agent-search')?.value || '');
        } else {
            renderListError('加载失败');
        }
    } catch (e) {
        utils.logError('loadAgents failed', e);
        renderListError('加载失败');
    }
}

function renderListError(msg) {
    document.getElementById('agents-list').innerHTML = `
        <div class="empty-state">
            <div class="empty-icon">⚠️</div>
            <div>${utils.escapeHtml(msg)}</div>
            <button class="btn btn-secondary" onclick="loadAgents()">重试</button>
        </div>`;
}

// ============ 搜索 ============
function handleSearch(value) {
    clearTimeout(searchTimer);
    document.getElementById('search-clear').style.display = value ? 'flex' : 'none';
    searchTimer = setTimeout(() => applySearch(value), 200);
}

function clearSearch() {
    const input = document.getElementById('agent-search');
    if (input) input.value = '';
    document.getElementById('search-clear').style.display = 'none';
    applySearch('');
}

function applySearch(keyword) {
    const kw = keyword.toLowerCase().trim();
    filteredAgents = kw
        ? allAgents.filter(a =>
            a.name?.toLowerCase().includes(kw) ||
            a.type?.toLowerCase().includes(kw) ||
            a.endpoint?.toLowerCase().includes(kw) ||
            a.description?.toLowerCase().includes(kw))
        : [...allAgents];
    renderList();
}

// ============ 渲染列表 ============
function renderList() {
    const el = document.getElementById('agents-list');

    if (filteredAgents.length === 0) {
        if (allAgents.length === 0) {
            el.innerHTML = `
                <div class="empty-state">
                    <div class="empty-icon">🤖</div>
                    <div class="empty-title">还没有配置 Agent</div>
                    <div class="empty-desc">从模板快速创建，或自定义配置接口</div>
                    <div class="empty-actions">
                        <button class="btn btn-outline" onclick="openTemplateModal()">📦 从模板创建</button>
                        <button class="btn btn-primary" onclick="openCreateModal()">➕ 新建空白配置</button>
                    </div>
                </div>`;
        } else {
            el.innerHTML = `
                <div class="empty-state">
                    <div class="empty-icon">🔍</div>
                    <div class="empty-title">未找到匹配的 Agent</div>
                    <div class="empty-desc">试试其他关键词，或清除搜索条件</div>
                    <button class="btn btn-secondary" onclick="clearSearch()">清除搜索</button>
                </div>`;
        }
        return;
    }

    el.innerHTML = `<div class="agents-grid">${filteredAgents.map(renderCard).join('')}</div>`;
}

const TYPE_META = {
    openai: { label: 'OpenAI', cls: 'type-openai', icon: '🟢' },
    claude: { label: 'Claude',  cls: 'type-claude', icon: '🟠' },
    http:   { label: 'HTTP',    cls: 'type-http',   icon: '🟣' },
    custom: { label: '自定义',  cls: 'type-custom', icon: '⚪' },
};

function renderCard(a) {
    const meta = TYPE_META[a.type] || { label: a.type || 'Agent', cls: 'type-custom', icon: '⚪' };
    const endpoint = a.endpoint || '';
    return `
        <div class="agent-card" data-id="${utils.escapeHtml(a.id)}">
            <div class="agent-card-top">
                <div class="agent-card-title">
                    <span class="agent-type-icon" title="${meta.label}">${meta.icon}</span>
                    <span class="agent-name" title="${utils.escapeHtml(a.name)}">${utils.escapeHtml(a.name)}</span>
                    <span class="agent-type-badge ${meta.cls}">${meta.label}</span>
                </div>
                <div class="agent-card-menu" onclick="event.stopPropagation()">
                    <button class="menu-btn" onclick="toggleMenu(this)" aria-label="更多操作">⋮</button>
                    <div class="menu-dropdown" style="display:none">
                        <div class="menu-item" onclick="editAgent('${a.id}')">✏️ 编辑</div>
                        <div class="menu-item" onclick="testAgentModal('${a.id}')">🧪 测试</div>
                        <div class="menu-item" onclick="copyAgent('${a.id}')">📋 复制</div>
                        <div class="menu-divider"></div>
                        <div class="menu-item danger" onclick="deleteAgent('${a.id}')">🗑️ 删除</div>
                    </div>
                </div>
            </div>
            <div class="agent-card-endpoint" title="点击复制端点" onclick="copyEndpoint(this, '${utils.escapeHtml(endpoint)}')">
                <span class="endpoint-text">${utils.escapeHtml(endpoint)}</span>
                <span class="copy-hint">⧉</span>
            </div>
            ${a.description ? `<div class="agent-card-desc" title="${utils.escapeHtml(a.description)}">${utils.escapeHtml(a.description)}</div>` : ''}
            <div class="agent-card-test">
                <input type="text" class="test-input" placeholder="🧪 输入内容测试..."
                       onkeydown="if(event.key==='Enter')testCardAgent('${a.id}', this)">
                <button class="btn btn-sm btn-outline" onclick="testCardAgent('${a.id}', this.previousElementSibling)">发送</button>
            </div>
        </div>`;
}

async function copyEndpoint(el, endpoint) {
    try {
        await navigator.clipboard.writeText(endpoint);
        utils.toast.success('端点已复制');
    } catch {
        // 降级：临时 textarea
        const ta = document.createElement('textarea');
        ta.value = endpoint;
        document.body.appendChild(ta);
        ta.select();
        try { document.execCommand('copy'); utils.toast.success('端点已复制'); } catch {}
        document.body.removeChild(ta);
    }
}

function toggleMenu(btn) {
    const dropdown = btn.nextElementSibling;
    const isOpen = dropdown.style.display !== 'none';
    document.querySelectorAll('.menu-dropdown').forEach(m => m.style.display = 'none');
    dropdown.style.display = isOpen ? 'none' : 'block';
}

// 点击页面其他位置关闭所有菜单
document.addEventListener('click', () => document.querySelectorAll('.menu-dropdown').forEach(m => m.style.display = 'none'));

// ============ 卡片内测试 ============
async function testCardAgent(id, inputEl) {
    const input = inputEl.value.trim();
    if (!input) { inputEl.focus(); return; }

    const card = inputEl.closest('.agent-card');
    const resultEl = card.querySelector('.card-test-result') || createCardResultEl(card);
    resultEl.style.display = 'flex';
    resultEl.innerHTML = '<div class="spinner-sm"></div><span>测试中...</span>';

    try {
        const res = await utils.api.post(`/api/agents/${id}/test`, { input });
        if (res.success) {
            resultEl.className = 'card-test-result success';
            resultEl.innerHTML = `<span>✅ ${utils.escapeHtml(truncate(res.output, 120))}</span><button class="text-btn" onclick="this.parentElement.style.display='none'">隐藏</button>`;
        } else {
            resultEl.className = 'card-test-result error';
            resultEl.innerHTML = `<span>❌ ${utils.escapeHtml(res.error || '未知错误')}</span><button class="text-btn" onclick="this.parentElement.style.display='none'">隐藏</button>`;
        }
    } catch (e) {
        resultEl.className = 'card-test-result error';
        resultEl.innerHTML = `<span>❌ ${utils.escapeHtml(e.message)}</span><button class="text-btn" onclick="this.parentElement.style.display='none'">隐藏</button>`;
    }
}

function createCardResultEl(card) {
    const el = document.createElement('div');
    el.className = 'card-test-result';
    card.querySelector('.agent-card-test').after(el);
    return el;
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
            const descs = {
                openai: '支持 GPT-4、GPT-3.5 等模型，填入 API Key 即可使用',
                claude: '支持 Claude 3 系列模型，Anthropic API',
                http: '简单的 input→output HTTP 接口，适合自定义后端',
                custom: '完全自定义请求和响应格式，支持 JSONPath 映射',
            };
            list.innerHTML = res.templates.map(t => `
                <div class="template-item" onclick="selectTemplate('${t.type}')">
                    <div class="template-item-icon">${TYPE_META[t.type]?.icon || '⚪'}</div>
                    <div class="template-item-info">
                        <div class="template-item-name">${utils.escapeHtml(t.name)}</div>
                        <div class="template-item-desc">${descs[t.type] || utils.escapeHtml(t.description || '')}</div>
                    </div>
                    <div class="template-item-arrow">→</div>
                </div>`).join('');
        } else {
            list.innerHTML = '<div class="empty-state"><div class="empty-icon">📦</div><div>暂无模板</div></div>';
        }
    } catch (e) {
        list.innerHTML = '<div class="empty-state"><div class="empty-icon">⚠️</div><div>加载失败</div></div>';
    }
}

function closeTemplateModal() {
    document.getElementById('template-modal').style.display = 'none';
}

async function selectTemplate(type) {
    closeTemplateModal();
    const res = await utils.api.get(`/api/agents/templates/${type}`);
    if (res.success && res.template) {
        openCreateModal(res.template);
    }
}

// ============ 创建 / 编辑弹窗 ============
function openCreateModal(template = null) {
    document.getElementById('agent-modal').style.display = 'flex';
    document.getElementById('modal-title').textContent = template ? '从模板创建' : '新建 Agent';
    document.getElementById('agent-form').reset();
    clearErrors();
    document.getElementById('agent-id').value = '';
    if (template) populateForm(template);
    onTypeChange();
}

async function editAgent(id) {
    const agent = allAgents.find(a => a.id === id);
    if (!agent) return;
    closeAllMenus();
    document.getElementById('agent-modal').style.display = 'flex';
    document.getElementById('modal-title').textContent = '编辑 Agent';
    clearErrors();
    document.getElementById('agent-id').value = agent.id;
    populateForm(agent);
    onTypeChange();
}

// 填充表单（创建/编辑/模板共用）
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

function cleanConfig(cfg) {
    // 展示时移除 apiKey（安全考虑）
    const c = { ...cfg };
    delete c.apiKey;
    return c;
}

function onTypeChange() {
    const type = document.getElementById('agent-type').value;
    const simple = document.getElementById('type-simple');
    const advanced = document.getElementById('type-advanced');
    const endpoint = document.getElementById('agent-endpoint');

    if (type === 'openai') {
        simple.style.display = 'block';
        advanced.style.display = 'none';
        if (!endpoint.value) endpoint.value = 'https://api.openai.com/v1/chat/completions';
    } else if (type === 'claude') {
        simple.style.display = 'block';
        advanced.style.display = 'none';
        if (!endpoint.value) endpoint.value = 'https://api.anthropic.com/v1/messages';
    } else {
        simple.style.display = 'none';
        advanced.style.display = 'block';
    }
}

function closeModal() {
    document.getElementById('agent-modal').style.display = 'none';
}

function closeAllMenus() {
    document.querySelectorAll('.menu-dropdown').forEach(m => m.style.display = 'none');
}

// ============ 表单提交 ============
function clearErrors() {
    document.querySelectorAll('.form-input.error, .form-textarea.error').forEach(el => el.classList.remove('error'));
    document.querySelectorAll('.field-error').forEach(el => el.remove());
}

function setSaving(loading) {
    const btn = document.getElementById('save-btn');
    if (!btn) return;
    btn.disabled = loading;
    btn.textContent = loading ? '⏳ 保存中...' : '💾 保存';
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
        name,
        type,
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
            utils.toast.success(id ? 'Agent 已更新' : 'Agent 已创建');
            closeModal();
            await loadAgents();
        } else {
            utils.toast.error(res.error || '保存失败');
        }
    } catch (e) {
        utils.logError('saveAgent failed', e);
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
    openTestModal('🧪 快速测试', '你好，请简单介绍一下你自己');
}

function testAgentModal(id) {
    closeAllMenus();
    currentTestAgentId = id;
    const agent = allAgents.find(a => a.id === id);
    const name = agent?.name || 'Agent';
    openTestModal(`🧪 测试：${name}`, '你好，请简单介绍一下你自己');
}

function openTestModal(title, defaultInput) {
    document.getElementById('test-modal-title').textContent = title;
    document.getElementById('test-input').value = defaultInput;
    document.getElementById('test-result').innerHTML = '';
    document.getElementById('test-modal').style.display = 'flex';
    document.getElementById('test-input').focus();
}

async function runTest() {
    const input = document.getElementById('test-input').value.trim();
    const resultEl = document.getElementById('test-result');
    if (!input) { resultEl.innerHTML = '<div class="test-msg error">❌ 请输入内容</div>'; return; }

    resultEl.innerHTML = '<div class="test-msg pending">⏳ 测试中...</div>';

    try {
        let res;
        if (currentTestAgentId) {
            res = await utils.api.post(`/api/agents/${currentTestAgentId}/test`, { input });
        } else {
            const payload = buildConfigFromForm();
            res = await utils.api.post('/api/agents/test-config', { config: payload, input });
        }

        if (res.success) {
            resultEl.innerHTML = `
                <div class="test-msg success">✅ 成功</div>
                <div class="test-block">
                    <div class="test-label">输入</div>
                    <div class="test-val">${utils.escapeHtml(input)}</div>
                </div>
                <div class="test-block">
                    <div class="test-label">输出</div>
                    <div class="test-val code">${utils.escapeHtml(res.output || '（无输出）')}</div>
                </div>
                ${res.responseTimeMs ? `<div class="test-meta">耗时 ${res.responseTimeMs}ms</div>` : ''}`;
        } else {
            resultEl.innerHTML = `<div class="test-msg error">❌ ${utils.escapeHtml(res.error || res.message || '未知错误')}</div>`;
        }
    } catch (e) {
        resultEl.innerHTML = `<div class="test-msg error">❌ ${utils.escapeHtml(e.message)}</div>`;
    }
}

// 从表单构建 config（快速测试用）
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
            model: document.getElementById('agent-model').value.trim() ||
                (type === 'openai' ? 'gpt-3.5-turbo' : 'claude-3-sonnet-20240229'),
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

function closeTestModal() {
    document.getElementById('test-modal').style.display = 'none';
}

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

// ============ 复制 ============
async function copyAgent(id) {
    closeAllMenus();
    const agent = allAgents.find(a => a.id === id);
    if (!agent) return;
    const { id: _, ...payload } = { ...agent, name: agent.name + ' (副本)' };
    try {
        const res = await utils.api.post('/api/agents', payload);
        if (res.success) { utils.toast.success('已创建副本'); await loadAgents(); }
        else utils.toast.error(res.error || '复制失败');
    } catch (e) {
        utils.toast.error('复制失败: ' + e.message);
    }
}

// ============ 删除 ============
async function deleteAgent(id) {
    closeAllMenus();
    if (!await utils.confirm('确定要删除这个 Agent 配置吗？')) return;
    try {
        const res = await utils.api.delete(`/api/agents/${id}`);
        if (res.success) { utils.toast.success('已删除'); await loadAgents(); }
        else utils.toast.error(res.error || '删除失败');
    } catch (e) {
        utils.logError('deleteAgent failed', e);
        utils.toast.error('删除失败: ' + e.message);
    }
}

// ============ 工具 ============
function truncate(str, max) {
    if (!str) return '';
    return str.length > max ? str.slice(0, max) + '…' : str;
}
