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










        let _historySearchTimer = null;

        // 收藏/取消收藏


// 对比选中报告(增强版:可视化对比弹窗)


        // 复制报告

        // 分享报告

        // 全选/取消全选历史记录

        // 删除选中的历史记录

        // 清空全部报告

        // 查看历史报告详情

        // 导出历史报告

        // ============ 分页状态 ============
        let tcPage = 1;
        let tcPageSize = 20;
        let tcTotal = 0;
        let tcTotalPages = 0;
        let tcKeyword = '';
        let tcGroupId = '';





        // ============ 加载测试用例 ============

        // 创建测试用例

        // 删除测试用例

        // 确保分组列表已加载(history 下拉框需要)

        // 加载分组

        // 查看分组中的用例

        // 显示批量添加到分组模态框

        // 加载用例到模态框

        // 批量添加选中的用例到分组

        // 关闭模态框

        // 全选/取消全选模态框中的用例

        // 创建分组

        // 删除分组

        // 运行评测 (选中用例)

        // 显示评测详情
        // Global chart instances
        let passRateChart = null;
        let scoreChart = null;
        let chartRenderPending = false;


        // 页面隐藏时销毁图表节省资源
        document.addEventListener('visibilitychange', () => {
            if (document.hidden) {
                if (passRateChart) { passRateChart.destroy(); passRateChart = null; }
                if (scoreChart) { scoreChart.destroy(); scoreChart = null; }
            }
        });

        // 渲染趋势图表



        // 导出报告

        // 导出 PDF 报告
        // 导出 PDF 报告(服务器端生成,支持中文)

        // 按分组评测


        // 加载分组用例

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

