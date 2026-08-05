#!/usr/bin/env python3
"""
更新 EvalModel 配置为可用的 LLM API
"""

import json
import requests

BASE = "http://localhost:8080"

def main():
    print("🔧 更新 EvalModel 配置")
    print("=" * 60)

    # 查询现有模型
    resp = requests.get(f"{BASE}/api/eval-config/models")
    if resp.status_code != 200:
        print(f"❌ 获取模型失败: {resp.status_code}")
        return
    
    models = resp.json()
    if not models:
        print("❌ 没有模型配置")
        return
    
    model = models[0]
    model_id = model["id"]
    
    print(f"   当前模型: {model['name']}")
    print(f"   当前 BaseUrl: {model.get('baseUrl', 'N/A')}")
    print(f"   当前 API Key: {model.get('apiKey', 'N/A')[:20]}...")
    
    # 更新为 ThunderSoft API
    print("\n   📝 更新为 ThunderSoft 意图模型...")
    
    update_data = {
        "name": "Qwen",
        "provider": "custom",
        "baseUrl": "http://36.150.116.241:18088/v1/chat/completions",
        "apiKey": "novastack_2026",
        "temperature": 0.1,
        "maxTokens": 256,
        "timeout": 30000,
        "modelName": "qwen3.6-35b-a3b",
        "isDefault": True,
        "description": "车辆控制意图识别评测专用模型 - ThunderSoft"
    }
    
    update_resp = requests.put(
        f"{BASE}/api/eval-config/models/{model_id}",
        json=update_data
    )
    
    if update_resp.status_code == 200:
        result = update_resp.json()
        print(f"   ✅ 更新成功")
        print(f"   新 BaseUrl: {result.get('baseUrl')}")
        print(f"   新 ModelName: {result.get('modelName')}")
    else:
        print(f"   ❌ 更新失败: {update_resp.status_code}")
        print(f"   {update_resp.text[:200]}")

if __name__ == "__main__":
    main()
