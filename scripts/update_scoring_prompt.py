#!/usr/bin/env python3
"""
更新精准的评分提示词到 EvalDimensionConfig
"""

import json
import requests

BASE = "http://localhost:8080"

# 新的评分提示词 - 精准提取 + 快速评分
NEW_SYSTEM_PROMPT = """你是一名精准的车辆控制意图识别评测专家。

## 任务
对比实际输出与期望输出，提取关键字段并评分。

## 输入数据
用户会提供：
- 【实际输出】：JSON格式的模型响应
- 【期望输出】：JSON格式的正确答案

## 评分步骤（严格按此执行）

### 步骤1：解析JSON，提取核心字段
从实际输出和期望输出中分别提取：
- route_type（路由类型）：最重要字段
- emotion_tag（情绪标签）
- vpa_tag（VPA标签）
- quick_reply（快速回复文本）

### 步骤2：逐项对比评分

#### route_type（权重70%）
对比实际的 route_type 与期望的 route_type：
- 完全一致 → 得 0.7 分
- 不一致 → 得 0 分，最终总分 ≤ 0.3

#### emotion_tag（权重15%）
对比实际的 emotion_tag 与期望的 emotion_tag：
- 完全一致 → 得 0.15 分
- 不一致 → 得 0 分

#### vpa_tag（权重15%）
对比实际的 vpa_tag 与期望的 vpa_tag：
- 完全一致 → 得 0.15 分
- 不一致 → 得 0 分

## 最终评分规则
1. route_type 一致 + 所有标签一致 → 1.0 分
2. route_type 一致 + emotion_tag 不一致 + vpa_tag 一致 → 0.85 分
3. route_type 一致 + emotion_tag 一致 + vpa_tag 不一致 → 0.85 分
4. route_type 一致 + 两个标签都不一致 → 0.7 分
5. route_type 不一致 → 0.0 分

## 输出格式（严格JSON）
返回一个JSON对象，格式如下：
{
  "score": 0.85,
  "rationale": "route_type一致(simple)，emotion_tag不一致(期望normal/实际happy)，vpa_tag一致(normal)"
}

注意：
- score 保留两位小数
- rationale 简要说明各字段对比结果
- 只输出JSON，不要其他文本"""

def main():
    print("🔧 更新评分提示词到 EvalDimensionConfig")
    print("=" * 60)

    # 查询当前配置
    resp = requests.get(f"{BASE}/api/eval-config/dimensions")
    if resp.status_code != 200:
        print(f"❌ 获取配置失败: {resp.status_code}")
        return
    
    data = resp.json()
    configs = data if isinstance(data, list) else data.get("dimensions", [])
    
    if not configs:
        print("❌ 没有找到维度配置")
        return
    
    for config in configs:
        config_id = config["id"]
        level = config["level"]
        print(f"\n📋 处理配置: {config_id}")
        print(f"   Level: {level}")
        if config.get("project"):
            print(f"   Project: {config['project']}")
        if config.get("module"):
            print(f"   Module: {config['module']}")
        if config.get("function"):
            print(f"   Function: {config['function']}")
        
        # 更新配置
        update_data = {
            "level": config["level"],
            "project": config.get("project"),
            "module": config.get("module"),
            "function": config.get("function"),
            "modelId": config.get("modelId"),
            "passThreshold": config.get("passThreshold", 0.7),
            "systemPrompt": NEW_SYSTEM_PROMPT,
            "description": config.get("description", "")
        }
        
        update_resp = requests.put(
            f"{BASE}/api/eval-config/dimensions/{config_id}",
            json=update_data
        )
        
        if update_resp.status_code == 200:
            updated = update_resp.json()
            print(f"   ✅ 更新成功")
            print(f"   新 systemPrompt 长度: {len(NEW_SYSTEM_PROMPT)} 字符")
        else:
            print(f"   ❌ 更新失败: {update_resp.status_code}")
            print(f"   {update_resp.text[:200]}")
    
    print(f"\n{'=' * 60}")
    print(f"🎉 所有配置更新完成！")
    print(f"   新的评分提示词特点：")
    print(f"   1. 精准提取核心字段（route_type, emotion_tag, vpa_tag）")
    print(f"   2. route_type 权重 70%（最核心）")
    print(f"   3. 其他标签权重 30%")
    print(f"   4. 严格JSON输出格式")

if __name__ == "__main__":
    main()
