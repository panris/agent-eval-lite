function addTag() {
    const input = document.getElementById('tags-input');
    const tag = input.value.trim();
    if (tag && !currentTags.includes(tag)) {
        currentTags.push(tag);
        renderTags();
    }
    input.value = '';
}

function buildHistoryParams() {
    const params = new URLSearchParams({ sort: _sortDir, sortBy: _sortBy, page: _reportPage, size: _reportSize });
    const d = getDimensionFilters();
    if (d.project) params.set('project', d.project);
    if (d.module) params.set('module', d.module);
    if (d.function) params.set('function', d.function);
    const group = document.getElementById('history-group')?.value?.trim() || '';
    if (group) params.set('group', group);
    const search = document.getElementById('history-search')?.value?.trim() || '';
    if (search) params.set('keyword', search);
    const filterValue = document.getElementById('history-filter')?.value || '';
    if (filterValue === 'favorites') params.set('favorite', 'true');
    else if (filterValue === 'passed') params.set('status', 'passed');
    else if (filterValue === 'failed') params.set('status', 'failed');
    return params;
}

function clearDateFilter() {
    document.getElementById('history-date-start').value = '';
    document.getElementById('history-date-end').value = '';
    const groupSel = document.getElementById('history-group');
    if (groupSel) groupSel.value = '';
    const projSel = document.getElementById('history-project');
    if (projSel) projSel.value = '';
    const modSel = document.getElementById('history-module');
    if (modSel) modSel.value = '';
    const funcSel = document.getElementById('history-function');
    if (funcSel) funcSel.value = '';
    _reportPage = 1;
    loadHistory();
}

function closeAsyncTasksModal() {
    if (_asyncPollTimer) {
        clearInterval(_asyncPollTimer);
        _asyncPollTimer = null;
    }
    document.getElementById('async-tasks-modal').style.display = 'none';
}

function closeNoteModal() {
    document.getElementById('note-modal').style.display = 'none';
}

function closeTagsModal() {
    document.getElementById('tags-modal').style.display = 'none';
    document.getElementById('tags-input').value = '';
}

async function compareSelectedReports() {
    const checkboxes = document.querySelectorAll('.history-checkbox:checked');
    const ids = Array.from(checkboxes).map(cb => cb.value);
    if (ids.length < 2) {
        showToast('请至少选择 2 条报告进行对比', 'info');
        return;
    }
    if (ids.length > 5) {
        showToast('最多对比 5 条报告', 'info');
        return;
    }
    try {
        showToast('正在对比分析...', 'info');
        const response = await fetch(`/api/reports/compare?ids=${ids.join(",")}`);
        const data = await response.json();
        if (!data.success) {
            showToast(data.error || '对比失败', 'error');
            return;
        }

        const reports = data.comparison.reports;
        const stats = data.comparison.scoreStats;
        const reportIds = ids; // 保留原始 ID 顺序

        // 构建对比结果弹窗
        const overlay = document.createElement('div');
        overlay.style.cssText = `
            position: fixed; top: 0; left: 0; width: 100%; height: 100%;
            background: rgba(0,0,0,0.6); display: flex; justify-content: center; align-items: center;
            z-index: 9999; overflow-y: auto; padding: 20px;
        `;

        let rowsHtml = reports.map((r, i) => {
            const summary = r.summary || {};
            const ts = new Date(r.timestamp || Date.now()).toLocaleString('zh-CN');
            const passRate = (summary.pass_rate || 0).toFixed(1);
            const avgScore = (summary.average_score || 0).toFixed(2);
            const totalCases = (r.totalTestCases || summary.total_test_cases || 0);
            const execTime = (r.executionTimeMs || 0);
            return `<tr>
                <td>${i + 1}</td>
                <td><code>${reportIds[i] || '-'}</code></td>
                <td>${ts}</td>
                <td>${totalCases}</td>
                <td style="color: #4CAF50; font-weight: bold;">${passRate}%</td>
                <td style="color: #2196F3; font-weight: bold;">${avgScore}</td>
                <td>${execTime > 0 ? execTime + 'ms' : '-'}</td>
            </tr>`;
        }).join('');

        const scoreBarHtml = stats ? `<div style="margin: 16px 0; padding: 16px; background: var(--bg-card,#f5f7fa); border-radius: 8px; display: flex; gap: 16px; flex-wrap: wrap;">
            <div style="flex: 1; min-width: 120px;"><h4 style="margin: 0 0 8px 0;">📊 平均评分</h4><div style="display: flex; gap: 16px;"><span>最低 <strong style="color:#f44336;">${stats.min.toFixed(2)}</strong></span><span>均分 <strong style="color:#2196F3;">${stats.avg.toFixed(2)}</strong></span><span>最高 <strong style="color:#4CAF50;">${stats.max.toFixed(2)}</strong></span></div></div>
            ${data.comparison.passRateStats ? `<div style="flex: 1; min-width: 120px;"><h4 style="margin: 0 0 8px 0;">✅ 通过率</h4><div style="display: flex; gap: 16px;"><span>最低 <strong style="color:#f44336;">${data.comparison.passRateStats.min.toFixed(1)}%</strong></span><span>均分 <strong style="color:#2196F3;">${data.comparison.passRateStats.avg.toFixed(1)}%</strong></span><span>最高 <strong style="color:#4CAF50;">${data.comparison.passRateStats.max.toFixed(1)}%</strong></span></div></div>` : ''}
            ${data.comparison.execTimeStats ? `<div style="flex: 1; min-width: 120px;"><h4 style="margin: 0 0 8px 0;">⏱ 耗时</h4><div style="display: flex; gap: 16px;"><span>最快 <strong style="color:#4CAF50;">${data.comparison.execTimeStats.min}ms</strong></span><span>均耗 <strong style="color:#2196F3;">${Math.round(data.comparison.execTimeStats.avg)}ms</strong></span><span>最慢 <strong style="color:#f44336;">${data.comparison.execTimeStats.max}ms</strong></span></div></div>` : ''}
        </div>` : '';

        // 逐评分器明细
        const scorerStats = data.comparison.scorerStats;
        const reportIdList = reportIds; // 与 reports 数组一一对应
        let scorerBarHtml = '';
        if (scorerStats && Object.keys(scorerStats).length > 0) {
            const scorerNames = Object.keys(scorerStats);
            scorerBarHtml = `<div style="margin: 16px 0; padding: 16px; background: var(--bg-card,#f5f7fa); border-radius: 8px;">
                <h4 style="margin: 0 0 12px 0;">📋 逐评分器明细</h4>
                <div style="overflow-x: auto;">
                    <table style="width:100%;border-collapse:collapse;font-size:13px;">
                        <thead>
                            <tr style="background:#e8f0fe;">
                                <th style="padding:6px 10px;text-align:left;">评分器</th>
                                ${reportIdList.map((rid,i) => `<th style="padding:6px 10px;text-align:center;">报告 ${i+1}</th>`).join('')}
                                <th style="padding:6px 10px;text-align:center;">最低</th>
                                <th style="padding:6px 10px;text-align:center;">均分</th>
                                <th style="padding:6px 10px;text-align:center;">最高</th>
                            </tr>
                        </thead>
                        <tbody>
                            ${scorerNames.map(scorer => {
                                const info = scorerStats[scorer];
                                const sc = info.scores || {};
                                const st = info.stats || {};
                                // 计算各报告的平均分(保留2位)
                                const vals = Object.values(sc);
                                const minColor = vals.length ? (vals.reduce((a,b)=>a<b?a:b, vals[0]) === vals[0] ? '#f44336' : '#4CAF50') : '#999';
                                const maxColor = vals.length ? (vals.reduce((a,b)=>a>b?a:b, vals[0]) === vals[0] ? '#4CAF50' : '#f44336') : '#999';
                                return `<tr>
                                    <td style="padding:6px 10px;font-weight:600;">${scorer}</td>
                                    ${reportIdList.map((rid,i) => {
                                        const v = sc[rid];
                                        return `<td style="padding:6px 10px;text-align:center;color:#2196F3;font-weight:bold;">${v !== undefined ? v.toFixed(2) : '-'}</td>`;
                                    }).join('')}
                                    <td style="padding:6px 10px;text-align:center;color:${minColor};font-weight:bold;">${st.min !== undefined ? st.min.toFixed(2) : '-'}</td>
                                    <td style="padding:6px 10px;text-align:center;color:#2196F3;font-weight:bold;">${st.avg !== undefined ? st.avg.toFixed(2) : '-'}</td>
                                    <td style="padding:6px 10px;text-align:center;color:${maxColor};font-weight:bold;">${st.max !== undefined ? st.max.toFixed(2) : '-'}</td>
                                </tr>`;
                            }).join('')}
                        </tbody>
                    </table>
                </div>
            </div>`;
        }

        overlay.innerHTML = `
            <div style="
                background: var(--bg-main, white);
                border-radius: 16px;
                max-width: 900px;
                width: 100%;
                padding: 24px;
                box-shadow: 0 8px 32px rgba(0,0,0,0.3);
                max-height: 85vh;
                overflow-y: auto;
            ">
                <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px;">
                    <h2 style="margin: 0;">📊 报告对比</h2>
                    <button onclick="this.closest('[style*=\\'position: fixed\\']').remove()" style="
                        background: none; border: none; font-size: 24px; cursor: pointer; color: #999;
                    ">&times;</button>
                </div>

                <p style="color: var(--text-secondary, #666); margin-bottom: 16px;">
                    对比 ${reports.length} 份报告
                </p>

                ${scoreBarHtml}

                ${scorerBarHtml}

                <div style="overflow-x: auto;">
                    <table style="width: 100%; border-collapse: collapse;">
                        <thead>
                            <tr style="background: var(--bg-card, #f0f0f0);">
                                <th style="padding: 8px 12px; text-align: left;">#</th>
                                <th style="padding: 8px 12px; text-align: left;">报告 ID</th>
                                <th style="padding: 8px 12px; text-align: left;">时间</th>
                                <th style="padding: 8px 12px; text-align: left;">用例数</th>
                                <th style="padding: 8px 12px; text-align: left;">通过率</th>
                                <th style="padding: 8px 12px; text-align: left;">平均分</th>
                                <th style="padding: 8px 12px; text-align: left;">耗时</th>
                            </tr>
                        </thead>
                        <tbody>
                            ${rowsHtml}
                        </tbody>
                    </table>
                </div>

                <div style="display: flex; gap: 8px; justify-content: flex-end; margin-top: 20px;">
                    <button class="btn btn-primary" onclick="this.closest('[style*=\\'position: fixed\\']').remove()">
                        ✅ 关闭
                    </button>
                </div>
            </div>
        `;

        document.body.appendChild(overlay);
        showToast('对比完成', 'success');
    } catch (error) {
        logError('Compare failed:', error);
        showToast('对比失败', 'error');
    }
}

async function copyReport(reportId) {
    try {
        const response = await fetch(`/api/reports/${reportId}/copy`, { method: 'POST' });
        const data = await response.json();
        if (data.success) {
            showToast('报告已复制', 'success');
            loadHistory();
        } else {
            showToast(data.error || '复制失败', 'error');
        }
    } catch (error) {
        showToast('复制失败', 'error');
    }
}

async function deleteAllReports() {
    showConfirm('清空全部', '⚠️ 确定要清空所有评测历史记录吗?此操作不可恢复!', async () => {
        try {
            await fetch('/api/reports', {
                method: 'DELETE',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ action: 'clearAll' })
            });
            showToast('已清空全部报告', 'success');
            loadHistory();
        } catch (error) {
            logError('Failed to clear reports:', error);
            showToast('清空失败', 'error');
        }
    });
}

async function deleteSelectedReports() {
    const checkboxes = document.querySelectorAll('.history-checkbox:checked');
    const ids = Array.from(checkboxes).map(cb => cb.value);

    if (ids.length === 0) {
        showToast('请至少选择一条记录', 'info');
        return;
    }

    showConfirm('删除记录', `确定要删除选中的 ${ids.length} 条记录吗?`, async () => {
        try {
            for (const id of ids) {
                await fetch(`/api/reports/${id}`, { method: 'DELETE' });
            }
            showToast(`已删除 ${ids.length} 条记录`, 'success');
            loadHistory();
        } catch (error) {
            logError('Failed to delete reports:', error);
            showToast('删除失败', 'error');
        }
    });
}

function exportHistoryReport(reportId, format) {
    const url = `/api/reports/${reportId}/export?format=${format}`;
    window.open(url, '_blank');
}

function filterHistory(reports) {
    // 由筛选项触发(无参):服务端统一过滤,所有筛选项由 loadHistory 下发
    if (reports === undefined) {
        _reportPage = 1;
        loadHistory();
        return;
    }
    const tbody = document.getElementById('history-tbody');
    const total = window._reportTotal || 0;
    if (!reports || reports.length === 0) {
        tbody.innerHTML = `<tr><td colspan="10" style="text-align: center; color: var(--text-secondary);">${total === 0 ? '暂无评测记录' : '无匹配结果'}</td></tr>`;
        return;
    }
    tbody.innerHTML = reports.map(report => {
        const _id = utils.escapeHtml(report.id);
        const _note = utils.escapeHtml(report.note || '');
        const _tags = (report.tags || []).map(t => utils.escapeHtml(t));
        const _displayNote = _note.length > 15 ? _note.substring(0, 15) + '...' : _note;
        const _group = utils.escapeHtml(report.group || '');
        return `
        <tr>
            <td><input type="checkbox" class="history-checkbox" value="${_id}"></td>
            <td><code>${_id}</code></td>
            <td>${report.timestamp ? new Date(report.timestamp).toLocaleString() : '-'}</td>
            <td>
                <span class="badge ${(report.summary?.pass_rate || 0) >= 70 ? 'badge-success' : 'badge-danger'}">
                    ${report.summary ? report.summary.pass_rate.toFixed(1) : 0}%
                </span>
            </td>
            <td>${report.summary?.average_score ? report.summary.average_score.toFixed(2) : '-'}</td>
            <td>${_group ? `<span class="badge" style="background: #9333ea; padding: 2px 8px; font-size: 11px;">${_group}</span>` : '-'}</td>
            <td>
                <div style="display: flex; gap: 4px; flex-wrap: wrap;">
                    ${report.project ? `<span class="badge" style="background: #2563eb; padding: 2px 6px; font-size: 11px;">${utils.escapeHtml(report.project)}</span>` : ''}
                    ${report.module ? `<span class="badge" style="background: #7c3aed; padding: 2px 6px; font-size: 11px;">${utils.escapeHtml(report.module)}</span>` : ''}
                    ${report.function ? `<span class="badge" style="background: #db2777; padding: 2px 6px; font-size: 11px;">${utils.escapeHtml(report.function)}</span>` : ''}
                    ${(!report.project && !report.module && !report.function) ? '<span style="color: var(--text-secondary); font-size: 12px;">-</span>' : ''}
                </div>
            </td>
            <td>
                <div style="display: flex; gap: 4px; flex-wrap: wrap; align-items: center;">
                    ${_tags.map(tag => `<span class="badge" style="background: #667eea; padding: 2px 8px; font-size: 11px;">${tag}</span>`).join('')}
                    <button class="btn btn-sm" style="padding: 2px 6px; font-size: 11px;" onclick="openTagsModal('${_id}', ${JSON.stringify(report.tags || []).replace(/"/g, '&quot;')})">+标签</button>
                </div>
            </td>
            <td>
                <div style="max-width: 120px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;" title="${_note}">
                    ${_note ? _displayNote : '<span style="color: var(--text-secondary);">无</span>'}
                </div>
            </td>
            <td>
                <div style="display: flex; gap: 4px; flex-wrap: wrap;">
                    <button class="btn btn-sm" onclick="openNoteModal('${_id}', '${_note.replace(/'/g, "&#39;")}')">📝</button>
                    <button class="btn btn-sm ${report.favorite ? 'btn-warning' : ''}" onclick="toggleFavorite('${_id}')" title="收藏">${report.favorite ? '⭐' : '☆'}</button>
                    <button class="btn btn-sm" onclick="viewHistoryDetails('${_id}')">详情</button>
                    <button class="btn btn-sm" onclick="shareReport('${_id}')" title="分享">🔗</button>
                    <button class="btn btn-sm" onclick="copyReport('${_id}')">📋</button>
                </div>
            </td>
        </tr>
        `;
    }).join('');
}

function getDimensionFilters() {
    return {
        project: (document.getElementById('history-project')?.value || '').trim(),
        module: (document.getElementById('history-module')?.value || '').trim(),
        function: (document.getElementById('history-function')?.value || '').trim()
    };
}

function goToPage(n) {
    if (n < 1) n = 1;
    if (n > _reportTotalPages) n = _reportTotalPages;
    if (n === _reportPage) return;
    _reportPage = n;
    loadHistory();
}

async function loadHistory() {
    try {
        document.getElementById('history-loading').classList.add('show');

        // 确保分组下拉框已初始化
        await ensureGroups();

        _reportSize = parseInt(document.getElementById('page-size')?.value || '20');

        const params = buildHistoryParams();
        const dateStart = document.getElementById('history-date-start')?.value || '';
        const dateEnd = document.getElementById('history-date-end')?.value || '';
        if (dateStart) params.set('since', new Date(dateStart).getTime());
        if (dateEnd) {
            const until = new Date(dateEnd);
            until.setHours(23, 59, 59, 999);
            params.set('until', until.getTime());
        }

        // 趋势图用:全量(不分页)且复用相同过滤条件
        const allParams = new URLSearchParams(params);
        allParams.delete('page');
        allParams.delete('size');
        allParams.set('all', 'true');

        // 并行获取:分页数据 + 全量数据(趋势图用)
        const [pagedRes, allRes] = await Promise.all([
            fetch(`/api/reports?${params}`),
            fetch(`/api/reports?${allParams}`)
        ]);
    const [data, allData] = await Promise.all([pagedRes.json(), allRes.json()]);

        window._allReports = data.reports || [];
        window._reportTotal = data.total || 0;
        window._reportFiltered = data.filtered || 0;
        _reportTotalPages = data.totalPages || 1;
        if (_reportPage > _reportTotalPages) { _reportPage = _reportTotalPages; }
        updateHistoryStats();
        updatePaginationUI();

        filterHistory(window._allReports);
        renderTrendChart(allData.reports || []);

    } catch (error) {
        logError('Failed to load history:', error);
        showToast('加载评测历史失败', 'error');
    } finally {
        document.getElementById('history-loading').classList.remove('show');
    }
}

function onHistorySearch() {
    clearTimeout(_historySearchTimer);
    _historySearchTimer = setTimeout(() => { _reportPage = 1; loadHistory(); }, 300);
}

function openAsyncTasksModal() {
    document.getElementById('async-tasks-modal').style.display = 'flex';
    pollAsyncTasks();
}

function openNoteModal(reportId, existingNote) {
    document.getElementById('note-report-id').value = reportId;
    document.getElementById('note-input').value = existingNote || '';
    document.getElementById('note-modal').style.display = 'flex';
    document.getElementById('note-input').focus();
}

function openTagsModal(reportId, existingTags) {
    currentTags = [...(existingTags || [])];
    document.getElementById('tags-report-id').value = reportId;
    renderTags();
    document.getElementById('tags-modal').style.display = 'flex';
    document.getElementById('tags-input').focus();
}

async function pollAsyncTasks() {
    try {
        const resp = await fetch('/api/tasks');
        const tasks = await resp.json();
        renderAsyncTasks(tasks);

        const hasRunning = tasks.some(t => t.status === 'PENDING' || t.status === 'RUNNING');
        if (hasRunning && !_asyncPollTimer) {
            _asyncPollTimer = setInterval(pollAsyncTasks, 2000);
        } else if (!hasRunning && _asyncPollTimer) {
            clearInterval(_asyncPollTimer);
            _asyncPollTimer = null;
        }
    } catch (e) {
        console.error('轮询异步任务失败:', e);
    }
}

function removeTag(index) {
    currentTags.splice(index, 1);
    renderTags();
}

function renderAsyncTasks(tasks) {
    const container = document.getElementById('async-tasks-body');
    if (!tasks || tasks.length === 0) {
        container.innerHTML = '<div style="color:var(--text-secondary);text-align:center;padding:30px;">暂无异步任务</div>';
        return;
    }

    const statusBadge = (s) => {
        const map = {
            'PENDING': '<span style="background:#6c757d;color:white;padding:2px 8px;border-radius:10px;font-size:12px;">⏳ 等待中</span>',
            'RUNNING': '<span style="background:#007bff;color:white;padding:2px 8px;border-radius:10px;font-size:12px;">🔄 运行中</span>',
            'COMPLETED': '<span style="background:#28a745;color:white;padding:2px 8px;border-radius:10px;font-size:12px;">✅ 完成</span>',
            'FAILED': '<span style="background:#dc3545;color:white;padding:2px 8px;border-radius:10px;font-size:12px;">❌ 失败</span>',
            'TIMED_OUT': '<span style="background:#fd7e14;color:white;padding:2px 8px;border-radius:10px;font-size:12px;">⏱️ 超时</span>',
        };
        return map[s] || `<span>${s}</span>`;
    };

    const progress = (t) => {
        if (!t.totalCases || t.totalCases === 0) return '';
        const pct = Math.round((t.completedCases / t.totalCases) * 100);
        const color = t.status === 'COMPLETED' ? '#28a745' : t.status === 'FAILED' || t.status === 'TIMED_OUT' ? '#dc3545' : '#007bff';
        return `<div style="margin-top:6px;">
            <div style="display:flex;justify-content:space-between;font-size:12px;color:var(--text-secondary);margin-bottom:3px;">
                <span>${t.completedCases}/${t.totalCases} 用例</span><span>${pct}%</span>
            </div>
            <div style="height:6px;background:var(--border-color);border-radius:3px;overflow:hidden;">
                <div style="height:100%;width:${pct}%;background:${color};border-radius:3px;transition:width .3s;"></div>
            </div>
        </div>`;
    };

    const timeAgo = (ts) => {
        const diff = Math.round((Date.now() - ts) / 1000);
        if (diff < 60) return diff + '秒前';
        if (diff < 3600) return Math.round(diff / 60) + '分钟前';
        return Math.round(diff / 3600) + '小时前';
    };

    const rows = tasks.map(t => {
        // 通知完成
        if (t.status === 'COMPLETED' && t.reportId && !_asyncCompletedNotified.has(t.taskId)) {
            _asyncCompletedNotified.add(t.taskId);
            showToast(`异步评测完成！通过率将更新至历史记录`, 'success');
            loadHistory && loadHistory();
        }

        const viewBtn = t.reportId
            ? `<button class="btn btn-success btn-sm" style="padding:2px 10px;font-size:12px;" onclick="closeAsyncTasksModal();viewHistoryDetails('${t.reportId}')">查看报告</button>`
            : '';
        const errInfo = t.error ? `<div style="font-size:11px;color:#dc3545;margin-top:4px;">${utils.escapeHtml(t.error)}</div>` : '';

        return `<div style="padding:12px;border-bottom:1px solid var(--border-color);">
            <div style="display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:8px;">
                <div>
                    <div style="font-size:13px;font-weight:600;">任务 ${t.taskId.slice(-8)}</div>
                    <div style="font-size:12px;color:var(--text-secondary);margin-top:2px;">${timeAgo(t.createdAt)}</div>
                </div>
                <div style="display:flex;align-items:center;gap:8px;flex-wrap:wrap;">
                    ${statusBadge(t.status)}${viewBtn}
                </div>
            </div>
            ${progress(t)}${errInfo}
        </div>`;
    }).join('');

    container.innerHTML = rows;
}

function renderTags() {
    const container = document.getElementById('tags-list');
    container.innerHTML = currentTags.map((tag, i) => `
        <span style="background: #667eea; color: white; padding: 4px 10px; border-radius: 12px; font-size: 12px; display: flex; align-items: center; gap: 6px;">
            ${utils.escapeHtml(tag)}
            <span style="cursor: pointer; font-weight: bold;" onclick="removeTag(${i})">×</span>
        </span>
    `).join('');
}

async function runAsyncSelectedEvaluation() {
    const checked = document.querySelectorAll('#cases-tbody .case-checkbox:checked');
    const caseIds = Array.from(checked).map(cb => cb.value);

    if (caseIds.length === 0) {
        showToast('请先选择测试用例', 'error');
        return;
    }

    const response = await fetch('/api/evaluate/async', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
            caseIds,
            metrics: ['correctness', 'safety', 'response_time', 'bleu', 'rouge', 'similarity'],
            agentType: 'demo'
        })
    });

    const data = await response.json();
    if (data.success) {
        showToast(`异步任务已提交: ${data.taskId.slice(-8)}，可在任务监控中查看进度`, 'success');
        openAsyncTasksModal();
    } else {
        showToast(data.error || '提交异步任务失败', 'error');
    }
}

async function saveNote() {
    const reportId = document.getElementById('note-report-id').value;
    const note = document.getElementById('note-input').value;
    try {
        const response = await fetch(`/api/reports/${reportId}/note`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ note })
        });
        const data = await response.json();
        if (data.success) {
            showToast('备注已保存', 'success');
            closeNoteModal();
            loadHistory();
        }
    } catch (e) {
        showToast('保存失败', 'error');
    }
}

async function saveTags() {
    const reportId = document.getElementById('tags-report-id').value;
    try {
        const response = await fetch(`/api/reports/${reportId}/tags`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ tags: currentTags })
        });
        const data = await response.json();
        if (data.success) {
            showToast('标签已保存', 'success');
            closeTagsModal();
            loadHistory();
        }
    } catch (e) {
        showToast('保存失败', 'error');
    }
}

async function shareReport(reportId) {
    try {
        const response = await fetch(`/api/reports/${reportId}/share`, { method: 'POST' });
        const data = await response.json();
        if (data.success) {
            const shareUrl = window.location.origin + data.url;
            // 复制链接到剪贴板
            await navigator.clipboard.writeText(shareUrl);
            showToast(`分享链接已复制到剪贴板: ${shareUrl}`, 'success');
        } else {
            showToast(data.error || '分享失败', 'error');
        }
    } catch (error) {
        showToast('分享失败', 'error');
    }
}

function toggleAllHistory() {
    const checked = document.getElementById('select-all-history').checked;
    document.querySelectorAll('.history-checkbox').forEach(cb => {
        cb.checked = checked;
    });
}

async function toggleFavorite(reportId) {
    try {
        const response = await fetch(`/api/reports/${reportId}/favorite`, { method: 'POST' });
        const data = await response.json();
        if (data.success) {
            showToast(data.favorite ? '已收藏' : '已取消收藏', 'success');
            loadHistory();
        }
    } catch (error) {
        showToast('操作失败', 'error');
    }
}

function toggleSort() {
    // 循环: time_desc → time_asc → score_desc → score_asc → time_desc
    if (_sortBy === 'time' && _sortDir === 'desc') { _sortBy = 'time'; _sortDir = 'asc'; }
    else if (_sortBy === 'time' && _sortDir === 'asc') { _sortBy = 'score'; _sortDir = 'desc'; }
    else if (_sortBy === 'score' && _sortDir === 'desc') { _sortBy = 'score'; _sortDir = 'asc'; }
    else { _sortBy = 'time'; _sortDir = 'desc'; }
    const btn = document.getElementById('sort-btn');
    const labels = {
        'time_desc': '↕️ 最新优先',
        'time_asc':  '↕️ 最早优先',
        'score_desc':'↕️ 最高分',
        'score_asc': '↕️ 最低分'
    };
    btn.textContent = labels[_sortBy + '_' + _sortDir] || '↕️ 最新优先';
    loadHistory();
}

function updateHistoryStats() {
    const total = window._reportTotal || 0;
    const filtered = window._reportFiltered || 0;
    const el = document.getElementById('history-stats');
    if (!el) return;
    el.textContent = total === 0 ? '' : `共 ${total} 条报告${filtered < total ? `(已过滤至 ${filtered} 条)` : ''}  ·  按${_sortBy === 'score' ? '分数' : '时间'}${_sortDir === 'desc' ? '倒序' : '正序'}`;
}

function updatePaginationUI() {
    const totalPages = _reportTotalPages || 1;
    const page = _reportPage || 1;
    document.getElementById('page-info').textContent = `第 ${page} / ${totalPages} 页`;
    document.getElementById('page-prev').disabled = page <= 1;
    document.getElementById('page-next').disabled = page >= totalPages;
}

async function viewHistoryDetails(reportId) {
    try {
        const response = await fetch(`/api/reports/${reportId}`);
        const report = await response.json();

        window.lastEvaluation = report;
        window.lastReportId = reportId;

        // 渲染图表
        if (report.evaluations) {
            renderCharts(report.evaluations);
        }

        // 显示通过率
        if (report.success && report.summary) {
            document.getElementById('pass-rate').textContent = report.summary.pass_rate.toFixed(1) + '%';
            document.getElementById('eval-result').style.display = 'block';
        }

        showEvalDetails();
        switchTab('evaluate', null);  // 无 event 对象,跳过高亮 tab  // 从此处调用时无 event,但已修复 switchTab 支持无 target 调用

    } catch (error) {
        logError('Failed to load report details:', error);
        showToast('加载报告详情失败', 'error');
    }
}
