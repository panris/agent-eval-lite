#!/usr/bin/env python3
"""
修复 Agent 配置，确保请求体使用 ${input} 占位符
"""

import json
import requests

BASE = "http://localhost:8080"

def main():
    print("🔧 修复 Agent 配置 - 意图服务")
    print("=" * 60)

    # 查询现有 Agent 配置
    resp = requests.get(f"{BASE}/api/agent-configs")
    configs = resp.json().get("configs", [])
    
    for config in configs:
        if config["name"] == "意图服务" or config["type"] == "http":
            print(f"\n📋 找到 Agent 配置: {config['name']}")
            print(f"   ID: {config['id']}")
            print(f"   Endpoint: {config['endpoint']}")
            print(f"   当前 type: {config['type']}")
            
            # 创建新的 requestMapping，使用正确的模板
            new_request_mapping = {
                "template": json.dumps({
                    "query": "${input}",
                    "body": "${input}",
                    "input": "${input}"
                }, ensure_ascii=False),
                "inputField": None,
                "staticFields": None
            }
            
            # 更新配置
            update_data = {
                "name": config["name"],
                "type": config["type"],
                "description": config.get("description", ""),
                "endpoint": config["endpoint"],
                "timeout": config.get("timeout", 30000),
                "headers": config.get("headers", {}),
                "config": config.get("config", {}),
                "requestMapping": new_request_mapping,
                "responseMapping": None  # 不配置 responseMapping，直接返回完整响应
            }
            
            # 发送更新请求
            update_resp = requests.put(
                f"{BASE}/api/agent-configs/{config['id']}",
                json=update_data
            )
            
            if update_resp.status_code == 200:
                print(f"   ✅ 更新成功")
                updated = update_resp.json()
                if "requestMapping" in updated:
                    print(f"   新 template: {updated['requestMapping'].get('template', 'N/A')[:100]}...")
            else:
                print(f"   ❌ 更新失败: {update_resp.text}")
            
            # 测试一下
            print(f"\n🧪 测试 Agent 是否能正确处理不同输入...")
            test_inputs = ["打开空调", "导航回家", "播放音乐"]
            for test_input in test_inputs:
                test_resp = requests.post(
                    f"{config['endpoint']}",
                    json={"query": test_input, "body": test_input, "input": test_input}
                )
                if test_resp.status_code == 200:
                    result = test_resp.json()
                    route_type = result.get("route_type", "unknown")
                    emotion_tag = result.get("emotion_tag", "unknown")
                    print(f"   输入 '{test_input}' -> route_type={route_type}, emotion={emotion_tag}")
                else:
                    print(f"   输入 '{test_input}' -> 请求失败: {test_resp.status_code}")
            
            break
    else:
        print("\n⚠️  未找到 Agent 配置")

if __name__ == "__main__":
    main()
