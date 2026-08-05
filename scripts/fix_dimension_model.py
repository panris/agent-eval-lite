#!/usr/bin/env python3
"""
修复 EvalDimensionConfig 的 modelId
"""

import json
import requests

BASE = "http://localhost:8080"

def main():
    print("🔧 修复 EvalDimensionConfig 的 modelId")
    print("=" * 60)

    # 1. 获取 EvalModel 配置
    print("\n📋 获取 EvalModel 配置...")
    models_resp = requests.get(f"{BASE}/api/eval-config/models")
    models = models_resp.json()
    
    if not models:
        print("❌ 没有可用的模型配置")
        return
    
    model = models[0]
    model_id = model["id"]
    print(f"   找到模型: {model['name']} (ID: {model_id})")
    print(f"   BaseUrl: {model.get('baseUrl', 'N/A')}")
    print(f"   ModelName: {model.get('modelName', 'N/A')}")
    
    # 2. 获取维度配置
    print("\n📋 获取维度配置...")
    dims_resp = requests.get(f"{BASE}/api/eval-config/dimensions")
    dims_data = dims_resp.json()
    dims = dims_data if isinstance(dims_data, list) else dims_data.get("dimensions", [])
    
    if not dims:
        print("❌ 没有维度配置")
        return
    
    print(f"   找到 {len(dims)} 个维度配置")
    
    # 3. 更新每个维度配置的 modelId
    for dim in dims:
        config_id = dim["id"]
        current_model_id = dim.get("modelId")
        
        if current_model_id == model_id:
            print(f"\n   ⏭️  配置 {config_id[:8]}... 已有正确的 modelId，跳过")
            continue
        
        print(f"\n   📝 更新配置 {config_id[:8]}...")
        print(f"      当前 modelId: {current_model_id}")
        print(f"      新 modelId: {model_id}")
        
        # 更新配置
        update_data = {
            "level": dim["level"],
            "project": dim.get("project"),
            "module": dim.get("module"),
            "function": dim.get("function"),
            "modelId": model_id,
            "passThreshold": dim.get("passThreshold", 0.7),
            "systemPrompt": dim.get("systemPrompt", ""),
            "description": dim.get("description", "")
        }
        
        update_resp = requests.put(
            f"{BASE}/api/eval-config/dimensions/{config_id}",
            json=update_data
        )
        
        if update_resp.status_code == 200:
            result = update_resp.json()
            if result.get("success"):
                print(f"      ✅ 更新成功")
            else:
                print(f"      ❌ 更新失败: {result.get('error', '未知错误')}")
        else:
            print(f"      ❌ HTTP 错误: {update_resp.status_code}")
            print(f"      {update_resp.text[:200]}")
    
    print(f"\n{'=' * 60}")
    print(f"🎉 修复完成！")

if __name__ == "__main__":
    main()
