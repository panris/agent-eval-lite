#!/usr/bin/env python3
"""
修复 Agent 配置 - 将硬编码的 template 替换为使用 ${input} 占位符的 template
"""

import json
import requests

BASE = "http://localhost:8080"

def main():
    print("🔧 修复 Agent 配置")
    print("=" * 60)

    # 查询现有 Agent 配置
    resp = requests.get(f"{BASE}/api/agents")
    if resp.status_code != 200:
        print(f"❌ 获取 Agent 配置失败: {resp.status_code}")
        return
    
    data = resp.json()
    configs = data.get("agents", [])
    print(f"   找到 {len(configs)} 个 Agent 配置")
    
    for config in configs:
        print(f"\n📋 处理 Agent: {config['name']} (ID: {config['id']})")
        print(f"   Endpoint: {config['endpoint']}")
        print(f"   Type: {config['type']}")
        
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
            f"{BASE}/api/agents/{config['id']}",
            json=update_data
        )
        
        if update_resp.status_code == 200:
            print(f"   ✅ 更新成功")
            updated = update_resp.json()
            if "requestMapping" in updated:
                template = updated['requestMapping'].get('template', '')
                print(f"   新 template: {template[:100]}...")
        else:
            print(f"   ❌ 更新失败: {update_resp.status_code} - {update_resp.text[:100]}")
            continue
        
        # 测试一下
        print(f"\n🧪 测试不同输入是否返回不同结果...")
        test_inputs = ["打开空调", "导航回家", "播放音乐"]
        results = []
        for test_input in test_inputs:
            test_resp = requests.post(
                config["endpoint"],
                json={"query": test_input, "body": test_input, "input": test_input}
            )
            if test_resp.status_code == 200:
                result = test_resp.json()
                route_type = result.get("route_type", "unknown")
                emotion_tag = result.get("emotion_tag", "unknown")
                vpa_tag = result.get("vpa_tag", "unknown")
                results.append(f"输入 '{test_input}' -> route_type={route_type}, emotion={emotion_tag}, vpa={vpa_tag}")
                print(f"   ✅ {results[-1]}")
            else:
                print(f"   ❌ 输入 '{test_input}' -> 请求失败: {test_resp.status_code}")
        
        # 检查结果是否都一致
        if len(set(r.split("->")[1].strip() for r in results)) == 1:
            print(f"\n   ⚠️  警告：所有输入返回的结果都相同！")
        else:
            print(f"\n   ✅ 不同输入返回不同结果，修复成功！")

if __name__ == "__main__":
    main()
