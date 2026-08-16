var API = '/api/eval-llm-configs';

async function loadAll() {
  var pr = await fetch(API+'/presets').then(function(r){return r.json();});
  var html = '';
  pr.presets.forEach(function(p) {
    html += '<button onclick="fillPreset(' + JSON.stringify(p).replace(/'/g,"&#39;") + ')" class="border px-3 py-1 rounded text-sm hover:bg-blue-50">' + esc(p.name) + '</button>';
  });
  document.getElementById('presets').innerHTML = html;

  var res = await fetch(API).then(function(r){return r.json();});
  document.getElementById('configCount').textContent = res.total;
  var listHtml = '';
  res.configs.forEach(function(c) {
    listHtml += '<div class="border rounded p-3 flex justify-between items-center"><div><span class="font-medium">' + esc(c.name) + '</span><span class="text-gray-400 text-sm ml-2">' + esc(c.model) + '</span><span class="text-xs text-gray-400 ml-2">阈值:' + c.passThreshold + '</span></div><div class="flex gap-1"><button onclick="editConfig(' + JSON.stringify(c).replace(/'/g,"&#39;") + ')" class="text-blue-600 text-sm hover:underline">编辑</button><button onclick="delConfig(\'' + c.id + '\')" class="text-red-500 text-sm hover:underline ml-2">删除</button></div></div>';
  });
  document.getElementById('configList').innerHTML = listHtml || '<p class="text-gray-400">暂无配置</p>';
}

function fillPreset(p) {
  ['name','baseUrl','apiKey','model'].forEach(function(f){document.getElementById(f).value=p[f]||'';});
  document.getElementById('temperature').value = p.temperature||0.1;
  document.getElementById('tempVal').textContent = p.temperature||0.1;
  document.getElementById('maxTokens').value = p.maxTokens||256;
  document.getElementById('passThreshold').value = p.passThreshold||0.7;
  document.getElementById('threshVal').textContent = p.passThreshold||0.7;
  document.getElementById('timeout').value = p.timeout||30000;
  document.getElementById('formTitle').textContent = '新建配置（预设填充）';
  document.getElementById('configId').value = '';
}

function editConfig(c) {
  document.getElementById('configId').value = c.id;
  ['name','baseUrl','apiKey','model'].forEach(function(f){document.getElementById(f).value=c[f]||'';});
  document.getElementById('temperature').value = c.temperature||0.1;
  document.getElementById('tempVal').textContent = c.temperature||0.1;
  document.getElementById('maxTokens').value = c.maxTokens||256;
  document.getElementById('passThreshold').value = c.passThreshold||0.7;
  document.getElementById('threshVal').textContent = c.passThreshold||0.7;
  document.getElementById('timeout').value = c.timeout||30000;
  document.getElementById('formTitle').textContent = '编辑: ' + c.name;
}

function resetForm() {
  document.getElementById('configId').value = '';
  document.getElementById('formTitle').textContent = '新建配置';
  ['name','baseUrl','apiKey','model'].forEach(function(f){document.getElementById(f).value='';});
  document.getElementById('temperature').value = 0.1;
  document.getElementById('tempVal').textContent = '0.1';
  document.getElementById('maxTokens').value = 256;
  document.getElementById('passThreshold').value = 0.7;
  document.getElementById('threshVal').textContent = '0.7';
  document.getElementById('timeout').value = 30000;
}

async function saveConfig() {
  var data = {
    name: document.getElementById('name').value,
    baseUrl: document.getElementById('baseUrl').value,
    apiKey: document.getElementById('apiKey').value,
    model: document.getElementById('model').value,
    temperature: parseFloat(document.getElementById('temperature').value),
    maxTokens: parseInt(document.getElementById('maxTokens').value),
    passThreshold: parseFloat(document.getElementById('passThreshold').value),
    timeout: parseInt(document.getElementById('timeout').value)
  };
  var id = document.getElementById('configId').value;
  var url = id ? API+'/'+id : API;
  var r = await fetch(url, {method: id?'PUT':'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify(data)});
  var j = await r.json();
  if (j.success) { alert(j.message); resetForm(); loadAll(); }
  else alert('错误: '+j.error);
}

async function delConfig(id) {
  if (!confirm('确认删除?')) return;
  await fetch(API+'/'+id, {method:'DELETE'});
  loadAll();
}

function esc(s) { return s ? s.replace(/</g,'&lt;').replace(/>/g,'&gt;') : ''; }

loadAll();
