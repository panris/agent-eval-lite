/**
 * Agent Eval Lite - 通用工具函数
 */

// ============ Toast 通知系统 ============
const Toast = {
    container: null,
    
    init() {
        if (!this.container) {
            this.container = document.createElement('div');
            this.container.id = 'toast-container';
            this.container.style.cssText = `
                position: fixed;
                top: 20px;
                right: 20px;
                z-index: 10000;
                display: flex;
                flex-direction: column;
                gap: 10px;
            `;
            document.body.appendChild(this.container);
        }
    },
    
    show(message, type = 'info', duration = 3000) {
        this.init();
        
        const toast = document.createElement('div');
        const colors = {
            success: { bg: '#4CAF50', text: 'white' },
            error: { bg: '#f44336', text: 'white' },
            warning: { bg: '#ff9800', text: 'white' },
            info: { bg: '#2196F3', text: 'white' }
        };
        const color = colors[type] || colors.info;
        
        toast.style.cssText = `
            padding: 12px 24px;
            background: ${color.bg};
            color: ${color.text};
            border-radius: 8px;
            box-shadow: 0 4px 12px rgba(0,0,0,0.15);
            font-size: 14px;
            animation: slideIn 0.3s ease;
            max-width: 300px;
        `;
        toast.textContent = message;
        
        // 动画样式
        if (!document.getElementById('toast-animations')) {
            const style = document.createElement('style');
            style.id = 'toast-animations';
            style.textContent = `
                @keyframes slideIn {
                    from { transform: translateX(100%); opacity: 0; }
                    to { transform: translateX(0); opacity: 1; }
                }
                @keyframes slideOut {
                    from { transform: translateX(0); opacity: 1; }
                    to { transform: translateX(100%); opacity: 0; }
                }
            `;
            document.head.appendChild(style);
        }
        
        this.container.appendChild(toast);
        
        setTimeout(() => {
            toast.style.animation = 'slideOut 0.3s ease';
            setTimeout(() => toast.remove(), 300);
        }, duration);
    },
    
    success(message) { this.show(message, 'success'); },
    error(message) { this.show(message, 'error'); },
    warning(message) { this.show(message, 'warning'); },
    info(message) { this.show(message, 'info'); }
};

// 兼容旧 API
function showToast(message, type = 'info') {
    Toast.show(message, type);
}

// ============ 确认对话框 ============
const Confirm = {
    show(title, message, onConfirm) {
        const overlay = document.createElement('div');
        overlay.style.cssText = `
            position: fixed;
            top: 0;
            left: 0;
            width: 100%;
            height: 100%;
            background: rgba(0,0,0,0.5);
            display: flex;
            justify-content: center;
            align-items: center;
            z-index: 9999;
        `;
        
        const _title = (s) => s == null ? '' : String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;').replace(/'/g,'&#39;');
        overlay.innerHTML = `
            <div style="
                background: var(--bg-card, white);
                padding: 24px;
                border-radius: 12px;
                max-width: 400px;
                text-align: center;
                box-shadow: 0 8px 32px rgba(0,0,0,0.2);
            ">
                <h3 style="margin: 0 0 12px 0;">${_title(title)}</h3>
                <p style="color: var(--text-secondary, #666); margin: 0 0 20px 0;">${_title(message)}</p>
                <div style="display: flex; gap: 12px; justify-content: center;">
                    <button class="btn" onclick="this.closest('[style*=\"position: fixed\"]').remove()">取消</button>
                    <button class="btn btn-primary" style="background: #f44336; color: white;">确定</button>
                </div>
            </div>
        `;
        
        document.body.appendChild(overlay);
        
        overlay.querySelector('.btn-primary').onclick = () => {
            overlay.remove();
            onConfirm();
        };
        
        overlay.querySelector('.btn').onclick = () => overlay.remove();
    },
    
    danger(title, message, onConfirm) {
        this.show(title, message, onConfirm);
    }
};

// 兼容旧 API
function showConfirm(title, message, onConfirm) {
    Confirm.show(title, message, onConfirm);
}

// ============ API 请求工具 ============
const API = {
    async get(url) {
        const res = await fetch(url);
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        return res.json();
    },
    
    async post(url, data) {
        const res = await fetch(url, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(data)
        });
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        return res.json();
    },
    
    async delete(url) {
        const res = await fetch(url, { method: 'DELETE' });
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
    },
    
    async download(url, filename) {
        const res = await fetch(url);
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        const blob = await res.blob();
        const url2 = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url2;
        a.download = filename;
        a.click();
        URL.revokeObjectURL(url2);
    }
};

// ============ 格式化工具 ============
const Format = {
    date(timestamp) {
        return new Date(timestamp).toLocaleString('zh-CN');
    },
    
    duration(ms) {
        if (ms < 1000) return `${ms}ms`;
        if (ms < 60000) return `${(ms/1000).toFixed(1)}s`;
        return `${(ms/60000).toFixed(1)}min`;
    },
    
    score(score) {
        return parseFloat(score).toFixed(2);
    },
    
    bytes(bytes) {
        if (bytes < 1024) return `${bytes}B`;
        if (bytes < 1024*1024) return `${(bytes/1024).toFixed(1)}KB`;
        return `${(bytes/1024/1024).toFixed(1)}MB`;
    }
};

// ============ 防抖工具 ============
function debounce(func, wait) {
    let timeout;
    return function executedFunction(...args) {
        clearTimeout(timeout);
        timeout = setTimeout(() => func.apply(this, args), wait);
    };
}

// ============ 主题工具 ============
const Theme = {
    KEY: 'agent-eval-theme',
    
    init() {
        const saved = localStorage.getItem(this.KEY);
        if (saved === 'dark') this.enable();
        else if (saved === 'light') this.disable();
        else if (window.matchMedia('(prefers-color-scheme: dark)').matches) this.enable();
    },
    
    enable() {
        document.documentElement.setAttribute('data-theme', 'dark');
        localStorage.setItem(this.KEY, 'dark');
    },
    
    disable() {
        document.documentElement.removeAttribute('data-theme');
        localStorage.setItem(this.KEY, 'light');
    },
    
    toggle() {
        if (document.documentElement.hasAttribute('data-theme')) {
            this.disable();
        } else {
            this.enable();
        }
    }
};

// 兼容旧 API
function toggleTheme() { Theme.toggle(); }
function loadTheme() { Theme.init(); }

// ============ 导出工具 ============
const Export = {
    json(data, filename) {
        const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
        this.download(blob, filename);
    },
    
    csv(headers, rows, filename) {
        const csv = [headers.join(','), ...rows.map(r => r.map(v => `"${v}"`).join(','))].join('\n');
        const blob = new Blob([csv], { type: 'text/csv' });
        this.download(blob, filename);
    },
    
    download(blob, filename) {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = filename;
        a.click();
        URL.revokeObjectURL(url);
    }
};

// ============ 表格工具 ============
const Table = {
    create(rows, options = {}) {
        const { headers, onRowClick, className = '' } = options;
        
        const table = document.createElement('table');
        table.className = className;
        
        // 表头
        if (headers) {
            const thead = document.createElement('thead');
            const headerRow = document.createElement('tr');
            headers.forEach(h => {
                const th = document.createElement('th');
                th.textContent = h;
                headerRow.appendChild(th);
            });
            thead.appendChild(headerRow);
            table.appendChild(thead);
        }
        
        // 数据行
        const tbody = document.createElement('tbody');
        rows.forEach(row => {
            const tr = document.createElement('tr');
            if (onRowClick) {
                tr.style.cursor = 'pointer';
                tr.onclick = () => onRowClick(row);
            }
            row.forEach(cell => {
                const td = document.createElement('td');
                td.textContent = cell;
                tr.appendChild(td);
            });
            tbody.appendChild(tr);
        });
        table.appendChild(tbody);
        
        return table;
    },

    /**
     * HTML 转义：防止 XSS 注入
     * 用于 innerHTML 模板字符串中所有用户提供的文本字段
     */
    escapeHtml(str) {
        if (str == null) return '';
        const s = String(str);
        return s
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }
};
