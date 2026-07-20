/**
 * Agent 管理页面 JS 模块
 * 职责：Agent 配置管理、模板、CRUD、测试
 */

let agents = [];
let currentTemplate = null;

// ============ 生命周期 ============

window.onload = async function() {
    await Promise.all([
        loadTemplates(),
        loadAgents()
    ]);
};

// ============ 模板 ============

/**
 * 加载模板列表
 */
async function loadTemplates() {
    try {
        const res = await utils.api.get('/api/agents/templates');
        if (res.success) {
            renderTemplates(res.templates);
        } else {
            renderTemplatesError('加载模板失败');
        }
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
            <button class="btn btn-secondary" onclick="loadTemplates()" style="margin-top: 12px;">重试</button>
        </div>
    `;
}

/**
 * 渲染模板卡片
 */
function renderTemplates(templates) {
    const grid = document.getElementById('templates-grid');
    const icons = {
        'openai': '🟢',
        'claude': '🟠',
        'custom': '🔵',
        'http': '🟣'
    };
    const features = {
        'openai': '支持 GPT-4、GPT-3.5 等模型',
        'claude': '支持 Claude 3 系列模型',
        'custom': '完全自定义请求和响应格式',
        'http': '简单的 input/output 接口'
    };

    if (!templates || templates.length === 0) {
        grid.innerHTML = `
            <div class="empty-state">
                <div class="empty-icon">📦</div>
                <div>暂无可用模板</div>
            </div>
        `;
        return;
    }

    grid.innerHTML = templates.map(t => `
        <div class="template-card" onclick="createFromTemplate(${utils.escapeHtml(JSON.stringify(t))})">
            <div class="template-icon">${icons[t.type] || '⚪'}</div>
            <div class="template-name">${utils.escapeHtml(t.name)}</div>
            <div class="template-desc">${utils.escapeHtml(t.description || features[t.type] || '')}</div>
        </div>
    `).join('');
}

/**
 * 从模板创建 Agent
 */
function createFromTemplate(template) {
    currentTemplate = template;
    openCreateModal(template);
}

// ============ Agent 列表 ============

/**
 * 加载 Agent 列表
 */
async function loadAgents() {
    try {
        const res = await utils.api.get('/api/agents');
        if (res.success) {
            agents = res.agents || [];
            renderAgents();
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
            <button class="btn btn-secondary" onclick="loadAgents()" style="margin-top: 12px;">重试</button>
        </div>
    `;
}

/**
 * 渲染 Agent 列表
 */
function renderAgents() {
    const list = document.getElementById('agents-list');
    const typeLabels = {
        'openai': 'OpenAI',
        'claude': 'Claude',
        'custom': '自定义',
        'http': 'HTTP'
    };

    if (agents.length === 0) {
        list.innerHTML = `
            <div class="empty-state">
                <div class="empty-icon">🤖</div>
                <div style="font-size: 18px; font-weight: 500; margin-bottom: 8px;">暂无 Agent 配置</div>
                <div style="margin-bottom: 20px;">从模板快速创建，或自定义配置您的 Agent</div>
                <button class="btn btn-primary" onclick="switchTab('templates')">📦 浏览模板</button>
            </div>
        `;
        return;
    }

    list.innerHTML = agents.map(agent => `
        <div class="agent-card">
            <div class="agent-header">
                <div class="agent-name">${utils.escapeHtml(agent.name)}</div>
                <span class="agent-type type-${utils.escapeHtml(agent.type)}">${typeLabels[agent.type] || agent.type}</span>
            </div>
            <div class="agent-info">📍 ${utils.escapeHtml(agent.endpoint)}</div>
            ${agent.description ? `<div class="agent-info">📝 ${utils.escapeHtml(agent.description)}</div>` : ''}
            <div class="agent-actions">
                <button class="btn btn-secondary" onclick="editAgent('${utils.escapeHtml(agent.id)}')">✏️ 编辑</button>
                <button class="btn btn-secondary" onclick="testAgent('${utils.escapeHtml(agent.id)}')">🧪 测试</button>
                <button class="btn btn-danger" onclick="deleteAgent('${utils.escapeHtml(agent.id)}')">🗑️ 删除</button>
            </div>
        </div>
    `).join('');
}

// ============ 模态框 ============

/**
 * 切换标签页
 */
function switchTab(tab) {
    document.querySelectorAll('.tab').forEach((t, i) => {
        t.classList.toggle('active', ['templates', 'agents'][i] === tab);
    });
    document.querySelectorAll('.tab-content').forEach(t => t.style.display = 'none');
    const target = document.getElementById(tab + '-tab');
    if (target) target.style.display = 'block';
}

/**
 * 打开创建弹窗
 */
function openCreateModal(template = null) {
    document.getElementById('agent-modal').style.display = 'flex';
    document.getElementById('modal-title').textContent = template ? '从模板创建 Agent' : '创建 Agent';

    document.getElementById('agent-form').reset();
    document.getElementById('agent-id').value = '';

    if (template) {
        document.getElementById('agent-name').value = template.name || '';
        document.getElementById('agent-type').value = template.type || 'openai';
        document.getElementById('agent-description').value = template.description || '';
        document.getElementById('agent-endpoint').value = template.endpoint || '';
        document.getElementById('agent-timeout').value = template.timeout || 30000;

        if (template.config) {
            document.getElementById('agent-api-key').value = template.config.apiKey || '';
            document.getElementById('agent-model').value = template.config.model || '';
        }

        if (template.requestMapping) {
            document.getElementById('agent-request-template').value = template.requestMapping.template || '';
        }

        if (template.responseMapping) {
            document.getElementById('agent-output-path').value = template.responseMapping.outputPath || '';
            document.getElementById('agent-error-path').value = template.responseMapping.errorPath || '';
            document.getElementById('agent-error-msg-path').value = template.responseMapping.errorMessagePath || '';
        }

        if (template.headers) {
            document.getElementById('agent-headers').value = JSON.stringify(template.headers, null, 2);
        } else {
            document.getElementById('agent-headers').value = '';
        }

        if (template.config) {
            document.getElementById('agent-config').value = JSON.stringify(template.config, null, 2);
        } else {
            document.getElementById('agent-config').value = '';
        }
    }

    updateFormByType();
}

/**
 * 根据类型更新表单显示
 */
function updateFormByType() {
    const type = document.getElementById('agent-type').value;
    const simpleConfig = document.getElementById('simple-config');
    const customConfig = document.getElementById('custom-config');

    if (type === 'openai' || type === 'claude') {
        simpleConfig.style.display = 'block';
        customConfig.style.display = 'none';

        if (type === 'openai' && !document.getElementById('agent-endpoint').value) {
            document.getElementById('agent-endpoint').value = 'https://api.openai.com/v1/chat/completions';
            document.getElementById('agent-model').value = 'gpt-3.5-turbo';
        } else if (type === 'claude' && !document.getElementById('agent-endpoint').value) {
            document.getElementById('agent-endpoint').value = 'https://api.anthropic.com/v1/messages';
            document.getElementById('agent-model').value = 'claude-3-sonnet-20240229';
        }
    } else {
        simpleConfig.style.display = 'none';
        customConfig.style.display = 'block';
    }
}

/**
 * 关闭创建弹窗
 */
function closeModal() {
    document.getElementById('agent-modal').style.display = 'none';
}

// ============ CRUD ============

/**
 * 编辑 Agent
 */
async function editAgent(id) {
    const agent = agents.find(a => a.id === id);
    if (!agent) return;

    document.getElementById('agent-modal').style.display = 'flex';
    document.getElementById('modal-title').textContent = '编辑 Agent';
    document.getElementById('agent-id').value = agent.id;

    document.getElementById('agent-name').value = agent.name || '';
    document.getElementById('agent-type').value = agent.type || 'openai';
    document.getElementById('agent-description').value = agent.description || '';
    document.getElementById('agent-endpoint').value = agent.endpoint || '';
    document.getElementById('agent-timeout').value = agent.timeout || 30000;

    if (agent.config) {
        document.getElementById('agent-api-key').value = agent.config.apiKey || '';
        document.getElementById('agent-model').value = agent.config.model || '';
        document.getElementById('agent-config').value = JSON.stringify(agent.config, null, 2);
    } else {
        document.getElementById('agent-api-key').value = '';
        document.getElementById('agent-model').value = '';
        document.getElementById('agent-config').value = '';
    }

    if (agent.headers) {
        document.getElementById('agent-headers').value = JSON.stringify(agent.headers, null, 2);
    } else {
        document.getElementById('agent-headers').value = '';
    }

    if (agent.requestMapping) {
        document.getElementById('agent-request-template').value = agent.requestMapping.template || '';
    } else {
        document.getElementById('agent-request-template').value = '';
    }

    if (agent.responseMapping) {
        document.getElementById('agent-output-path').value = agent.responseMapping.outputPath || '';
        document.getElementById('agent-error-path').value = agent.responseMapping.errorPath || '';
        document.getElementById('agent-error-msg-path').value = agent.responseMapping.errorMessagePath || '';
    } else {
        document.getElementById('agent-output-path').value = '';
        document.getElementById('agent-error-path').value = '';
        document.getElementById('agent-error-msg-path').value = '';
    }

    updateFormByType();
}

/**
 * 删除 Agent
 */
async function deleteAgent(id) {
    if (!await utils.confirm('确定要删除这个 Agent 配置吗？')) return;

    try {
        const res = await utils.api.delete(`/api/agents/${id}`);
        if (res.success) {
            utils.toast.success('Agent 已删除');
            await loadAgents();
        } else {
            utils.toast.error(res.error || '删除失败');
        }
    } catch (e) {
        utils.logError('Failed to delete agent', e);
        utils.toast.error('删除失败: ' + e.message);
    }
}

// ============ 表单提交 ============

document.addEventListener('DOMContentLoaded', function() {
    document.getElementById('agent-form').addEventListener('submit', async function(e) {
        e.preventDefault();

        const type = document.getElementById('agent-type').value;
        const agentData = {
            name: document.getElementById('agent-name').value.trim(),
            type: type,
            description: document.getElementById('agent-description').value.trim(),
            endpoint: document.getElementById('agent-endpoint').value.trim(),
            timeout: parseInt(document.getElementById('agent-timeout').value) || 30000
        };

        if (type === 'openai' || type === 'claude') {
            agentData.config = {
                apiKey: document.getElementById('agent-api-key').value.trim(),
                model: document.getElementById('agent-model').value.trim() || (type === 'openai' ? 'gpt-3.5-turbo' : 'claude-3-sonnet-20240229')
            };
        } else {
            try {
                const headersText = document.getElementById('agent-headers').value.trim();
                if (headersText) {
                    try { agentData.headers = JSON.parse(headersText); }
                    catch { utils.toast.error('请求头 JSON 格式错误'); return; }
                }

                const configText = document.getElementById('agent-config').value.trim();
                if (configText) {
                    try { agentData.config = JSON.parse(configText); }
                    catch { utils.toast.error('静态配置 JSON 格式错误'); return; }
                }

                agentData.requestMapping = {
                    template: document.getElementById('agent-request-template').value.trim()
                };
                agentData.responseMapping = {
                    outputPath: document.getElementById('agent-output-path').value.trim(),
                    errorPath: document.getElementById('agent-error-path').value.trim(),
                    errorMessagePath: document.getElementById('agent-error-msg-path').value.trim()
                };
            } catch (err) {
                utils.toast.error('JSON 格式错误: ' + err.message);
                return;
            }
        }

        const agentId = document.getElementById('agent-id').value;
        try {
            let res;
            if (agentId) {
                res = await utils.api.put(`/api/agents/${agentId}`, agentData);
            } else {
                res = await utils.api.post('/api/agents', agentData);
            }

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
        }
    });
});

// ============ 测试 ============

/**
 * 打开测试弹窗（已有 Agent）
 */
async function testAgent(id) {
    document.getElementById('test-modal').style.display = 'flex';
    document.getElementById('test-result').innerHTML = '';
    document.getElementById('test-modal').dataset.agentId = id;
}

/**
 * 打开测试弹窗（新建 Agent，先保存再测）
 */
function testAgentConfig() {
    document.getElementById('test-modal').style.display = 'flex';
    document.getElementById('test-result').innerHTML = '';
    document.getElementById('test-modal').dataset.agentId = '';
}

/**
 * 运行测试
 */
async function runTest() {
    const input = document.getElementById('test-input').value.trim();
    const agentId = document.getElementById('test-modal').dataset.agentId;
    const resultDiv = document.getElementById('test-result');

    if (!input) {
        resultDiv.innerHTML = `<div class="status-badge status-error">❌ 请输入测试内容</div>`;
        return;
    }

    resultDiv.innerHTML = '<div class="status-badge status-pending">⏳ 测试中...</div>';

    try {
        const res = await utils.api.post(`/api/agents/${agentId}/test`, { input });

        if (res.success) {
            resultDiv.innerHTML = `
                <div class="status-badge status-success">✅ 测试成功</div>
                <div style="margin-top: 12px;">
                    <div class="agent-info"><strong>输入:</strong></div>
                    <div class="json-preview">${utils.escapeHtml(input)}</div>
                    <div class="agent-info" style="margin-top: 12px;"><strong>输出:</strong></div>
                    <div class="json-preview">${utils.escapeHtml(res.output || '无输出')}</div>
                </div>
            `;
        } else {
            resultDiv.innerHTML = `<div class="status-badge status-error">❌ 测试失败: ${utils.escapeHtml(res.error || '未知错误')}</div>`;
        }
    } catch (e) {
        resultDiv.innerHTML = `<div class="status-badge status-error">❌ 请求失败: ${utils.escapeHtml(e.message)}</div>`;
    }
}

/**
 * 关闭测试弹窗
 */
function closeTestModal() {
    document.getElementById('test-modal').style.display = 'none';
}
