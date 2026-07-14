            const html = document.documentElement;
            const currentTheme = html.getAttribute('data-theme');
            const newTheme = currentTheme === 'dark' ? 'light' : 'dark';

            html.setAttribute('data-theme', newTheme);
            localStorage.setItem('theme', newTheme);

            // Update button icon
            document.getElementById('theme-btn').textContent = newTheme === 'dark' ? '☀️' : '🌙';
        }

        // Load saved theme
        function loadTheme() {
            const savedTheme = localStorage.getItem('theme') || 'light';
            document.documentElement.setAttribute('data-theme', savedTheme);
            document.getElementById('theme-btn').textContent = savedTheme === 'dark' ? '☀️' : '🌙';
        }

        // Toast Notification System
        function showToast(message, type = 'info', duration = 3000) {
            let container = document.querySelector('.toast-container');
            if (!container) {
                container = document.createElement('div');
                container.className = 'toast-container';
                document.body.appendChild(container);
            }

            const toast = document.createElement('div');
            toast.className = `toast ${type}`;
            toast.innerHTML = `
                <span>${type === 'success' ? '✅' : type === 'error' ? '❌' : 'i️'}</span>
                <span>${utils.escapeHtml(message)}</span>
            `;

            container.appendChild(toast);

            setTimeout(() => {
                toast.style.animation = 'slideOut 0.3s ease';
                setTimeout(() => toast.remove(), 300);
            }, duration);
        }

        // Confirm Dialog System
        function showConfirm(title, message, onConfirm) {
            const overlay = document.getElementById('confirm-overlay');
            document.getElementById('confirm-title').textContent = title;
            document.getElementById('confirm-message').textContent = message;

            document.getElementById('confirm-yes').onclick = () => {
                overlay.classList.remove('show');
                onConfirm();
            };

            document.getElementById('confirm-no').onclick = () => {
                overlay.classList.remove('show');
            };

            overlay.classList.add('show');
        }

        let testCases = [];
        let groups = [];

        // 切换标签页
        function switchTab(tab, evt) {
            document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));
            document.querySelectorAll('.card').forEach(c => c.classList.remove('show'));

            // 支持显式传入 event,或使用全局 event(onclick 调用时)
            const target = evt?.target || (typeof event !== 'undefined' ? event.target : null);
            if (target) target.classList.add('active');
            document.getElementById(`${tab}-card`).classList.add('show');

            if (tab === 'cases') loadTestCases();
            else if (tab === 'groups') loadGroups();
            else if (tab === 'evaluate') loadTestCases();
            else if (tab === 'history') loadHistory();
        }

        // Debounce function for search optimization
        function debounce(func, wait) {
            let timeout;
            return function executedFunction(...args) {
                const later = () => {
                    clearTimeout(timeout);
                    func(...args);
                };
                clearTimeout(timeout);
                timeout = setTimeout(later, wait);
            };
        }

        // 过滤测试用例
        const filterTestCases = debounce(function() {
            tcKeyword = document.getElementById('case-search').value.trim();
            tcPage = 1;
            loadTestCases();
        }, 300);

        let trendChart = null;

        // 加载评测历史
        let _sortBy = 'time';   // 'time' | 'score'
        let _sortDir = 'desc';  // 'desc' | 'asc'
        let _reportPage = 1;
        let _reportSize = 20;
        let _reportTotalPages = 1;

        function getDimensionFilters() {
            return {
                project: (document.getElementById('history-project')?.value || '').trim(),
                module: (document.getElementById('history-module')?.value || '').trim(),
                function: (document.getElementById('history-function')?.value || '').trim()
            };
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

        function goToPage(n) {
            if (n < 1) n = 1;
            if (n > _reportTotalPages) n = _reportTotalPages;
            if (n === _reportPage) return;
            _reportPage = n;
            loadHistory();
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
                    <td>${report.summary ? report.summary.average_score.toFixed(2) : 0}</td>
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

        let _historySearchTimer = null;
        function onHistorySearch() {
            clearTimeout(_historySearchTimer);
            _historySearchTimer = setTimeout(() => { _reportPage = 1; loadHistory(); }, 300);
        }

        // 收藏/取消收藏
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


// 对比选中报告(增强版:可视化对比弹窗)
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


        // 复制报告
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

        // 分享报告
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

        // 全选/取消全选历史记录
        function toggleAllHistory() {
            const checked = document.getElementById('select-all-history').checked;
            document.querySelectorAll('.history-checkbox').forEach(cb => {
                cb.checked = checked;
            });
        }

        // 删除选中的历史记录
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

        // 清空全部报告
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

        // 查看历史报告详情
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

        // 导出历史报告
        function exportHistoryReport(reportId, format) {
            const url = `/api/reports/${reportId}/export?format=${format}`;
            window.open(url, '_blank');
        }

        // ============ 分页状态 ============
        let tcPage = 1;
        let tcPageSize = 20;
        let tcTotal = 0;
        let tcTotalPages = 0;
        let tcKeyword = '';
        let tcGroupId = '';

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

        function tcPrevPage() {
            if (tcPage > 1) { tcPage--; loadTestCases(); }
        }

        function tcNextPage() {
            if (tcPage < tcTotalPages) { tcPage++; loadTestCases(); }
        }

        function tcGoToPage(p) {
            tcPageSize = parseInt(document.getElementById('cases-page-size').value, 10);
            tcPage = p;
            loadTestCases();
        }

        // ============ 加载测试用例 ============
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

        // 创建测试用例
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

        // 删除测试用例
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

        // 确保分组列表已加载(history 下拉框需要)
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

        // 加载分组
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

        // 查看分组中的用例
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

        // 显示批量添加到分组模态框
        function showAddToGroupModal(groupId) {
            window.currentGroupId = groupId;
            loadTestCasesForModal();
            document.getElementById('add-to-group-modal').style.display = 'block';
        }

        // 加载用例到模态框
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

        // 批量添加选中的用例到分组
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

        // 关闭模态框
        function closeAddToGroupModal() {
            document.getElementById('add-to-group-modal').style.display = 'none';
        }

        // 全选/取消全选模态框中的用例
        function toggleModalSelectAll() {
            const checked = document.getElementById('modal-select-all').checked;
            document.querySelectorAll('#modal-cases-tbody input[type="checkbox"]').forEach(cb => {
                cb.checked = checked;
            });
        }

        // 创建分组
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

        // 删除分组
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

        // 运行评测 (选中用例)
        async function runEvaluation() {
            const checkboxes = document.querySelectorAll('.eval-checkbox:checked');
            const caseIds = Array.from(checkboxes).map(cb => cb.value);

            if (caseIds.length === 0) {
                showToast('请至少选择一个测试用例', 'error');
                return;
            }

            document.getElementById('eval-loading').classList.add('show');
            document.getElementById('eval-result').style.display = 'none';
            document.getElementById('eval-details').style.display = 'none';

            try {
                const response = await fetch('/api/evaluate/cases', {
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
                    const passRate = data.summary.pass_rate.toFixed(1);
                    document.getElementById('pass-rate').textContent = passRate + '%';
                    document.getElementById('eval-result').style.display = 'block';
                    showToast(`评测完成!通过率: ${passRate}%`, 'success');

                    window.lastEvaluation = data;
                    window.lastReportId = data.reportId;
                }
            } catch (error) {
                logError('Evaluation failed:', error);
                showToast('评测失败', 'error');
            } finally {
                document.getElementById('eval-loading').classList.remove('show');
            }
        }

        // 显示评测详情
        // Global chart instances
        let passRateChart = null;
        let scoreChart = null;
        let chartRenderPending = false;

        function renderCharts(evaluations) {
            // 防止重复渲染
            if (chartRenderPending) return;
            chartRenderPending = true;

            requestAnimationFrame(() => {
                // Get theme colors
                const isDark = document.documentElement.getAttribute('data-theme') === 'dark';
                const textColor = isDark ? '#e0e0e0' : '#333';
                const gridColor = isDark ? '#3d3d5c' : '#e0e0e0';

                // Pass Rate Pie Chart
                const passed = evaluations.filter(e => e.passed).length;
                const failed = evaluations.length - passed;

                if (passRateChart) passRateChart.destroy();
                passRateChart = new Chart(document.getElementById('passRateChart'), {
                    type: 'pie',
                    data: {
                        labels: ['通过', '失败'],
                        datasets: [{
                            data: [passed, failed],
                            backgroundColor: ['#28a745', '#dc3545'],
                            borderWidth: 2,
                            borderColor: isDark ? '#252542' : 'white'
                        }]
                    },
                    options: {
                        responsive: true,
                        maintainAspectRatio: false,
                        animation: { duration: 300 },  // 减少动画时间
                        plugins: {
                            legend: {
                                position: 'bottom',
                                labels: { color: textColor }
                            }
                        }
                    }
                });

                // Score Distribution Bar Chart
                const scores = evaluations.map(e => ({
                    score: e.overallScore || 0,
                    name: e.testCaseId ? e.testCaseId.substring(0, 10) : 'N/A'
                }));

                if (scoreChart) scoreChart.destroy();
                scoreChart = new Chart(document.getElementById('scoreChart'), {
                    type: 'bar',
                    data: {
                        labels: scores.map(s => s.name),
                        datasets: [{
                            label: '评分',
                            data: scores.map(s => s.score),
                            backgroundColor: scores.map(s => s.score >= 0.7 ? '#28a745' : s.score >= 0.4 ? '#ffc107' : '#dc3545'),
                            borderRadius: 4
                        }]
                    },
                    options: {
                        responsive: true,
                        maintainAspectRatio: false,
                        animation: { duration: 300 },  // 减少动画时间
                        scales: {
                            y: {
                                beginAtZero: true,
                                max: 1,
                                ticks: { color: textColor },
                                grid: { color: gridColor }
                            },
                            x: {
                                ticks: { color: textColor },
                                grid: { color: gridColor }
                            }
                        },
                        plugins: {
                            legend: { display: false }
                        }
                    }
                });

                chartRenderPending = false;
            });
        }

        // 页面隐藏时销毁图表节省资源
        document.addEventListener('visibilitychange', () => {
            if (document.hidden) {
                if (passRateChart) { passRateChart.destroy(); passRateChart = null; }
                if (scoreChart) { scoreChart.destroy(); scoreChart = null; }
            }
        });

        // 渲染趋势图表
        function renderTrendChart(reportsOverride) {
            const reports = reportsOverride || window._allReports || [];
            if (reports.length < 2) {
                // 数据不足时显示提示
                const ctx = document.getElementById('trend-chart');
                if (ctx && ctx.parentElement) {
                    ctx.parentElement.innerHTML = '<div style="text-align: center; color: var(--text-secondary); padding: 40px;">📊 评测趋势图需要至少 2 条历史记录</div>';
                }
                return;
            }

            // 按时间排序
            const sorted = [...reports].sort((a, b) => (a.timestamp || 0) - (b.timestamp || 0));

            // 获取最近 10 条记录
            const recent = sorted.slice(-10);

            const labels = recent.map(r => {
                const d = new Date(r.timestamp);
                return `${d.getMonth()+1}/${d.getDate()} ${d.getHours()}:${String(d.getMinutes()).padStart(2, '0')}`;
            });
            const passRates = recent.map(r => r.summary?.pass_rate || 0);
            const avgScores = recent.map(r => r.summary?.average_score || 0);

            const isDark = document.documentElement.getAttribute('data-theme') === 'dark';
            const textColor = isDark ? '#e0e0e0' : '#333';
            const gridColor = isDark ? '#3d3d5c' : '#e0e0e0';

            if (trendChart) trendChart.destroy();

            const ctx = document.getElementById('trend-chart');
            if (!ctx) return;

            trendChart = new Chart(ctx, {
                type: 'line',
                data: {
                    labels: labels,
                    datasets: [
                        {
                            label: '通过率 (%)',
                            data: passRates,
                            borderColor: '#667eea',
                            backgroundColor: 'rgba(102, 126, 234, 0.1)',
                            fill: true,
                            tension: 0.3,
                            yAxisID: 'y'
                        },
                        {
                            label: '平均分',
                            data: avgScores.map(s => s * 100),
                            borderColor: '#28a745',
                            backgroundColor: 'rgba(40, 167, 69, 0.1)',
                            fill: true,
                            tension: 0.3,
                            yAxisID: 'y1'
                        }
                    ]
                },
                options: {
                    responsive: true,
                    maintainAspectRatio: false,
                    animation: { duration: 500 },
                    interaction: {
                        mode: 'index',
                        intersect: false
                    },
                    plugins: {
                        legend: {
                            position: 'top',
                            labels: { color: textColor }
                        },
                        tooltip: {
                            callbacks: {
                                label: function(context) {
                                    return context.dataset.label + ': ' + context.raw.toFixed(1) + (context.datasetIndex === 0 ? '%' : '');
                                }
                            }
                        }
                    },
                    scales: {
                        y: {
                            type: 'linear',
                            display: true,
                            position: 'left',
                            min: 0,
                            max: 100,
                            title: { display: true, text: '通过率 (%)', color: textColor },
                            ticks: { color: textColor },
                            grid: { color: gridColor }
                        },
                        y1: {
                            type: 'linear',
                            display: true,
                            position: 'right',
                            min: 0,
                            max: 100,
                            title: { display: true, text: '平均分', color: textColor },
                            ticks: { color: textColor },
                            grid: { drawOnChartArea: false }
                        },
                        x: {
                            ticks: { color: textColor },
                            grid: { color: gridColor }
                        }
                    }
                }
            });
        }

        function showEvalDetails() {
            const detailsDiv = document.getElementById('eval-details');
            const tbody = document.getElementById('details-tbody');

            if (!window.lastEvaluation || !window.lastEvaluation.evaluations) {
                showToast('没有评测详情', 'info');
                return;
            }

            // Render charts
            renderCharts(window.lastEvaluation.evaluations);
            renderReportMeta(window.lastEvaluation);

            const evaluations = window.lastEvaluation.evaluations;
            tbody.innerHTML = evaluations.map(ev => {
                const testCase = testCases.find(tc => tc.id === ev.testCaseId) || {};
                const _name = utils.escapeHtml(testCase.name || ev.testCaseId);
                const _input = utils.escapeHtml(testCase.input);
                const _expected = utils.escapeHtml(testCase.expected);
                const scorerNames = Object.keys(ev.scorerResults || {}).join(', ');
                const scorerScores = Object.values(ev.scorerResults || {}).map(sr => sr.score.toFixed(2)).join(', ');

                return `
                    <tr>
                        <td>${_name}</td>
                        <td>${_input || '-'}</td>
                        <td>${_expected || '-'}</td>
                        <td>${scorerScores || '-'} (${scorerNames || '-'})</td>
                        <td>
                            <span class="badge ${ev.passed ? 'badge-success' : 'badge-danger'}">
                                ${ev.passed ? '通过' : '失败'}
                            </span>
                        </td>
                    </tr>
                `;
            }).join('');

            detailsDiv.style.display = 'block';
        }

        function renderReportMeta(report) {
            const metaDiv = document.getElementById('report-meta');
            const groupSpan = document.getElementById('report-meta-group');
            const dimsSpan = document.getElementById('report-meta-dims');
            if (!metaDiv || !report) return;

            const group = report.group;
            const project = report.project;
            const module = report.module;
            const func = report.function;

            const hasGroup = group && String(group).trim() !== '';
            const hasDims = (project && String(project).trim() !== '') ||
                            (module && String(module).trim() !== '') ||
                            (func && String(func).trim() !== '');

            if (!hasGroup && !hasDims) {
                metaDiv.style.display = 'none';
                return;
            }

            groupSpan.innerHTML = hasGroup
                ? `<span class="badge badge-info">${utils.escapeHtml(String(group))}</span>`
                : '';
            groupSpan.style.display = hasGroup ? 'inline' : 'none';

            const badges = [];
            if (project && String(project).trim() !== '') {
                badges.push(`<span class="badge" style="background: #2563eb;">${utils.escapeHtml(String(project))}</span>`);
            }
            if (module && String(module).trim() !== '') {
                badges.push(`<span class="badge" style="background: #7c3aed;">${utils.escapeHtml(String(module))}</span>`);
            }
            if (func && String(func).trim() !== '') {
                badges.push(`<span class="badge" style="background: #db2777;">${utils.escapeHtml(String(func))}</span>`);
            }
            dimsSpan.innerHTML = badges.join(' ');
            dimsSpan.style.display = badges.length ? 'inline' : 'none';

            metaDiv.style.display = 'block';
        }

        // 导出报告
        function exportReport(format) {
            if (!window.lastReportId) {
                showToast('没有可导出的评测报告', 'info');
                return;
            }

            const url = `/api/reports/${window.lastReportId}/export?format=${format}`;
            window.open(url, '_blank');
        }

        // 导出 PDF 报告
        // 导出 PDF 报告(服务器端生成,支持中文)
        async function exportReportPdf() {
            if (!window.lastReportId) {
                showToast('没有可导出的评测报告', 'info');
                return;
            }

            try {
                showToast('正在生成 PDF...', 'info');
                const response = await fetch(`/api/reports/${window.lastReportId}/export/pdf`);

                if (!response.ok) throw new Error('PDF 生成失败');

                const blob = await response.blob();
                const url = URL.createObjectURL(blob);
                const a = document.createElement('a');
                a.href = url;
                a.download = response.headers.get('Content-Disposition')?.match(/filename="(.+)"/)?.[1] || 'report.pdf';
                a.click();
                URL.revokeObjectURL(url);
                showToast('PDF 报告已下载', 'success');

            } catch (error) {
                logError('PDF export failed:', error);
                showToast('PDF 导出失败', 'error');
            }
        }

        // 按分组评测
        async function runGroupEvaluation() {
            const groupId = document.getElementById('eval-group').value;
            if (!groupId) {
                showToast('请选择一个分组', 'info');
                return;
            }

            document.getElementById('eval-loading').classList.add('show');
            document.getElementById('eval-result').style.display = 'none';
            document.getElementById('eval-details').style.display = 'none';

            try {
                const response = await fetch(`/api/evaluate/group/${groupId}`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        metrics: ['correctness', 'safety', 'response_time', 'bleu', 'rouge', 'similarity'],
                        agentType: 'demo'
                    })
                });

                const data = await response.json();
                if (data.success) {
                    const passRate = data.summary.pass_rate.toFixed(1);
                    document.getElementById('pass-rate').textContent = passRate + '%';
                    document.getElementById('eval-result').style.display = 'block';
                    showToast(`评测完成!通过率: ${passRate}%`, 'success');

                    window.lastEvaluation = data;
                    window.lastReportId = data.reportId;
                }
            } catch (error) {
                logError('Evaluation failed:', error);
                showToast('评测失败', 'error');
            } finally {
                document.getElementById('eval-loading').classList.remove('show');
            }
        }

        async function runDimensionEvaluation() {
            const project = document.getElementById('eval-project').value;
            const moduleDim = document.getElementById('eval-module').value;
            const functionDim = document.getElementById('eval-function').value;

            if (!project && !moduleDim && !functionDim) {
                showToast('请至少选择一个维度(项目/模块/功能)', 'info');
                return;
            }

            document.getElementById('eval-loading').classList.add('show');
            document.getElementById('eval-result').style.display = 'none';
            document.getElementById('eval-details').style.display = 'none';

            try {
                const response = await fetch('/api/evaluate/dimensions', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        metrics: ['correctness', 'safety', 'response_time', 'bleu', 'rouge', 'similarity'],
                        agentType: 'demo',
                        project: project || null,
                        module: moduleDim || null,
                        function: functionDim || null
                    })
                });

                const data = await response.json();
                if (data.success) {
                    const summary = data.summary || {};
                    const passRate = (summary.pass_rate != null ? summary.pass_rate : 0).toFixed(1);
                    document.getElementById('pass-rate').textContent = passRate + '%';
                    document.getElementById('eval-result').style.display = 'block';
                    showToast(`按三维评测完成!通过率: ${passRate}%`, 'success');
                    window.lastEvaluation = data;
                    window.lastReportId = data.reportId;
                } else {
                    showToast(data.error || '评测失败', 'error');
                }
            } catch (error) {
                logError('Dimension evaluation failed:', error);
                showToast('评测失败', 'error');
            } finally {
                document.getElementById('eval-loading').classList.remove('show');
            }
        }

        // 加载分组用例
        function loadGroupCases() {
            const groupId = document.getElementById('eval-group').value;
            if (!groupId) {
                loadTestCases();
                return;
            }

            const group = groups.find(g => g.id === groupId);
            if (group && group.testCaseIds) {
                document.querySelectorAll('.eval-checkbox').forEach(cb => {
                    cb.checked = group.testCaseIds.includes(cb.value);
                });
            }
        }

        // 页面加载时初始化
        window.onload = function() {
            // Complete loading bar
            const loadingBar = document.getElementById("loading-bar");
            if (loadingBar) {
                loadingBar.classList.remove("loading");
                loadingBar.classList.add("loaded");
                setTimeout(() => loadingBar.remove(), 500);
            }
            loadTheme();
            loadTestCases();
            loadGroups();
            loadDimensions();
        };

        // 加载三维分组去重值并填充下拉框 / datalist
        async function loadDimensions() {
            try {
                const res = await fetch('/api/testcases/dimensions');
                const data = await res.json();
                if (!data.success) return;
                const projects = data.projects || [];
                const modules = data.modules || [];
                const functions = data.functions || [];

                // datalist(创建表单 + 评测下拉联想)
                fillDataList('project-list', projects);
                fillDataList('module-list', modules);
                fillDataList('function-list', functions);

                // 评测下拉
                fillSelect('eval-project', projects, '项目(任意)');
                fillSelect('eval-module', modules, '模块(任意)');
                fillSelect('eval-function', functions, '功能(任意)');

                // 历史筛选下拉
                fillSelect('history-project', projects, '全部项目');
                fillSelect('history-module', modules, '全部模块');
                fillSelect('history-function', functions, '全部功能');
            } catch (error) {
                logError('Failed to load dimensions:', error);
            }
        }

        function fillDataList(id, values) {
            const el = document.getElementById(id);
            if (!el) return;
            el.innerHTML = values.map(v => `<option value="${utils.escapeHtml(v)}">`).join('');
        }

        function fillSelect(id, values, allLabel) {
            const el = document.getElementById(id);
            if (!el) return;
            const current = el.value;
            el.innerHTML = `<option value="">${utils.escapeHtml(allLabel)}</option>` +
                values.map(v => `<option value="${utils.escapeHtml(v)}">${utils.escapeHtml(v)}</option>`).join('');
            if (values.includes(current)) el.value = current;
        }


        // 批量导入相关函数
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

        // 导出 Excel
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

        // 打开导入 Excel 模态框
        function openImportExcelModal() {
            document.getElementById('import-excel-modal').style.display = 'block';
            document.getElementById('excel-file-input').value = '';
        }

        // 关闭导入 Excel 模态框
        function closeImportExcelModal() {
            document.getElementById('import-excel-modal').style.display = 'none';
        }

        // 导入 Excel 文件
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

        function openImportModal() {
            document.getElementById('import-modal').style.display = 'block';
        }

        function closeImportModal() {
            document.getElementById('import-modal').style.display = 'none';
            document.getElementById('import-data').value = '';
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
                    showToast('没有找到有效数据', 'error');
                    return;
                }

                // 逐个创建
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

        // ===== 测试用例标签功能 =====
        let currentCaseTags = [];
        let currentCaseId = '';

        function openCaseTagsModal(caseId, existingTags) {
            currentCaseId = caseId;
            currentCaseTags = [...(existingTags || [])];
            document.getElementById('case-tags-report-id').value = caseId;
            renderCaseTags();
            document.getElementById('case-tags-modal').style.display = 'flex';
            document.getElementById('case-tags-input').focus();
        }

        function closeCaseTagsModal() {
            document.getElementById('case-tags-modal').style.display = 'none';
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

        function addCaseTag() {
            const input = document.getElementById('case-tags-input');
            const tag = input.value.trim();
            if (tag && !currentCaseTags.includes(tag)) {
                currentCaseTags.push(tag);
                input.value = '';
                renderCaseTags();
            }
        }

        function removeCaseTag(index) {
            currentCaseTags.splice(index, 1);
            renderCaseTags();
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

        // ===== 报告标签功能 =====
        let currentTags = [];

        function openTagsModal(reportId, existingTags) {
            currentTags = [...(existingTags || [])];
            document.getElementById('tags-report-id').value = reportId;
            renderTags();
            document.getElementById('tags-modal').style.display = 'flex';
            document.getElementById('tags-input').focus();
        }

        function closeTagsModal() {
            document.getElementById('tags-modal').style.display = 'none';
            document.getElementById('tags-input').value = '';
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

        function addTag() {
            const input = document.getElementById('tags-input');
            const tag = input.value.trim();
            if (tag && !currentTags.includes(tag)) {
                currentTags.push(tag);
                renderTags();
            }
            input.value = '';
        }

        function removeTag(index) {
            currentTags.splice(index, 1);
            renderTags();
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

        // ===== 报告备注功能 =====
        function openNoteModal(reportId, existingNote) {
            document.getElementById('note-report-id').value = reportId;
            document.getElementById('note-input').value = existingNote || '';
            document.getElementById('note-modal').style.display = 'flex';
            document.getElementById('note-input').focus();
        }

        function closeNoteModal() {
            document.getElementById('note-modal').style.display = 'none';
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

        // ===== 报告复制功能 =====
        async function copyReport(reportId) {
            try {
                const response = await fetch(`/api/reports/${reportId}/copy`, { method: 'POST' });
                const data = await response.json();
                if (data.success) {
                    showToast('报告已复制', 'success');
                    loadHistory();
                }
            } catch (e) {
                showToast('复制失败', 'error');
            }
        }

        // ===== 批量评测功能 =====
        function updateSelectedCaseCount() {
            const checked = document.querySelectorAll('#cases-tbody .case-checkbox:checked').length;
            document.getElementById('selected-case-count').textContent = checked;
        }

        function selectAllCases() {
            document.querySelectorAll('#cases-tbody .case-checkbox').forEach(cb => cb.checked = true);
            updateSelectedCaseCount();
        }

        function deselectAllCases() {
            document.querySelectorAll('#cases-tbody .case-checkbox').forEach(cb => cb.checked = false);
            document.getElementById('select-all-cases').checked = false;
            updateSelectedCaseCount();
        }

        // 打开批量编辑模态框
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

        // 加载分组列表到批量编辑
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

        // 切换批量编辑字段可用状态
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

        // 关闭批量编辑模态框
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

        // ============ 异步评测模态框 ============
        let _asyncPollTimer = null;
        let _asyncCompletedNotified = new Set();

        function openAsyncTasksModal() {
            document.getElementById('async-tasks-modal').style.display = 'flex';
            pollAsyncTasks();
        }

        function closeAsyncTasksModal() {
            if (_asyncPollTimer) {
                clearInterval(_asyncPollTimer);
                _asyncPollTimer = null;
            }
            document.getElementById('async-tasks-modal').style.display = 'none';
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

        // 批量编辑用例
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

        // 批量删除用例
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

        async function runSelectedEvaluation() {
            const checked = document.querySelectorAll('#cases-tbody .case-checkbox:checked');
            if (checked.length === 0) {
                showToast('请先选择测试用例', 'info');
                return;
            }

            const caseIds = Array.from(checked).map(cb => cb.value);

            try {
                document.getElementById('cases-loading').classList.add('show');

                const response = await fetch('/api/evaluate/cases', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        caseIds: caseIds,
                        metrics: ['correctness', 'safety', 'response_time', 'bleu', 'rouge', 'similarity'],
                        agentType: 'demo'
                    })
                });

                const data = await response.json();
                document.getElementById('cases-loading').classList.remove('show');

                if (data.success) {
                    showToast(`评测完成!通过率: ${data.summary?.pass_rate?.toFixed(1) || 0}%`, 'success');
                    loadHistory();
                    switchTab('history');
                } else {
                    showToast(data.error || '评测失败', 'error');
                }
            } catch (e) {
                document.getElementById('cases-loading').classList.remove('show');
                showToast('评测请求失败', 'error');
            }
        }

        // 复选框变化时更新计数
        document.addEventListener('DOMContentLoaded', () => {
            const casesTbody = document.getElementById('cases-tbody');
            if (casesTbody) {
                casesTbody.addEventListener('change', (e) => {
                    if (e.target.classList.contains('case-checkbox')) {
                        updateSelectedCaseCount();
                    }
                });
            }
        });

        // 键盘快捷键
        document.addEventListener('keydown', (e) => {
            // Ctrl/Cmd + Enter: 提交表单
            if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') {
                const activeTab = document.querySelector('.tab-content.active');
                if (activeTab && activeTab.id === 'cases-content') createTestCase();
                else if (activeTab && activeTab.id === 'groups-content') createGroup();
            }
            // Ctrl/Cmd + D: 切换深色模式
            if ((e.ctrlKey || e.metaKey) && e.key === 'd') {
                e.preventDefault();
                toggleTheme();
            }
            // Ctrl/Cmd + F: 聚焦搜索框
            if ((e.ctrlKey || e.metaKey) && e.key === 'f') {
                e.preventDefault();
                document.getElementById('case-search')?.focus();
            }
        });
