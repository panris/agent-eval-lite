#!/usr/bin/env python3
"""
车辆控制评测配置脚本

在评测配置管理中创建 Qwen 评测模型和维度配置，
用于车辆控制意图识别的评测。
"""

import json
import requests

BASE = "http://localhost:8080"

# 1. 查询现有模型
def get_models():
    resp = requests.get(f"{BASE}/api/eval-config/models")
    return resp.json()

# 2. 创建评测模型
def create_model(data):
    resp = requests.post(f"{BASE}/api/eval-config/models", json=data)
    return resp.json()

# 3. 创建维度配置
def create_dimension(data):
    resp = requests.post(f"{BASE}/api/eval-config/dimensions", json=data)
    return resp.json()

# 4. 查询现有维度
def get_dimensions():
    resp = requests.get(f"{BASE}/api/eval-config/dimensions")
    return resp.json()


def main():
    print("📊 车辆控制评测配置创建脚本")
    print("=" * 60)

    # Step 1: 检查/创建 Qwen 模型
    models = get_models()
    qwen_model = None
    for m in models:
        if m["name"] == "Qwen":
            qwen_model = m
            print(f"  ✅ 已存在 Qwen 模型: {m['id']}")
            break

    if not qwen_model:
        print("  📝 创建 Qwen 评测模型...")
        model_data = {
            "name": "Qwen",
            "provider": "custom",
            "baseUrl": "http://115.191.1.3:3000/v1",
            "modelName": "qwen3.6-flash",
            "apiKey": "sk-8CZK4oA3aIzu4xQGA3CeQA2NeYT5ZMJvHacx0UTN5bFyht5i",
            "temperature": 0.1,
            "maxTokens": 256,
            "timeout": 30000,
            "isDefault": True,
            "description": "车辆控制意图识别评测专用模型",
        }
        result = create_model(model_data)
        print(f"  {'✅' if result.get('id') else '❌'} 创建结果: {json.dumps(result, ensure_ascii=False)}")
        if result.get("id"):
            qwen_model = result
    else:
        print("  ⏭️ 跳过模型创建")

    if not qwen_model:
        print("  ❌ 无法获取模型 ID，退出")
        return

    model_id = qwen_model["id"]

    # Step 2: 创建维度配置
    dimensions = get_dimensions()
    existing_funcs = set()
    for d in dimensions:
        if d["level"] == "FUNCTION" and d["project"] == "雷诺" and d["module"] == "意图识别":
            existing_funcs.add(d["function"])

    # 2a. 创建全局默认配置（车辆控制通用）
    if not any(d["level"] == "GLOBAL" for d in dimensions):
        print("\n  📝 创建全局默认配置...")
        global_dim = {
            "level": "GLOBAL",
            "modelId": model_id,
            "passThreshold": 0.7,
            "systemPrompt": """你是一名专业的车辆控制意图识别评测专家。你需要对比模型的实际输出与期望输出，评估模型在意图识别任务中的表现。

评测维度：
1. **路由类型识别（权重 60%）**：判断模型识别的 route_type 是否与期望一致。这是最核心的指标。
   - navigate（导航）：导航类指令
   - simple（简单控制）：直接的车辆控制指令
   - chat（闲聊）：通用对话
   - unmute（取消静音）：取消静音指令
   - vlm（视觉理解）：需要视觉的指令
   - complex（复杂情绪）：带情绪的复杂指令
   - skills_trigger（技能触发）：触发特定技能
   - skills（技能）：技能类指令
   - unsupported（不支持）：不支持的功能
   - laifu（来福）：特定场景

2. **情绪标签（权重 20%）**：判断 emotion_tag 是否与期望一致
   - normal（正常）：情绪平稳
   - confused（困惑）：不确定
   - happy（开心）：积极情绪
   - sad（伤心）：消极情绪
   - playful（俏皮）：轻松幽默
   - angry（生气）：愤怒情绪

3. **VPA 标签（权重 20%）**：判断 vpa_tag 是否与期望一致
   - normal（正常）：普通对话
   - visual（视觉）：需要视觉能力
   - confused（困惑）：不确定
   - memory（记忆）：需要记忆能力

评分标准：
- route_type 正确且其他标签正确：1.0 分
- route_type 正确但部分标签错误：0.7-0.9 分
- route_type 错误但相近（如 simple vs skills_trigger）：0.3-0.5 分
- route_type 完全错误：0.0-0.2 分

请逐项评估并给出具体分数和理由。""",
            "description": "车辆控制意图识别全局默认配置",
        }
        result = create_dimension(global_dim)
        print(f"  {'✅' if result.get('id') else '❌'} 全局配置: {json.dumps(result, ensure_ascii=False)[:100]}")

    # 2b. 为每个路由类型创建功能级配置
    route_type_configs = {
        "navigate": {
            "passThreshold": 0.85,
            "systemPrompt": """评测车辆导航意图识别。重点评估：
1. route_type 是否正确识别为 "navigate"（权重 70%）
2. emotion_tag 是否合理（正常场景应为 normal）
3. quick_reply 是否符合导航场景（如"我看看"、"稍等"等）

评分：route_type 正确 1.0，类型错误 0.0，部分正确酌情 0.3-0.7""",
        },
        "simple": {
            "passThreshold": 0.7,
            "systemPrompt": """评测车辆简单控制意图识别。重点评估：
1. route_type 是否正确识别为 "simple"（权重 60%）
2. emotion_tag 是否合理（应为 normal 或 confused）
3. vpa_tag 是否合理（应为 normal）
4. quick_reply 是否符合车控场景

评分：全部正确 1.0，route_type 正确但标签有偏差 0.7-0.9，route_type 错误 0.0-0.3""",
        },
        "chat": {
            "passThreshold": 0.6,
            "systemPrompt": """评测闲聊意图识别。重点评估：
1. route_type 是否正确识别为 "chat"（权重 60%）
2. emotion_tag 是否符合语境（如讲笑话应为 playful，投诉应为 angry）
3. vpa_tag 是否合理（memory 类查询应为 memory）

评分：全部正确 1.0，route_type 正确但情绪标签偏差 0.6-0.9，route_type 错误 0.0-0.3""",
        },
        "unmute": {
            "passThreshold": 0.8,
            "systemPrompt": """评测取消静音意图识别。重点评估：
1. route_type 是否正确识别为 "unmute"（权重 70%）
2. emotion_tag 是否合理

评分：route_type 正确 1.0，错误 0.0""",
        },
        "vlm": {
            "passThreshold": 0.7,
            "systemPrompt": """评测视觉理解意图识别。重点评估：
1. route_type 是否正确识别为 "vlm"（权重 60%）
2. vpa_tag 是否正确识别为 "visual"（权重 30%）
3. emotion_tag 是否合理

评分：全部正确 1.0，route_type 正确但 vpa_tag 错误 0.6-0.8，route_type 错误 0.0-0.3""",
        },
        "complex": {
            "passThreshold": 0.6,
            "systemPrompt": """评测复杂情绪意图识别。重点评估：
1. route_type 是否正确识别为 "complex"（权重 60%）
2. emotion_tag 是否符合语境（如"天气太热"应为 sad 或 normal）
3. quick_reply 是否合理

评分：全部正确 1.0，route_type 正确但情绪偏差 0.5-0.8，route_type 错误 0.0-0.3""",
        },
        "skills_trigger": {
            "passThreshold": 0.6,
            "systemPrompt": """评测技能触发意图识别。重点评估：
1. route_type 是否正确识别为 "skills_trigger"（权重 70%）
2. emotion_tag 是否合理

评分：route_type 正确 1.0，错误 0.0""",
        },
        "skills": {
            "passThreshold": 0.6,
            "systemPrompt": """评测技能类意图识别。重点评估：
1. route_type 是否正确识别为 "skills"（权重 70%）
2. vpa_tag 是否合理（memory 类应为 memory）

评分：route_type 正确 1.0，错误 0.0""",
        },
        "unsupported": {
            "passThreshold": 0.8,
            "systemPrompt": """评测不支持功能的意图识别。重点评估：
1. route_type 是否正确识别为 "unsupported"（权重 70%）
2. emotion_tag 是否合理（应为 confused）
3. vpa_tag 是否合理（应为 confused）

评分：全部正确 1.0，route_type 正确但标签错误 0.6-0.8，route_type 错误 0.0-0.2""",
        },
        "laifu": {
            "passThreshold": 0.6,
            "systemPrompt": """评测来福特定场景意图识别。重点评估：
1. route_type 是否正确识别为 "laifu"（权重 70%）
2. emotion_tag 和 vpa_tag 是否合理

评分：route_type 正确 1.0，错误 0.0""",
        },
    }

    for func, config in route_type_configs.items():
        if func in existing_funcs:
            print(f"\n  ⏭️ 跳过已存在的功能级配置: {func}")
            continue

        print(f"\n  📝 创建功能级配置: {func}...")
        dim_data = {
            "level": "FUNCTION",
            "project": "雷诺",
            "module": "意图识别",
            "function": func,
            "modelId": model_id,
            "passThreshold": config["passThreshold"],
            "systemPrompt": config["systemPrompt"],
            "description": f"车辆控制 - {func} 意图评测配置",
        }
        result = create_dimension(dim_data)
        if result.get("id"):
            print(f"  ✅ 创建成功: {func} (阈值={config['passThreshold']})")
        else:
            print(f"  ❌ 创建失败: {json.dumps(result, ensure_ascii=False)[:100]}")

    print("\n" + "=" * 60)
    print("🎉 评测配置创建完成！")
    print(f"   模型: Qwen (qwen3.6-flash)")
    print(f"   维度配置: 1 个全局 + {len(route_type_configs)} 个功能级")
    print(f"   覆盖路由类型: {list(route_type_configs.keys())}")


if __name__ == "__main__":
    main()
