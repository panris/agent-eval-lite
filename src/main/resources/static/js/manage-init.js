// manage.html 页面加载 loading bar（需在 DOM 未完成前注入到 <head>）
(function () {
    var loadingBar = document.createElement('div');
    loadingBar.id = 'loading-bar';
    document.head.appendChild(loadingBar);
    setTimeout(function () { loadingBar.classList.add('loading'); }, 10);
})();
