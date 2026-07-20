/**
 * Agent 管理页面 JS 模块
 * 职责：Agent 配置管理、模板、CRUD、测试
 */

// ============ 状态 ============
let agents = [];
let currentTemplate = null;
let filteredAgents = [];

// ============ 生命周期 ============
window.onload = async function() {
    await Promise.all([loadTemplates(), loadAgents()]);
    // ESC 关闭弹窗
    document.addEventListener('keydown', e => {
        if (e.key === 'Escape') {
            if (document.getElementById('agent-modal').style.display === 'flex') closeModal();
            if (document.getElementById('test-modal').style.display === 'flex') closeTestModal();
        }
    });
};

// ============ 模板 ============

async function loadTemplates() {
    try {
        const res = await utils.api.get('/api/agents/templates');
        if (res.success) renderTemplates(res.templates);
        else renderTemplatesError('加载模板失败');
    } catch (e) {
        utils.logError('Failed to load templates', e);
        renderTemplatesError('加载模板失败');
    }
}

function renderTemplatesError(msg) {
    document.getElementById('templates-grid').innerHTML = `
        <div class="empty-state">
            <div class="empty-icon">⚠️</div>
            <div>${utils.escapeHtml(msg)}</div>
            <button class="btn btn-secondary mt-12" onclick="loadTemplates()">重试</button>
        </div>`;
}

function renderTemplates(templates) {
    const grid = document.getElementById('templates-grid');
    const icons = { openai: '🟢', claude: '🟠', custom: '🔵', http: '🟣' };
    const features = {
        openai: '支持 GPT-4、GPT-3.5 等模型',
        claude: '支持 Claude 3 系列模型',
        custom: '完全自定义请求和响应格式',
        http: '简单的 input/output 接口'
    };

    if (!templates?.length) {
        grid.innerHTML = `<div class="empty-state"><div class="empty-icon">📦</div><div>暂无可用模板</div></div>`;
        return;
    }
    grid.innerHTML = templates.map(t => `
        <div class="template-card" onclick="createFromTemplate(${utils.escapeHtml(JSON.stringify(t))})">
            <div class="template-icon">${icons[t.type] || '⚪'}</div>
            <div class="template-name">${utils.escapeHtml(t.name)}</div>
            <div class="template-desc">${utils.escapeHtml(t.description || features[t.type] || '')}</div>
        </div>`).join('');
}

function createFromTemplate(template) {
    currentTemplate = template;
    openCreateModal(template);
}

// ============ Agent 列表 ============

async function loadAgents() {
    try {
        const res = await utils.api.get('/api/agents');
        if (res.success) {
            agents = res.agents || [];
            applyFilter();
        } else {
            renderAgentsError('加载 Agent 列表失败');
        }
    } catch (e) {
        utils.logError('Failed to load agents', e);
        renderAgentsError('加载 Agent 列表失败');
    }
}

function renderAgentsError(msg) {
    document.getElementById('agents-list').innerHTML = `
        <div class="empty-state">
            <div class="empty-icon">⚠️</div>
            <div>${utils.escapeHtml(msg)}</div>
            <button class="btn btn-secondary mt-12" onclick="loadAgents()">重试</button>
        </div>`;
}

/**
 * 根据搜索词过滤 Agent 列表
 */
function applyFilter() {
    const keyword = (document.getElementById('agent-search')?.value || '').toLowerCase().trim();
    filteredAgents = keyword
        ? agents.filter(a =>
            a.name?.toLowerCase().includes(keyword) ||
            a.type?.toLowerCase().includes(keyword) ||
            a.endpoint?.toLowerCase().includes(keyword) ||
            a.description?.toLowerCase().includes(keyword))
        : [...agents];
    renderAgents();
}

function renderAgents() {
    const list = document.getElementById('agents-list');
    const typeLabels = { openai: 'OpenAI', claude: 'Claude', custom: '自定义', http: 'HTTP' };

    if (filteredAgents.length === 0) {
        list.innerHTML = `
            <div class="empty-state">
                <div class="empty-icon">🤖</div>
                <div style="font-size:18px;font-weight:500;margin-bottom:8px;">暂无 Agent 配置</div>
                <div style="margin-bottom:20px;">从模板快速创建，或自定义配置您的 Agent</div>
                <button class="btn btn-primary" onclick="switchTab('templates')">📦 浏览模板</button>
            </div>`;
        return;
    }

    list.innerHTML = filteredAgents.map(a => `
        <div class="agent-card">
            <div class="agent-header">
                <div class="agent-name">${utils.escapeHtml(a.name)}</div>
                <span class="agent-type type-${utils.escapeHtml(a.type)}">${typeLabels[a.type] || a.type}</span>
            </div>
            <div class="agent-info">📍 ${utils.escapeHtml(a.endpoint)}</div>
            ${a.description ? `<div class="agent-info">📝 ${utils.escapeHtml(a.description)}</div>` : ''}
            <div class="agent-actions">
                <button class="btn btn-secondary" onclick="editAgent('${utils.escapeHtml(a.id)}')">✏️ 编辑</button>
                <button class="btn btn-secondary" onclick="testAgent('${utils.escapeHtml(a.id)}')">🧪 测试</button>
                <button class="btn btn-secondary" onclick="copyAgent('${utils.escapeHtml(a.id)}')">📋 复制</button>
                <button class="btn btn-danger" onclick="deleteAgent('${utils.escapeHtml(a.id)}')">🗑️ 删除</button>
            </div>
        </div>`).join('');
}

// ============ 导入 / 导出 ============

/** 导出所有 Agent 为 JSON 文件下载 */
function exportAgents() {
    if (!agents.length) { utils.toast.error('暂无 Agent 可导出'); return; }
    const blob = new Blob([JSON.stringify(agents, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a'); a.href = url;
    a.download = `agents-${new Date().toISOString().slice(0,10)}.json`;
    a.click(); URL.revokeObjectURL(url);
    utils.toast.success(`已导出 ${agents.length} 个 Agent`);
}

/** 触发文件导入 */
function triggerImport() { document.getElementById('import-file').click(); }

/** 处理文件导入 */
async function handleImportFile(e) {
    const file = e.target.files?.[0];
    if (!file) return;
    try {
        const text = await file.text();
        const imported = JSON.parse(text);
        if (!Array.isArray(imported)) throw new Error('格式错误：期望 JSON 数组');
        let ok = 0, fail = 0;
        for (const cfg of imported) {
            if (!cfg.name || !cfg.type || !cfg.endpoint) { fail++; continue; }
            // 移除 id 让后端创建新记录
            const { id, ...payload } = cfg;
            const res = await utils.api.post('/api/agents', payload);
            if (res.success) ok++; else fail++;
        }
        utils.toast.success(`导入完成：成功 ${ok}，失败 ${fail}`);
        await loadAgents();
    } catch (err) {
        utils.toast.error('导入失败: ' + err.message);
    }
    e.target.value = '';
}

// ============ 模态框 ============

function switchTab(tab) {
    document.querySelectorAll('.tab').forEach((t, i) =>
        t.classList.toggle('active', ['templates','agents'][i] === tab));
    document.querySelectorAll('.tab-content').forEach(t => t.style.display = 'none');
    const target = document.getElementById(tab + '-tab');
    if (target) target.style.display = 'block';
}

function openCreateModal(template = null) {
    document.getElementById('agent-modal').style.display = 'flex';
    document.getElementById('modal-title').textContent = template ? '从模板创建 Agent' : '创建 Agent';
    document.getElementById('agent-form').reset();
    clearFormErrors();
    document.getElementById('agent-id').value = '';
    if (template) populateForm(template);
    updateFormByType();
}

async function editAgent(id) {
    const agent = agents.find(a => a.id === id);
    if (!agent) return;
    document.getElementById('agent-modal').style.display = 'flex';
    document.getElementById('modal-title').textContent = '编辑 Agent';
    clearFormErrors();
    document.getElementById('agent-id').value = agent.id;
    populateForm(agent);
    updateFormByType();
}

/**
 * 统一表单填充 — 消除 openCreateModal / editAgent 重复
 */
function populateForm(data) {
    const set = (id, val) => {
        const el = document.getElementById(id);
        if (el) el.value = val ?? '';
    };
    set('agent-name', data.name);
    set('agent-type', data.type || 'openai');
    set('agent-description', data.description);
    set('agent-endpoint', data.endpoint);
    set('agent-timeout', data.timeout || 30000);
    set('agent-api-key', data.config?.apiKey ?? '');
    set('agent-model', data.config?.model ?? '');
    set('agent-headers', data.headers ? JSON.stringify(data.headers, null, 2) : '');
    set('agent-config', data.config ? JSON.stringify(data.config, null, 2) : '');
    set('agent-request-template', data.requestMapping?.template ?? '');
    set('agent-output-path', data.responseMapping?.outputPath ?? '');
    set('agent-error-path', data.responseMapping?.errorPath ?? '');
    set('agent-error-msg-path', data.responseMapping?.errorMessagePath ?? '');
}

function updateFormByType() {
    const type = document.getElementById('agent-type').value;
    const simple = document.getElementById('simple-config');
    const custom = document.getElementById('custom-config');
    if (type === 'openai' || type === 'claude') {
        simple.style.display = 'block';
        custom.style.display = 'none';
        if (type === 'openai' && !document.getElementById('agent-endpoint').value)
            document.getElementById('agent-endpoint').value = 'https://api.openai.com/v1/chat/completions';
        if (type === 'claude' && !document.getElementById('agent-endpoint').value)
            document.getElementById('agent-endpoint').value = 'https://api.anthropic.com/v1/messages';
    } else {
        simple.style.display = 'none';
        custom.style.display = 'block';
    }
}

function closeModal() {
    document.getElementById('agent-modal').style.display = 'none';
}

// ============ JSON 校验 ============

function validateJsonField(id, label) {
    const el = document.getElementById(id);
    if (!el) return true;
    const existing = el.parentElement.querySelector('.json-error-msg');
    if (existing) existing.remove();
    el.classList.remove('form-error');

    const val = el.value.trim();
    if (!val) return true;  // 空值跳过

    try {
        JSON.parse(val);
        return true;
    } catch (e) {
        el.classList.add('form-error');
        const hint = document.createElement('div');
        hint.className = 'json-error-msg';
        hint.textContent = `${label} JSON 格式错误: ${e.message}`;
        el.parentElement.appendChild(hint);
        return false;
    }
}

// ============ 复制 ============

async function copyAgent(id) {
    const agent = agents.find(a => a.id === id);
    if (!agent) return;
    const { id: _, ...payload } = { ...agent, name: agent.name + ' (副本)' };
    try {
        const res = await utils.api.post('/api/agents', payload);
        if (res.success) {
            utils.toast.success('已创建副本');
            await loadAgents();
        } else {
            utils.toast.error(res.error || '复制失败');
        }
    } catch (e) {
        utils.toast.error('复制失败: ' + e.message);
    }
}

// ============ 删除 ============

async function deleteAgent(id) {
    if (!await utils.confirm('确定要删除这个 Agent 配置吗？')) return;
    try {
        const res = await utils.api.delete(`/api/agents/${id}`);
        if (res.success) { utils.toast.success('Agent 已删除'); await loadAgents(); }
        else utils.toast.error(res.error || '删除失败');
    } catch (e) {
        utils.logError('Failed to delete agent', e);
        utils.toast.error('删除失败: ' + e.message);
    }
}

// ============ 表单提交 ============

function clearFormErrors() {
    document.querySelectorAll('.form-error').forEach(el => el.classList.remove('form-error'));
    document.querySelectorAll('.json-error-msg').forEach(el => el.remove());
}

function setSaving(loading) {
    const btn = document.getElementById('save-btn');
    if (!btn) return;
    btn.disabled = loading;
    btn.classList.toggle('btn-loading', loading);
    btn.textContent = loading ? '⏳ 保存中...' : (document.getElementById('agent-id').value ? '💾 更新' : '💾 保存');
}

document.addEventListener('DOMContentLoaded', function() {
    const form = document.getElementById('agent-form');

    // JSON 字段 blur 时校验
    ['agent-headers', 'agent-config'].forEach(id => {
        const el = document.getElementById(id);
        if (el) el.addEventListener('blur', () => validateJsonField(id, id === 'agent-headers' ? '请求头' : '静态配置'));
    });

    form.addEventListener('submit', async function(e) {
        e.preventDefault();
        clearFormErrors();

        // 必填校验
        const name = document.getElementById('agent-name').value.trim();
        const endpoint = document.getElementById('agent-endpoint').value.trim();
        if (!name) { utils.toast.error('请填写名称'); return; }
        if (!endpoint) { utils.toast.error('请填写端点 URL'); return; }

        // JSON 校验
        if (!validateJsonField('agent-headers', '请求头')) return;
        if (!validateJsonField('agent-config', '静态配置')) return;

        const type = document.getElementById('agent-type').value;
        const agentData = {
            name, type,
            description: document.getElementById('agent-description').value.trim(),
            endpoint,
            timeout: parseInt(document.getElementById('agent-timeout').value) || 30000
        };

        if (type === 'openai' || type === 'claude') {
            agentData.config = {
                apiKey: document.getElementById('agent-api-key').value.trim(),
                model: document.getElementById('agent-model').value.trim() ||
                    (type === 'openai' ? 'gpt-3.5-turbo' : 'claude-3-sonnet-20240229')
            };
        } else {
            const headersText = document.getElementById('agent-headers').value.trim();
            if (headersText) agentData.headers = JSON.parse(headersText);
            const configText = document.getElementById('agent-config').value.trim();
            if (configText) agentData.config = JSON.parse(configText);
            agentData.requestMapping = {
                template: document.getElementById('agent-request-template').value.trim()
            };
            agentData.responseMapping = {
                outputPath: document.getElementById('agent-output-path').value.trim(),
                errorPath: document.getElementById('agent-error-path').value.trim(),
                errorMessagePath: document.getElementById('agent-error-msg-path').value.trim()
            };
        }

        const agentId = document.getElementById('agent-id').value;
        setSaving(true);
        try {
            let res;
            if (agentId) res = await utils.api.put(`/api/agents/${agentId}`, agentData);
            else res = await utils.api.post('/api/agents', agentData);

            if (res.success) {
                utils.toast.success(agentId ? 'Agent 更新成功' : 'Agent 创建成功');
                closeModal();
                await loadAgents();
            } else {
                utils.toast.error(res.error || '操作失败');
            }
        } catch (e) {
            utils.logError('Failed to save agent', e);
            utils.toast.error('保存失败: ' + e.message);
        } finally {
            setSaving(false);
        }
    });
});

// ============ 测试 ============

async function testAgent(id) {
    document.getElementById('test-modal').style.display = 'flex';
    document.getElementById('test-result').innerHTML = '';
    document.getElementById('test-modal').dataset.agentId = id;
}

function testAgentConfig() {
    document.getElementById('test-modal').style.display = 'flex';
    document.getElementById('test-result').innerHTML = '';
    document.getElementById('test-modal').dataset.agentId = '';
}

async function runTest() {
    const input = document.getElementById('test-input').value.trim();
    const agentId = document.getElementById('test-modal').dataset.agentId;
    const resultDiv = document.getElementById('test-result');

    if (!input) { resultDiv.innerHTML = `<div class="status-badge status-error">❌ 请输入测试内容</div>`; return; }

    resultDiv.innerHTML = '<div class="status-badge status-pending">⏳ 测试中...</div>';
    try {
        const res = await utils.api.post(`/api/agents/${agentId}/test`, { input });
        if (res.success) {
            resultDiv.innerHTML = `
                <div class="status-badge status-success">✅ 测试成功</div>
                <div class="test-result-block">
                    <div class="agent-info"><strong>输入:</strong></div>
                    <div class="json-preview">${utils.escapeHtml(input)}</div>
                    <div class="agent-info mt-12"><strong>输出:</strong></div>
                    <div class="json-preview">${utils.escapeHtml(res.output || '无输出')}</div>
                </div>`;
        } else {
            resultDiv.innerHTML = `<div class="status-badge status-error">❌ 测试失败: ${utils.escapeHtml(res.error || '未知错误')}</div>`;
        }
    } catch (e) {
        resultDiv.innerHTML = `<div class="status-badge status-error">❌ 请求失败: ${utils.escapeHtml(e.message)}</div>`;
    }
}

function closeTestModal() {
    document.getElementById('test-modal').style.display = 'none';
}
