#!/usr/bin/env python3
"""
验证新的评分提示词效果
"""

import json
import requests

BASE = "http://localhost:8080"

def main():
    print("🔍 验证新的评分提示词效果")
    print("=" * 60)

    # 获取测试用例
    resp = requests.get(f"{BASE}/api/testcases?page=0&size=5")
    if resp.status_code != 200:
        print(f"❌ 获取测试用例失败: {resp.status_code}")
        return
    
    data = resp.json()
    test_cases = data.get("testCases", data.get("content", []))
    
    if not test_cases:
        print("❌ 没有测试用例")
        return
    
    print(f"   获取到 {len(test_cases)} 条测试用例\n")
    
    # 准备评测请求
    case_ids = [tc["id"] for tc in test_cases[:3]]  # 取前3条
    
    # 先获取 Agent 配置 ID
    agents_resp = requests.get(f"{BASE}/api/agents")
    agent_config_id = None
    if agents_resp.status_code == 200:
        agents_data = agents_resp.json()
        agents = agents_data.get("agents", [])
        if agents:
            agent_config_id = agents[0]["id"]
            print(f"   使用 Agent 配置: {agents[0]['name']} (ID: {agent_config_id})")
    
    eval_request = {
        "caseIds": case_ids,
        "metrics": ["llm"],
        "agentConfigId": agent_config_id,
        "project": "雷诺",
        "module": "意图识别",
        "name": "评分提示词验证测试",
        "description": "验证新的评分提示词效果"
    }
    
    print(f"   评测请求: {json.dumps(eval_request, indent=2, ensure_ascii=False)}")
    
    # 发送评测请求（同步评测）
    print("\n⏳ 执行评测任务...")
    resp = requests.post(f"{BASE}/api/evaluate/cases", json=eval_request, timeout=120)
    
    if resp.status_code != 200:
        print(f"❌ 评测请求失败: {resp.status_code}")
        print(f"   {resp.text[:300]}")
        return
    
    result = resp.json()
    
    if not result.get("success"):
        print(f"❌ 评测失败: {result.get('error', '未知错误')}")
        return
    
    evaluations = result.get("evaluations", [])
    summary = result.get("summary", {})
    
    print(f"\n📊 评测结果:")
    print("-" * 60)
    print(f"   报告ID: {result.get('reportId')}")
    print(f"   总用例数: {result.get('totalTestCases')}")
    print(f"   通过: {result.get('passedTestCases')}")
    print(f"   失败: {result.get('failedTestCases')}")
    print(f"   执行时间: {result.get('executionTimeMs')}ms\n")
    
    for i, ev in enumerate(evaluations):
        test_case_input = ev.get("testCaseInput", "")
        output = ev.get("output", "")
        score = ev.get("overallScore", 0)
        passed = ev.get("passed", False)
        rationale = ev.get("rationale", "")
        
        status_icon = "✅" if passed else "❌"
        print(f"   {status_icon} Case {i+1}:")
        print(f"      输入: {test_case_input}")
        print(f"      输出: {output[:80] if output else 'N/A'}...")
        print(f"      评分: {score}")
        print(f"      理由: {rationale[:100] if rationale else 'N/A'}")
    
    # 检查评分差异
    scores = [ev.get("overallScore", 0) for ev in evaluations]
    unique_scores = len(set([round(s, 2) for s in scores]))
    print(f"\n📈 评分统计:")
    print(f"   唯一分数数量: {unique_scores}")
    print(f"   分数列表: {[round(s, 2) for s in scores]}")
    
    if unique_scores > 1:
        print(f"   ✅ 评分有差异化，评分提示词工作正常")
    else:
        print(f"   ⚠️  所有分数相同，需要检查评分逻辑")

if __name__ == "__main__":
    main()
