#!/usr/bin/env python3
"""
验证评测执行 - 检查不同 case 是否返回不同结果
"""

import json
import requests

BASE = "http://localhost:8080"

def main():
    print("🧪 验证评测执行")
    print("=" * 60)

    # 获取测试用例
    resp = requests.get(f"{BASE}/api/testcases?project=雷诺&module=意图识别&page=0&size=3")
    if resp.status_code != 200:
        print(f"❌ 获取测试用例失败: {resp.status_code}")
        return
    
    data = resp.json()
    test_cases = data.get("testCases", [])
    print(f"   找到 {len(test_cases)} 条测试用例")
    
    if len(test_cases) < 2:
        print("⚠️  测试用例太少，无法验证")
        return
    
    # 获取 Agent 配置
    agent_resp = requests.get(f"{BASE}/api/agents")
    agent_data = agent_resp.json()
    agents = agent_data.get("agents", [])
    
    if not agents:
        print("❌ 没有找到 Agent 配置")
        return
    
    agent_config = agents[0]
    print(f"   使用 Agent: {agent_config['name']} (ID: {agent_config['id']})")
    
    # 构建评测请求
    case_ids = [tc["id"] for tc in test_cases]
    
    eval_request = {
        "caseIds": case_ids,
        "metrics": ["correctness"],
        "agentConfigId": agent_config["id"]
    }
    
    print(f"\n📤 发送评测请求...")
    print(f"   Case IDs: {case_ids}")
    print(f"   Agent Config ID: {agent_config['id']}")
    
    # 发送评测请求
    eval_resp = requests.post(f"{BASE}/api/evaluate/cases", json=eval_request, timeout=60)
    
    if eval_resp.status_code != 200:
        print(f"❌ 评测请求失败: {eval_resp.status_code}")
        print(eval_resp.text[:200])
        return
    
    eval_data = eval_resp.json()
    
    if not eval_data.get("success"):
        print(f"❌ 评测失败: {eval_data.get('message', 'Unknown error')}")
        return
    
    evaluations = eval_data.get("evaluations", [])
    
    print(f"\n📊 评测结果:")
    print(f"   总用例数: {eval_data.get('totalTestCases')}")
    print(f"   通过: {eval_data.get('passedTestCases')}")
    print(f"   失败: {eval_data.get('failedTestCases')}")
    print(f"   执行时间: {eval_data.get('executionTimeMs')}ms")
    
    # 检查每个用例的输出是否不同
    outputs = []
    for i, ev in enumerate(evaluations):
        test_case_id = ev.get("testCaseId", "")
        test_case_input = ev.get("testCaseInput", "")
        output = ev.get("output", "")
        score = ev.get("overallScore", 0)
        passed = ev.get("passed", False)
        
        outputs.append(output)
        
        print(f"\n   用例 {i+1}:")
        print(f"     ID: {test_case_id}")
        print(f"     输入: {test_case_input}")
        print(f"     输出: {output[:80]}...")
        print(f"     得分: {score:.2f}")
        print(f"     通过: {'是' if passed else '否'}")
    
    # 检查输出是否都一致
    unique_outputs = set(outputs)
    if len(unique_outputs) == 1:
        print(f"\n⚠️  警告：所有用例返回的输出都相同！")
        print(f"   这表明 Agent 没有正确处理不同的输入。")
    else:
        print(f"\n✅ 验证成功：不同用例返回不同的输出")
        print(f"   唯一输出数量: {len(unique_outputs)}")

if __name__ == "__main__":
    main()
