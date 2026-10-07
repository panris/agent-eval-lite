// 首页内联脚本：主题切换 + 统计数据 + 最近历史 + 系统状态
// 版本与 index.html 的 script src 同步更新

(function () {
    // ========== 主题切换 ==========
    window.toggleTheme = function () {
        const html = document.documentElement;
        const currentTheme = html.getAttribute('data-theme');
        const newTheme = currentTheme === 'dark' ? 'light' : 'dark';
        html.setAttribute('data-theme', newTheme);
        localStorage.setItem('theme', newTheme);
        document.getElementById('theme-btn').textContent = newTheme === 'dark' ? '☀️' : '🌙';
    };

    window.loadTheme = function () {
        const savedTheme = localStorage.getItem('theme') || 'light';
        document.documentElement.setAttribute('data-theme', savedTheme);
        document.getElementById('theme-btn').textContent = savedTheme === 'dark' ? '☀️' : '🌙';
    };

    // ========== 格式化运行时长 ==========
    window.formatUptime = function (seconds) {
        if (seconds < 60) return seconds + '秒';
        if (seconds < 3600) return Math.floor(seconds / 60) + '分钟';
        if (seconds < 86400) return Math.floor(seconds / 3600) + '小时';
        return Math.floor(seconds / 86400) + '天';
    };

    // ========== 统计数据 ==========
    window.loadStats = async function () {
        try {
            const health = await utils.api.get('/api/health?_=' + Date.now());
            const reports = await utils.api.get('/api/reports?size=100&_=' + Date.now());

            document.getElementById('totalTestCases').textContent = health.testCases || 0;
            document.getElementById('totalReports').textContent = health.reports || 0;

            const reportList = reports.reports || [];
            if (reportList.length > 0) {
                const avgPassRate = reportList.reduce((sum, r) => sum + (r.summary?.pass_rate || 0), 0) / reportList.length;
                const avgTime = reportList.reduce((sum, r) => sum + (r.summary?.avg_response_time || 0), 0) / reportList.length;
                document.getElementById('avgPassRate').textContent = avgPassRate.toFixed(1) + '%';
                document.getElementById('avgResponseTime').textContent = avgTime.toFixed(0) + 'ms';
            } else {
                document.getElementById('avgPassRate').textContent = '-';
                document.getElementById('avgResponseTime').textContent = '-';
            }
        } catch (err) {
            utils.logError('加载统计数据失败:', err);
        }
    };

    // ========== 最近评测历史 ==========
    window.loadRecentReports = async function () {
        try {
            const data = await utils.api.get('/api/reports?size=5&sort=desc&_=' + Date.now());
            const reports = data.reports || [];
            const container = document.getElementById('recentReportsContent');

            if (reports.length === 0) {
                container.innerHTML = `
                    <div class="empty-state">
                        <div class="empty-state-icon">📭</div>
                        <p>暂无评测记录</p>
                    </div>`;
                return;
            }

            container.innerHTML = `
                <table class="report-table">
                    <thead>
                        <tr>
                            <th>报告ID</th>
                            <th>评测时间</th>
                            <th>通过率</th>
                            <th>分组</th>
                            <th>操作</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${reports.map(r => `
                            <tr>
                                <td><code>${utils.escapeHtml(r.id)}</code></td>
                                <td>${r.timestamp ? new Date(r.timestamp).toLocaleString() : '-'}</td>
                                <td>
                                    <span class="badge ${(r.summary?.pass_rate || 0) >= 70 ? 'badge-success' : 'badge-danger'}">
                                        ${(r.summary?.pass_rate || 0).toFixed(1)}%
                                    </span>
                                </td>
                                <td>${r.group ? utils.escapeHtml(r.group) : '-'}</td>
                                <td><a href="/manage#history-tab" class="view-all-link">查看详情</a></td>
                            </tr>`).join('')}
                    </tbody>
                </table>`;
        } catch (err) {
            utils.logError('加载评测历史失败:', err);
        }
    };

    // ========== 系统状态 ==========
    window.loadSystemStatus = async function () {
        try {
            const health = await utils.api.get('/api/health');
            const container = document.getElementById('systemStatusContent');
            container.innerHTML = `
                <div class="status-item">
                    <span class="status-label">服务状态</span>
                    <span class="status-value">
                        <span class="status-indicator ${health.status === 'UP' ? 'healthy' : 'error'}"></span>
                        ${health.status === 'UP' ? '运行正常' : '异常'}
                    </span>
                </div>
                <div class="status-item">
                    <span class="status-label">版本</span>
                    <span class="status-value">${health.version || '-'}</span>
                </div>
                <div class="status-item">
                    <span class="status-label">运行时长</span>
                    <span class="status-value">${formatUptime(health.uptimeSeconds || 0)}</span>
                </div>
                <div class="status-item">
                    <span class="status-label">分组数量</span>
                    <span class="status-value">${health.groups || 0}</span>
                </div>
                <div class="status-item">
                    <span class="status-label">已配置 Agent</span>
                    <span class="status-value">${health.agents || 0}</span>
                </div>`;
        } catch (err) {
            utils.logError('加载系统状态失败:', err);
        }
    };

    // ========== 页面初始化 ==========
    window.addEventListener('DOMContentLoaded', function () {
        loadTheme();
        Promise.all([loadStats(), loadRecentReports(), loadSystemStatus()]);
    });
})();
