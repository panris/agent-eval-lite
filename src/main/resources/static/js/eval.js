function exportReport(format) {
    if (!window.lastReportId) {
        showToast('没有可导出的评测报告', 'info');
        return;
    }

    const url = `/api/reports/${window.lastReportId}/export?format=${format}`;
    window.open(url, '_blank');
}

// 独立加载全部测试用例到评测表格（不分页）
async function loadEvalTestCases() {
    const evalTbody = document.getElementById('eval-tbody');
    if (!evalTbody) return;

    // 保存当前选中状态
    const selectedCaseIds = new Set();
    evalTbody.querySelectorAll('.eval-checkbox:checked').forEach(cb => {
        selectedCaseIds.add(cb.value);
    });

    try {
        const response = await fetch('/api/testcases/all');
        const data = await response.json();
        const allCases = data.testCases || [];
        window._testCases = allCases;

        evalTbody.innerHTML = allCases.map(tc => {
            const _name = utils.escapeHtml(tc.name);
            const _input = utils.escapeHtml(tc.input);
            const _expected = utils.escapeHtml(tc.expected);
            const _project = tc.project ? `<span class="badge" style="background: #2563eb;">${utils.escapeHtml(tc.project)}</span>` : '-';
            const _module = tc.module ? `<span class="badge" style="background: #7c3aed;">${utils.escapeHtml(tc.module)}</span>` : '-';
            const _function = tc.function ? `<span class="badge" style="background: #db2777;">${utils.escapeHtml(tc.function)}</span>` : '-';
            const isSelected = selectedCaseIds.has(tc.id) ? 'checked' : '';
            return `
            <tr>
                <td class="checkbox-cell"><input type="checkbox" class="eval-checkbox" value="${utils.escapeHtml(tc.id)}" ${isSelected}></td>
                <td>${_name}</td>
                <td>${_input}</td>
                <td>${_expected}</td>
                <td>${_project}</td>
                <td>${_module}</td>
                <td>${_function}</td>
            </tr>
            `;
        }).join('');

        if (typeof updateEvalSelectedCount === 'function') updateEvalSelectedCount();
        if (typeof updateSelectAllState === 'function') updateSelectAllState();
    } catch (error) {
        logError('Failed to load eval test cases:', error);
        showToast('加载评测用例失败', 'error');
    }
}

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

function loadDimensionCases() {
    const project = document.getElementById('eval-project').value;
    const moduleDim = document.getElementById('eval-module').value;
    const functionDim = document.getElementById('eval-function').value;

    if (!project && !moduleDim && !functionDim) {
        loadTestCases();
        return;
    }

    document.querySelectorAll('.eval-checkbox').forEach(cb => {
        cb.checked = false;
    });

    const evalTbody = document.getElementById('eval-tbody');
    if (!evalTbody) return;

    const rows = evalTbody.querySelectorAll('tr');
    rows.forEach(row => {
        const checkbox = row.querySelector('.eval-checkbox');
        const caseId = checkbox.value;
        const caseData = window._testCases?.find(tc => tc.id === caseId);
        
        if (caseData) {
            const matchProject = !project || caseData.project === project;
            const matchModule = !moduleDim || caseData.module === moduleDim;
            const matchFunction = !functionDim || caseData.function === functionDim;
            
            if (matchProject && matchModule && matchFunction) {
                checkbox.checked = true;
            }
        }
    });

    const checkedCount = document.querySelectorAll('.eval-checkbox:checked').length;
    showToast(`已选中 ${checkedCount} 个符合条件的测试用例`, 'success');
    updateEvalSelectedCount();
    updateSelectAllState();
}

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
                metrics: getSelectedEvalMetrics(),
                agentType: getSelectedAgentType(),
                agentConfigId: getSelectedAgentConfigId(),
                evalConfigId: getSelectedEvalConfigId(),
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

    // 从选中的测试用例中获取维度信息
    const selectedCases = (window._testCases || []).filter(tc => caseIds.includes(tc.id));
    const project = selectedCases.length > 0 ? selectedCases[0].project : null;
    const module = selectedCases.length > 0 ? selectedCases[0].module : null;
    const function_ = selectedCases.length > 0 ? selectedCases[0].function : null;

    console.log('Sending evaluation for', caseIds.length, 'cases:', caseIds);
    console.log('Dimensions:', { project, module, function: function_ });

    // 显示进度条
    const progressBar = document.getElementById('eval-progress-bar');
    const progressText = document.getElementById('eval-progress-text');
    if (progressBar) progressBar.style.display = 'block';
    if (progressText) {
        progressText.style.display = 'block';
        progressText.textContent = `提交评测任务中... (0/${caseIds.length})`;
    }

    try {
        const response = await fetch('/api/evaluate/async', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                caseIds,
                metrics: getSelectedEvalMetrics(),
                agentType: getSelectedAgentType(),
                agentConfigId: getSelectedAgentConfigId(),
                evalConfigId: getSelectedEvalConfigId(),
                project: project || null,
                module: module || null,
                function: function_ || null
            })
        });

        const data = await response.json();
        if (data.success) {
            const taskId = data.taskId;
            showToast(`评测任务已提交 (共 ${caseIds.length} 条用例)`, 'success');
            await startTaskPolling(taskId, caseIds.length);
        } else {
            showToast(data.error || '评测任务提交失败', 'error');
            document.getElementById('eval-loading').classList.remove('show');
            if (progressBar) progressBar.style.display = 'none';
            if (progressText) progressText.style.display = 'none';
        }
    } catch (error) {
        logError('Evaluation failed:', error);
        showToast('评测请求失败', 'error');
        document.getElementById('eval-loading').classList.remove('show');
        if (progressBar) progressBar.style.display = 'none';
        if (progressText) progressText.style.display = 'none';
    }
}

async function startTaskPolling(taskId, totalCases) {
    const progressBar = document.getElementById('eval-progress-bar');
    const progressFill = document.getElementById('eval-progress-fill');
    const progressText = document.getElementById('eval-progress-text');
    
    const maxAttempts = 120; // 最多轮询120次，每次2秒 = 4分钟
    const pollInterval = 2000;
    let attempts = 0;

    return new Promise((resolve) => {
        const timer = setInterval(async () => {
            attempts++;
            if (attempts > maxAttempts) {
                clearInterval(timer);
                showToast('评测任务超时，请查看异步任务列表', 'error');
                document.getElementById('eval-loading').classList.remove('show');
                if (progressBar) progressBar.style.display = 'none';
                if (progressText) progressText.style.display = 'none';
                resolve();
                return;
            }

            try {
                const resp = await fetch(`/api/tasks/${taskId}`);
                const taskData = await resp.json();
                
                if (taskData.success) {
                    // 后端返回扁平结构，直接使用 taskData
                    const completedCount = taskData.completedCases || 0;
                    const status = taskData.status;
                    
                    // 更新进度
                    const progress = totalCases > 0 ? (completedCount / totalCases) * 100 : 0;
                    if (progressFill) progressFill.style.width = progress + '%';
                    if (progressText) progressText.textContent = `评测中... (${completedCount}/${totalCases})`;

                    if (status === 'COMPLETED') {
                        clearInterval(timer);
                        showToast('评测完成!', 'success');
                        document.getElementById('eval-loading').classList.remove('show');
                        if (progressBar) progressBar.style.display = 'none';
                        if (progressText) progressText.style.display = 'none';
                        
                        // 加载评测详情和刷新历史
                        if (taskData.reportId) {
                            try {
                                const reportResp = await fetch(`/api/reports/${taskData.reportId}`);
                                const reportData = await reportResp.json();
                                if (reportData.success) {
                                    window.lastReportId = taskData.reportId;
                                    window.lastEvaluation = reportData.data;
                                    showEvalDetails();
                                    document.getElementById('eval-result').style.display = 'block';
                                }
                            } catch (e) {
                                logError('Failed to load report:', e);
                            }
                        }
                        
                        // 刷新历史记录
                        await loadHistory();
                        resolve();
                    } else if (status === 'FAILED' || status === 'TIMED_OUT') {
                        clearInterval(timer);
                        showToast(`评测失败: ${taskData.error || status}`, 'error');
                        document.getElementById('eval-loading').classList.remove('show');
                        if (progressBar) progressBar.style.display = 'none';
                        if (progressText) progressText.style.display = 'none';
                        resolve();
                    }
                }
            } catch (e) {
                logError('Polling error:', e);
            }
        }, pollInterval);
    });
}

async function runSelectedEvaluation() {
    const checked = document.querySelectorAll('#cases-tbody .case-checkbox:checked');
    if (checked.length === 0) {
        showToast('请先选择测试用例', 'info');
        return;
    }

    const caseIds = Array.from(checked).map(cb => cb.value);

    // 从选中的测试用例中获取维度信息
    const selectedCases = (window._testCases || []).filter(tc => caseIds.includes(tc.id));
    const project = selectedCases.length > 0 ? selectedCases[0].project : null;
    const module = selectedCases.length > 0 ? selectedCases[0].module : null;
    const function_ = selectedCases.length > 0 ? selectedCases[0].function : null;

    try {
        document.getElementById('cases-loading').classList.add('show');
        
        // 显示进度条
        const progressBar = document.getElementById('eval-progress-bar');
        const progressText = document.getElementById('eval-progress-text');
        if (progressBar) progressBar.style.display = 'block';
        if (progressText) {
            progressText.style.display = 'block';
            progressText.textContent = `提交评测任务中... (0/${caseIds.length})`;
        }

        const response = await fetch('/api/evaluate/async', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                caseIds: caseIds,
                metrics: getSelectedEvalMetrics(),
                agentType: getSelectedAgentType(),
                agentConfigId: getSelectedAgentConfigId(),
                evalConfigId: getSelectedEvalConfigId(),
                project: project || null,
                module: module || null,
                function: function_ || null
            })
        });

        const data = await response.json();

        if (data.success) {
            const taskId = data.taskId;
            showToast(`评测任务已提交 (共 ${caseIds.length} 条用例)`, 'success');
            
            // 轮询任务状态
            const maxAttempts = 120;
            const pollInterval = 2000;
            let attempts = 0;
            
            await new Promise((resolve) => {
                const timer = setInterval(async () => {
                    attempts++;
                    if (attempts > maxAttempts) {
                        clearInterval(timer);
                        showToast('评测任务超时，请查看异步任务列表', 'error');
                        document.getElementById('cases-loading').classList.remove('show');
                        if (progressBar) progressBar.style.display = 'none';
                        if (progressText) progressText.style.display = 'none';
                        resolve();
                        return;
                    }

                    try {
                        const resp = await fetch(`/api/tasks/${taskId}`);
                        const taskData = await resp.json();
                        
                        if (taskData.success) {
                            // 后端返回扁平结构，直接使用 taskData
                            const completedCount = taskData.completedCases || 0;
                            const status = taskData.status;
                            
                            const progress = caseIds.length > 0 ? (completedCount / caseIds.length) * 100 : 0;
                            const progressFill = document.getElementById('eval-progress-fill');
                            if (progressFill) progressFill.style.width = progress + '%';
                            if (progressText) progressText.textContent = `评测中... (${completedCount}/${caseIds.length})`;

                            if (status === 'COMPLETED') {
                                clearInterval(timer);
                                document.getElementById('cases-loading').classList.remove('show');
                                if (progressBar) progressBar.style.display = 'none';
                                if (progressText) progressText.style.display = 'none';
                                
                                if (taskData.reportId) {
                                    try {
                                        const reportResp = await fetch(`/api/reports/${taskData.reportId}`);
                                        const reportData = await reportResp.json();
                                        if (reportData.success) {
                                            window.lastReportId = taskData.reportId;
                                            window.lastEvaluation = reportData.data;
                                        }
                                    } catch (e) {
                                        logError('Failed to load report:', e);
                                    }
                                }
                                
                                showToast('评测完成!', 'success');
                                await loadHistory();
                                switchTab('history');
                                resolve();
                            } else if (status === 'FAILED' || status === 'TIMED_OUT') {
                                clearInterval(timer);
                                document.getElementById('cases-loading').classList.remove('show');
                                if (progressBar) progressBar.style.display = 'none';
                                if (progressText) progressText.style.display = 'none';
                                showToast(`评测失败: ${taskData.error || status}`, 'error');
                                resolve();
                            }
                        }
                    } catch (e) {
                        logError('Polling error:', e);
                    }
                }, pollInterval);
            });
        } else {
            document.getElementById('cases-loading').classList.remove('show');
            if (progressBar) progressBar.style.display = 'none';
            if (progressText) progressText.style.display = 'none';
            showToast(data.error || '评测任务提交失败', 'error');
        }
    } catch (e) {
        document.getElementById('cases-loading').classList.remove('show');
        showToast('评测请求失败', 'error');
    }
}

function showEvalDetails() {
    const detailsDiv = document.getElementById('eval-details');
    const tbody = document.getElementById('details-tbody');

    if (!window.lastEvaluation || !window.lastEvaluation.evaluations) {
        showToast('没有评测详情', 'info');
        return;
    }

    renderCharts(window.lastEvaluation.evaluations);
    renderReportMeta(window.lastEvaluation);

    const evaluations = window.lastEvaluation.evaluations;
    tbody.innerHTML = evaluations.map(ev => {
        const testCase = (window._testCases || []).find(tc => tc.id === ev.testCaseId) || {};
        const _name = utils.escapeHtml(testCase.name || ev.testCaseName || ev.testCaseId || '');
        const _input = utils.escapeHtml(testCase.input || ev.testCaseInput || '');
        const _expected = utils.escapeHtml(testCase.expected || ev.testCaseExpected || '');
        const _actual = utils.escapeHtml(ev.output || '');
        const scorerNames = Object.keys(ev.scorerResults || {}).join(', ');
        const scorerScores = Object.values(ev.scorerResults || {}).map(sr => sr.score.toFixed(2)).join(', ');
        
        let rationaleHtml = '';
        for (const [key, sr] of Object.entries(ev.scorerResults || {})) {
            const reason = utils.escapeHtml(sr.rationale || '');
            if (reason) {
                rationaleHtml += `<div style="font-size:12px;color:#666;margin-top:2px;">${key}: ${reason}</div>`;
            }
        }

        return `
            <tr>
                <td>${_name}</td>
                <td style="max-width:200px;word-break:break-all;">${_input || '-'}</td>
                <td style="max-width:200px;word-break:break-all;">${_expected || '-'}</td>
                <td style="max-width:300px;word-break:break-all;">${_actual || '-'}</td>
                <td>${scorerScores || '-'} (${scorerNames || '-'})${rationaleHtml}</td>
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

// ===== 动态加载 Agent 和评测模型配置 =====
async function loadEvalConfigs() {
    try {
        const r = await fetch('/api/eval-llm-configs');
        const d = await r.json();
        if (d.success && d.configs) {
            const sel = document.getElementById('eval-config');
            if (sel) {
                sel.innerHTML = '<option value="">评测模型(默认算法)</option>';
                d.configs.forEach(c => {
                    sel.innerHTML += `<option value="${c.id}">${c.name} (${c.model})</option>`;
                });
            }
        }
    } catch (e) { console.error('Failed to load eval LLM configs:', e); }
}

async function loadEvalAgents() {
    try {
        const r = await fetch('/api/agents');
        const d = await r.json();
        if (d.success && d.agents) {
            const sel = document.getElementById('eval-agent');
            if (sel) {
                sel.innerHTML = '<option value="">Agent(演示)</option>';
                d.agents.forEach(a => {
                    sel.innerHTML += `<option value="${a.id}">${a.name}</option>`;
                });
            }
        }
    } catch (e) { console.error('Failed to load agents:', e); }
}

function getSelectedEvalMetrics() {
    const checks = document.querySelectorAll('.eval-metric:checked');
    const list = [];
    checks.forEach(c => list.push(c.value));
    return list.length > 0 ? list : ['llm'];
}

function getSelectedAgentConfigId() {
    const sel = document.getElementById('eval-agent');
    return sel ? sel.value || null : null;
}

function getSelectedAgentType() {
    const configId = getSelectedAgentConfigId();
    return configId ? 'custom' : 'demo';
}

function getSelectedEvalConfigId() {
    const sel = document.getElementById('eval-config');
    return sel ? sel.value || null : null;
}

// 页面初始化时加载
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => {
        loadEvalConfigs();
        loadEvalAgents();
        bindEvalCheckboxEvents();
    });
} else {
    loadEvalConfigs();
    loadEvalAgents();
    bindEvalCheckboxEvents();
}

function updateEvalSelectedCount() {
    const count = document.querySelectorAll('.eval-checkbox:checked').length;
    const countEl = document.getElementById('eval-selected-count');
    if (countEl) countEl.textContent = count;
}

function bindEvalCheckboxEvents() {
    const selectAll = document.getElementById('select-all-eval');
    if (selectAll) {
        selectAll.addEventListener('change', function() {
            document.querySelectorAll('.eval-checkbox').forEach(cb => {
                cb.checked = this.checked;
            });
            updateEvalSelectedCount();
        });
    }

    document.addEventListener('change', function(e) {
        if (e.target && e.target.classList.contains('eval-checkbox')) {
            updateEvalSelectedCount();
            updateSelectAllState();
        }
    });
}

function updateSelectAllState() {
    const selectAll = document.getElementById('select-all-eval');
    if (!selectAll) return;
    const all = document.querySelectorAll('.eval-checkbox');
    const checked = document.querySelectorAll('.eval-checkbox:checked');
    selectAll.checked = all.length > 0 && all.length === checked.length;
    selectAll.indeterminate = checked.length > 0 && checked.length < all.length;
}
