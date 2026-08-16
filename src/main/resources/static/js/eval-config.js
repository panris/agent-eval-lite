var MODEL_API = '/api/eval-config/models';
var DIMENSION_API = '/api/eval-config/dimensions';

async function loadAll() {
  await loadModels();
  await loadDimensions();
}

async function loadModels() {
  var res = await fetch(MODEL_API).then(function(r){return r.json();});
  var html = '';
  res.forEach(function(m) {
    var defaultBadge = m.isDefault ? '<span class="text-xs bg-green-100 text-green-600 px-2 py-0.5 rounded ml-2">默认</span>' : '';
    html += '<div class="border rounded p-3"><div class="flex justify-between items-start"><div><span class="font-medium">' + esc(m.name) + '</span>' + defaultBadge + '<div class="text-xs text-gray-400 mt-1">' + esc(m.provider) + ' | ' + esc(m.baseUrl) + '</div></div><div class="flex gap-1"><button onclick="editModel(' + JSON.stringify(m).replace(/"/g, '&quot;') + ')" class="text-blue-600 text-xs hover:underline">编辑</button><button onclick="delModel(\'' + m.id + '\')" class="text-red-500 text-xs hover:underline ml-2">删除</button></div></div></div>';
  });
  document.getElementById('modelList').innerHTML = html || '<p class="text-gray-400 text-sm">暂无模型，请先添加</p>';
}

async function loadDimensions() {
  var res = await fetch(DIMENSION_API).then(function(r){return r.json();});
  var html = '';
  res.forEach(function(d) {
    var levelLabel = {GLOBAL:'全局', PROJECT:'项目', MODULE:'模块', FUNCTION:'功能'}[d.level];
    var levelColor = {GLOBAL:'bg-gray-100 text-gray-600', PROJECT:'bg-blue-100 text-blue-600', MODULE:'bg-yellow-100 text-yellow-600', FUNCTION:'bg-green-100 text-green-600'}[d.level];
    var path = [];
    if (d.project) path.push(d.project);
    if (d.module) path.push(d.module);
    if (d.function) path.push(d.function);
    html += '<div class="border rounded p-3"><div class="flex justify-between items-start"><div><span class="text-xs ' + levelColor + ' px-2 py-0.5 rounded mr-2">' + levelLabel + '</span><span class="font-medium">' + (path.length > 0 ? path.join('/') : '默认配置') + '</span><div class="text-xs text-gray-400 mt-1">阈值:' + d.passThreshold + (d.systemPrompt ? ' | 自定义提示词' : '') + '</div></div><div class="flex gap-1"><button onclick="editDimension(' + JSON.stringify(d).replace(/"/g, '&quot;') + ')" class="text-blue-600 text-xs hover:underline">编辑</button><button onclick="delDimension(\'' + d.id + '\')" class="text-red-500 text-xs hover:underline ml-2">删除</button></div></div></div>';
  });
  document.getElementById('dimensionList').innerHTML = html || '<p class="text-gray-400 text-sm">暂无维度配置</p>';
}

function showModelModal() {
  document.getElementById('modelModalTitle').textContent = '新增模型';
  document.getElementById('modelId').value = '';
  document.getElementById('modelName').value = '';
  document.getElementById('modelProvider').value = 'openai';
  document.getElementById('modelBaseUrl').value = '';
  document.getElementById('modelApiKey').value = '';
  document.getElementById('modelTemperature').value = '0.1';
  document.getElementById('modelMaxTokens').value = '256';
  document.getElementById('modelTimeout').value = '30000';
  document.getElementById('modelIsDefault').checked = false;
  document.getElementById('modelDescription').value = '';
  document.getElementById('modelModal').classList.remove('hidden');
}

function closeModelModal() {
  document.getElementById('modelModal').classList.add('hidden');
}

function editModel(m) {
  document.getElementById('modelModalTitle').textContent = '编辑模型: ' + m.name;
  document.getElementById('modelId').value = m.id;
  document.getElementById('modelName').value = m.name || '';
  document.getElementById('modelProvider').value = m.provider || 'openai';
  document.getElementById('modelBaseUrl').value = m.baseUrl || '';
  document.getElementById('modelModelName').value = m.modelName || '';
  document.getElementById('modelApiKey').value = m.apiKey || '';
  document.getElementById('modelTemperature').value = m.temperature || '0.1';
  document.getElementById('modelMaxTokens').value = m.maxTokens || '256';
  document.getElementById('modelTimeout').value = m.timeout || '30000';
  document.getElementById('modelIsDefault').checked = m.isDefault || false;
  document.getElementById('modelDescription').value = m.description || '';
  document.getElementById('modelModal').classList.remove('hidden');
}

async function saveModel() {
  var data = {
    name: document.getElementById('modelName').value,
    provider: document.getElementById('modelProvider').value,
    baseUrl: document.getElementById('modelBaseUrl').value,
    modelName: document.getElementById('modelModelName').value,
    apiKey: document.getElementById('modelApiKey').value,
    temperature: parseFloat(document.getElementById('modelTemperature').value),
    maxTokens: parseInt(document.getElementById('modelMaxTokens').value),
    timeout: parseInt(document.getElementById('modelTimeout').value),
    isDefault: document.getElementById('modelIsDefault').checked,
    description: document.getElementById('modelDescription').value
  };
  
  if (!data.name || !data.baseUrl || !data.apiKey) {
    alert('请填写必填字段');
    return;
  }
  
  var id = document.getElementById('modelId').value;
  var url = id ? MODEL_API + '/' + id : MODEL_API;
  var r = await fetch(url, {
    method: id ? 'PUT' : 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify(data)
  });
  
  if (r.ok) {
    closeModelModal();
    await loadAll();
  } else {
    var j = await r.json();
    alert('错误: ' + (j.error || '保存失败'));
  }
}

async function delModel(id) {
  if (!confirm('确认删除该模型?')) return;
  var r = await fetch(MODEL_API + '/' + id, {method: 'DELETE'});
  if (r.ok) {
    await loadAll();
  } else {
    var j = await r.json();
    alert('删除失败: ' + (j.error || '未知错误'));
  }
}

function showDimensionModal() {
  document.getElementById('dimensionModalTitle').textContent = '新增维度配置';
  document.getElementById('dimensionId').value = '';
  document.getElementById('dimensionLevel').value = 'GLOBAL';
  document.getElementById('dimensionProject').value = '';
  document.getElementById('dimensionModule').value = '';
  document.getElementById('dimensionFunction').value = '';
  document.getElementById('dimensionSystemPrompt').value = '';
  document.getElementById('dimensionPassThreshold').value = '0.7';
  document.getElementById('dimThreshVal').textContent = '0.7';
  document.getElementById('dimensionDescription').value = '';
  updateDimensionFields();
  Promise.all([loadModelOptions(), loadDimensionOptions()]).then(function() {
    document.getElementById('dimensionModal').classList.remove('hidden');
  });
}

function closeDimensionModal() {
  document.getElementById('dimensionModal').classList.add('hidden');
}

function updateDimensionFields() {
  var level = document.getElementById('dimensionLevel').value;
  document.getElementById('dimensionProjectField').classList.toggle('hidden', level === 'GLOBAL');
  document.getElementById('dimensionModuleField').classList.toggle('hidden', level !== 'MODULE' && level !== 'FUNCTION');
  document.getElementById('dimensionFunctionField').classList.toggle('hidden', level !== 'FUNCTION');
}

async function loadModelOptions() {
  var res = await fetch(MODEL_API).then(function(r){return r.json();});
  var select = document.getElementById('dimensionModelId');
  select.innerHTML = '<option value="">使用默认模型</option>';
  res.forEach(function(m) {
    var defaultLabel = m.isDefault ? ' (默认)' : '';
    select.innerHTML += '<option value="' + m.id + '">' + esc(m.name) + defaultLabel + '</option>';
  });
}

async function loadDimensionOptions() {
  var res = await fetch('/api/testcases/dimensions').then(function(r){return r.json();});
  if (!res.success) return;
  
  var projects = res.projects || [];
  var modules = res.modules || [];
  var functions = res.functions || [];
  
  fillSelect('dimensionProject', projects, '请选择或输入项目');
  fillSelect('dimensionModule', modules, '请选择或输入模块');
  fillSelect('dimensionFunction', functions, '请选择或输入功能');
}

function fillSelect(id, options, placeholder) {
  var select = document.getElementById(id);
  if (!select) return;
  select.innerHTML = '<option value="">' + placeholder + '</option><option value="_custom_">🔧 输入自定义值...</option>';
  options.forEach(function(opt) {
    select.innerHTML += '<option value="' + esc(opt) + '">' + esc(opt) + '</option>';
  });
  select.setAttribute('style', 'width: 100%;');
}

function handleCustomSelect(select, customInputId) {
  var customInput = document.getElementById(customInputId);
  if (select.value === '_custom_') {
    customInput.classList.remove('hidden');
    customInput.focus();
  } else {
    customInput.classList.add('hidden');
  }
}

function getDimensionValue(selectId, customInputId) {
  var select = document.getElementById(selectId);
  if (select.value === '_custom_') {
    var customInput = document.getElementById(customInputId);
    return customInput.value.trim();
  }
  return select.value;
}

function editDimension(d) {
  document.getElementById('dimensionModalTitle').textContent = '编辑维度配置';
  document.getElementById('dimensionId').value = d.id;
  document.getElementById('dimensionLevel').value = d.level;
  document.getElementById('dimensionProjectCustom').value = '';
  document.getElementById('dimensionModuleCustom').value = '';
  document.getElementById('dimensionFunctionCustom').value = '';
  document.getElementById('dimensionSystemPrompt').value = d.systemPrompt || '';
  document.getElementById('dimensionPassThreshold').value = d.passThreshold || '0.7';
  document.getElementById('dimThreshVal').textContent = d.passThreshold || '0.7';
  document.getElementById('dimensionDescription').value = d.description || '';
  updateDimensionFields();
  Promise.all([loadModelOptions(), loadDimensionOptions()]).then(function() {
    document.getElementById('dimensionModelId').value = d.modelId || '';
    if (d.project) {
      var projectSelect = document.getElementById('dimensionProject');
      var hasProjectOption = Array.from(projectSelect.options).some(function(o) { return o.value === d.project; });
      if (hasProjectOption) {
        projectSelect.value = d.project;
        document.getElementById('dimensionProjectCustom').classList.add('hidden');
      } else {
        projectSelect.value = '_custom_';
        document.getElementById('dimensionProjectCustom').value = d.project;
        document.getElementById('dimensionProjectCustom').classList.remove('hidden');
      }
    }
    if (d.module) {
      var moduleSelect = document.getElementById('dimensionModule');
      var hasModuleOption = Array.from(moduleSelect.options).some(function(o) { return o.value === d.module; });
      if (hasModuleOption) {
        moduleSelect.value = d.module;
        document.getElementById('dimensionModuleCustom').classList.add('hidden');
      } else {
        moduleSelect.value = '_custom_';
        document.getElementById('dimensionModuleCustom').value = d.module;
        document.getElementById('dimensionModuleCustom').classList.remove('hidden');
      }
    }
    if (d.function) {
      var functionSelect = document.getElementById('dimensionFunction');
      var hasFunctionOption = Array.from(functionSelect.options).some(function(o) { return o.value === d.function; });
      if (hasFunctionOption) {
        functionSelect.value = d.function;
        document.getElementById('dimensionFunctionCustom').classList.add('hidden');
      } else {
        functionSelect.value = '_custom_';
        document.getElementById('dimensionFunctionCustom').value = d.function;
        document.getElementById('dimensionFunctionCustom').classList.remove('hidden');
      }
    }
    document.getElementById('dimensionModal').classList.remove('hidden');
  });
}

async function saveDimension() {
  var level = document.getElementById('dimensionLevel').value;
  var project = getDimensionValue('dimensionProject', 'dimensionProjectCustom');
  var module = getDimensionValue('dimensionModule', 'dimensionModuleCustom');
  var functionName = getDimensionValue('dimensionFunction', 'dimensionFunctionCustom');
  
  if (level === 'PROJECT' && !project) {
    alert('项目维度必须指定项目');
    return;
  }
  if (level === 'MODULE' && (!project || !module)) {
    alert('模块维度必须指定项目和模块');
    return;
  }
  if (level === 'FUNCTION' && (!project || !module || !functionName)) {
    alert('功能维度必须指定项目、模块和功能');
    return;
  }
  
  var data = {
    level: level,
    project: level === 'GLOBAL' ? null : project,
    module: level === 'GLOBAL' || level === 'PROJECT' ? null : module,
    function: level === 'FUNCTION' ? functionName : null,
    modelId: document.getElementById('dimensionModelId').value || null,
    systemPrompt: document.getElementById('dimensionSystemPrompt').value || null,
    passThreshold: parseFloat(document.getElementById('dimensionPassThreshold').value),
    description: document.getElementById('dimensionDescription').value
  };
  
  var id = document.getElementById('dimensionId').value;
  var url = id ? DIMENSION_API + '/' + id : DIMENSION_API;
  var r = await fetch(url, {
    method: id ? 'PUT' : 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify(data)
  });
  
  if (r.ok) {
    closeDimensionModal();
    await loadAll();
  } else {
    var j = await r.json();
    alert('错误: ' + (j.error || '保存失败'));
  }
}

async function delDimension(id) {
  if (!confirm('确认删除该维度配置?')) return;
  var r = await fetch(DIMENSION_API + '/' + id, {method: 'DELETE'});
  if (r.ok) {
    await loadAll();
  } else {
    var j = await r.json();
    alert('删除失败: ' + (j.error || '未知错误'));
  }
}

function esc(s) { return s ? s.replace(/</g,'&lt;').replace(/>/g,'&gt;') : ''; }

loadAll();
