#!/usr/bin/env python3
"""
车辆控制意图识别 - 批量生成测试用例脚本

调用本地意图服务 API (http://localhost:8000/api/v2/vehicle-control/route)，
根据接口返回结果批量生成测试用例，导入到测试样例管理系统中。
"""

import json
import time
import sys
import requests

INTENT_API = "http://localhost:8000/api/v2/vehicle-control/route"
TESTCASE_API = "http://localhost:8080/api/testcases"
PROJECT = "雷诺"
MODULE = "意图识别"

# 测试用例场景：覆盖所有车辆控制意图类型
SCENARIOS = [
    # === 导航类 navigate ===
    ("navigate", "帮我导航到天安门", "导航到目的地"),
    ("navigate", "导航回家", "导航到家"),
    ("navigate", "导航到公司", "导航到公司"),
    ("navigate", "我要回家", "回家导航"),
    ("navigate", "去最近的加油站", "导航到加油站"),

    # === 简单控制 simple (车控指令) ===
    ("simple", "打开空调", "空调开启"),
    ("simple", "打开天窗", "天窗开启"),
    ("simple", "关闭车窗", "车窗关闭"),
    ("simple", "播放音乐", "音乐播放"),
    ("simple", "打开后备箱", "后备箱开启"),
    ("simple", "关闭后备箱", "后备箱关闭"),
    ("simple", "打开收音机", "收音机开启"),
    ("simple", "蓝牙音乐", "蓝牙音乐"),
    ("simple", "车窗升降", "车窗升降"),
    ("simple", "吹脚", "吹脚模式"),
    ("simple", "电池健康度", "电池查询"),
    ("simple", "里程数", "里程查询"),
    ("simple", "保养提醒", "保养查询"),
    ("simple", "油耗多少", "油耗查询"),
    ("simple", "胎压监测", "胎压查询"),
    ("simple", "车辆状态", "车辆状态查询"),
    ("simple", "充电还剩多少", "充电状态查询"),
    ("simple", "太热了开空调", "空调开启降温"),

    # === 闲聊 chat ===
    ("chat", "你好", "打招呼"),
    ("chat", "你是谁", "自我介绍"),
    ("chat", "你叫什么名字", "询问名字"),
    ("chat", "今天星期几", "日期查询"),
    ("chat", "现在几点了", "时间查询"),
    ("chat", "讲个笑话", "讲笑话"),
    ("chat", "附近有什么加油站", "附近查询"),
    ("chat", "车在哪里", "车辆位置查询"),
    ("chat", "播放新闻", "新闻播放"),
    ("chat", "拨打10086", "拨打电话"),
    ("chat", "发短信给妈妈", "发送短信"),
    ("chat", "设置提醒", "设置提醒"),
    ("chat", "明天下午三点提醒我开会", "定时提醒"),
    ("chat", "打开蓝牙", "蓝牙开启"),
    ("chat", "连接手机", "手机连接"),
    ("chat", "系统更新", "系统升级"),
    ("chat", "OTA升级", "OTA升级"),
    ("chat", "导航到最近的充电桩", "充电桩导航"),
    ("chat", "手机音乐", "手机音乐播放"),
    ("chat", "播放广播", "广播播放"),
    ("chat", "SOS紧急救援", "紧急救援"),
    ("chat", "我要投诉", "投诉处理"),
    ("chat", "我要洗车", "洗车服务"),
    ("chat", "找个停车场", "停车场查询"),
    ("chat", "升级系统", "系统升级"),

    # === 取消静音 unmute ===
    ("unmute", "取消静音", "取消静音"),
    ("unmute", "声音打开", "打开声音"),

    # === 视觉理解 vlm ===
    ("vlm", "帮我拍照", "拍照"),
    ("vlm", "看看外面", "查看车外"),

    # === 复杂情绪 complex ===
    ("complex", "天气太热了", "温度调节请求"),
    ("complex", "我有点冷", "温度升高请求"),

    # === 技能触发 skills_trigger ===
    ("skills_trigger", "座椅加热", "座椅加热"),

    # === 技能 skills ===
    ("skills", "定时启动", "定时启动"),

    # === 不支持 unsupported ===
    ("unsupported", "调节座椅", "座椅调节（暂不支持）"),
    ("unsupported", "打开灯光", "灯光控制（暂不支持）"),
    ("unsupported", "方向盘加热", "方向盘加热（暂不支持）"),
    ("unsupported", "鸣笛", "鸣笛（暂不支持）"),
    ("unsupported", "闪烁远光灯", "远光灯闪烁（暂不支持）"),
    ("unsupported", "双闪", "双闪（暂不支持）"),
    ("unsupported", "打开蓝牙", "蓝牙（暂不支持）"),
    ("unsupported", "连接手机", "手机连接（暂不支持）"),
    ("unsupported", "充电到80%", "充电目标设置（暂不支持）"),
    ("unsupported", "开始充电", "开始充电（暂不支持）"),
    ("unsupported", "停止充电", "停止充电（暂不支持）"),
    ("unsupported", "开门", "车门开启（暂不支持）"),
    ("unsupported", "锁车", "车辆锁定（暂不支持）"),
    ("unsupported", "开雨刷", "雨刷开启（暂不支持）"),
    ("unsupported", "关雨刷", "雨刷关闭（暂不支持）"),
    ("unsupported", "雨刷调到最快", "雨刷调速（暂不支持）"),
    ("unsupported", "玻璃水", "玻璃水（暂不支持）"),
    ("unsupported", "除雾模式", "除雾（暂不支持）"),
    ("unsupported", "吹前挡", "前挡吹风（暂不支持）"),
    ("unsupported", "自动泊车", "自动泊车（暂不支持）"),
    ("unsupported", "远程启动", "远程启动（暂不支持）"),
    ("unsupported", "预约充电", "预约充电（暂不支持）"),
]


def call_intent_api(text: str) -> dict:
    """调用意图服务 API 获取返回结果"""
    payload = {"input": text, "body": text, "query": text}
    try:
        resp = requests.post(INTENT_API, json=payload, timeout=5)
        resp.raise_for_status()
        return resp.json()
    except Exception as e:
        return {"error": str(e), "route_type": "unknown", "quick_reply": "",
                "emotion_tag": "unknown", "vpa_tag": "unknown"}


def build_testcase(route_type: str, user_input: str, desc: str, api_response: dict) -> dict:
    """根据接口返回构建测试用例"""
    route = api_response.get("route_type", "unknown")
    emotion = api_response.get("emotion_tag", "unknown")
    vpa = api_response.get("vpa_tag", "unknown")
    quick = api_response.get("quick_reply", "")
    skill_id = api_response.get("matched_skill_id", 0)
    skill_name = api_response.get("matched_skill_name", "")

    expected = {
        "route_type": route,
        "emotion_tag": emotion,
        "vpa_tag": vpa,
        "quick_reply": quick,
        "matched_skill_id": skill_id,
        "matched_skill_name": skill_name,
    }

    function_name = route
    type_cn = {
        "navigate": "导航",
        "simple": "简单控制",
        "chat": "闲聊",
        "unmute": "取消静音",
        "vlm": "视觉理解",
        "complex": "复杂情绪",
        "skills_trigger": "技能触发",
        "skills": "技能",
        "unsupported": "不支持",
        "mute": "静音",
    }.get(route, route)

    name = f"车辆控制-{type_cn}-{desc}"
    description = f"意图类型: {route} | 情绪: {emotion} | VPA标签: {vpa} | 描述: {desc}"

    return {
        "name": name,
        "input": user_input,
        "expected": json.dumps(expected, ensure_ascii=False),
        "project": PROJECT,
        "module": MODULE,
        "function": function_name,
        "description": description,
        "metadata": {
            "source": "intent-service-batch",
            "route_type": route,
            "emotion_tag": emotion,
            "vpa_tag": vpa,
        },
    }


def batch_import(testcases: list):
    """批量导入测试用例到测试样例系统"""
    url = f"{TESTCASE_API}/batch"
    try:
        resp = requests.post(url, json=testcases, timeout=30)
        resp.raise_for_status()
        return resp.json()
    except Exception as e:
        return {"success": False, "error": str(e)}


def main():
    print(f"🚗 车辆控制意图识别测试用例生成脚本")
    print(f"   意图服务: {INTENT_API}")
    print(f"   测试样例系统: {TESTCASE_API}")
    print(f"   场景数量: {len(SCENARIOS)}")
    print()

    testcases = []
    route_types_seen = set()

    for i, (route_type, user_input, desc) in enumerate(SCENARIOS):
        print(f"  [{i+1}/{len(SCENARIOS)}] 调用意图服务: \"{user_input}\" ...", end=" ")
        api_response = call_intent_api(user_input)
        actual_route = api_response.get("route_type", "?")
        route_types_seen.add(actual_route)

        tc = build_testcase(route_type, user_input, desc, api_response)
        testcases.append(tc)
        print(f"→ {actual_route} (情绪:{api_response.get('emotion_tag','?')}, VPA:{api_response.get('vpa_tag','?')})")

    print(f"\n📊 接口返回的路由类型: {sorted(route_types_seen)}")
    print(f"   共生成 {len(testcases)} 条测试用例")

    # 批量导入 (每次最多 50 条)
    batch_size = 50
    total_imported = 0
    for i in range(0, len(testcases), batch_size):
        batch = testcases[i:i + batch_size]
        print(f"\n📤 导入第 {i//batch_size + 1} 批 ({len(batch)} 条)...", end=" ")
        result = batch_import(batch)
        if result.get("success"):
            imported = result.get("imported", 0)
            total_imported += imported
            print(f"✅ 成功导入 {imported} 条")
        else:
            print(f"❌ 失败: {result.get('error', result.get('message', 'unknown'))}")

    print(f"\n🎉 全部完成！共导入 {total_imported} 条测试用例")
    print(f"   项目: {PROJECT}")
    print(f"   模块: {MODULE}")
    print(f"   路由类型分布: {sorted(route_types_seen)}")


if __name__ == "__main__":
    main()
