(function () {
    try {
        var t = localStorage.getItem('agent-eval-theme');
        if (t === 'dark') document.documentElement.setAttribute('data-theme', 'dark');
        else if (t === 'light') document.documentElement.setAttribute('data-theme', 'light');
    } catch (e) {}
})();