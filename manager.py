import os
import json
import time
from datetime import datetime

# 配置路径
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
CONFIG_DIR = os.path.join(BASE_DIR, "config")
DAILY_FILE = os.path.join(CONFIG_DIR, "daily_config.json")
INTERVAL_FILE = os.path.join(CONFIG_DIR, "interval_config.json")
LOG_FILE = os.path.join(BASE_DIR, "log.txt")

def load_json(filepath):
    if not os.path.exists(filepath):
        return {}
    try:
        with open(filepath, 'r', encoding='utf-8') as f:
            return json.load(f)
    except Exception as e:
        print(f"[错误] 解析 {os.path.basename(filepath)} 失败: {e}")
        return {}

def save_json(filepath, data):
    if not os.path.exists(CONFIG_DIR):
        os.makedirs(CONFIG_DIR)
    try:
        with open(filepath, 'w', encoding='utf-8') as f:
            json.dump(data, f, indent=4, ensure_ascii=False)
        print(f"[成功] 已保存到 {os.path.basename(filepath)}")
    except Exception as e:
        print(f"[错误] 保存 {os.path.basename(filepath)} 失败: {e}")

def format_timestamp(ts):
    if ts == 0:
        return "从未发送"
    return datetime.fromtimestamp(ts / 1000.0).strftime('%Y-%m-%d %H:%M:%S')

def view_groups():
    daily_data = load_json(DAILY_FILE)
    interval_data = load_json(INTERVAL_FILE)
    
    groups = set(daily_data.keys()).union(set(interval_data.keys()))
    print("\n--- 当前纳管群组列表 ---")
    if not groups:
        print("暂无配置的群组。")
    for g in sorted(groups):
        print(f"- {g}")

def view_status():
    print("\n=== 消息发送状态列表 ===")
    
    print("\n[每日定时任务]")
    daily_data = load_json(DAILY_FILE)
    if not daily_data:
        print("  无数据")
    for gid, info in daily_data.items():
        status = "✅ 已发送" if info.get('is_sent_today') else "⏳ 等待中"
        print(f"  群号: {gid} | 时间: {info.get('time')} | 状态: {status} | 消息: {info.get('message')[:15]}...")
        
    print("\n[间隔定时任务]")
    interval_data = load_json(INTERVAL_FILE)
    if not interval_data:
        print("  无数据")
    for gid, info in interval_data.items():
        last_time = format_timestamp(info.get('last_sent_time', 0))
        print(f"  群号: {gid} | 间隔: {info.get('interval')} | 上次发送: {last_time} | 消息: {info.get('message')[:15]}...")

def add_task():
    print("\n--- 新增定时任务 ---")
    print("1. 每日定时任务")
    print("2. 间隔定时任务")
    choice = input("请选择任务类型 (1/2): ")
    
    if choice not in ['1', '2']:
        print("无效选择。")
        return

    gid = input("请输入群号: ").strip()
    msg = input("请输入发送内容: ").strip()
    
    if choice == '1':
        t = input("请输入时间 (格式 HH-mm-ss, 例如 12-00-00): ").strip()
        data = load_json(DAILY_FILE)
        data[gid] = {
            "time": t,
            "message": msg,
            "is_sent_today": False,
            "last_update_date": "1970-01-01"
        }
        save_json(DAILY_FILE, data)
    else:
        interval = input("请输入间隔 (格式 HH-mm-ss, 最低 00-00-30): ").strip()
        data = load_json(INTERVAL_FILE)
        data[gid] = {
            "interval": interval,
            "message": msg,
            "last_sent_time": 0
        }
        save_json(INTERVAL_FILE, data)

def delete_task():
    print("\n--- 删除任务 ---")
    print("1. 每日定时任务")
    print("2. 间隔定时任务")
    choice = input("请选择要删除的任务类型 (1/2): ")
    
    gid = input("请输入要删除的群号: ").strip()
    
    if choice == '1':
        data = load_json(DAILY_FILE)
        if gid in data:
            del data[gid]
            save_json(DAILY_FILE, data)
        else:
            print(f"每日任务中未找到群号 {gid}")
    elif choice == '2':
        data = load_json(INTERVAL_FILE)
        if gid in data:
            del data[gid]
            save_json(INTERVAL_FILE, data)
        else:
            print(f"间隔任务中未找到群号 {gid}")

def modify_task():
    print("\n--- 修改任务 ---")
    print("1. 每日定时任务")
    print("2. 间隔定时任务")
    choice = input("请选择要修改的任务类型 (1/2): ")
    
    gid = input("请输入要修改的群号: ").strip()
    
    if choice == '1':
        data = load_json(DAILY_FILE)
        if gid not in data:
            print(f"未找到群号 {gid}")
            return
        print(f"当前时间: {data[gid]['time']}, 当前消息: {data[gid]['message']}")
        new_time = input("输入新时间 (留空保持原样): ").strip()
        new_msg = input("输入新消息 (留空保持原样): ").strip()
        
        if new_time: data[gid]['time'] = new_time
        if new_msg: data[gid]['message'] = new_msg
        
        # 修改时间后重置发送状态
        if new_time:
            data[gid]['is_sent_today'] = False
            data[gid]['last_update_date'] = "1970-01-01"
            
        save_json(DAILY_FILE, data)
        
    elif choice == '2':
        data = load_json(INTERVAL_FILE)
        if gid not in data:
            print(f"未找到群号 {gid}")
            return
        print(f"当前间隔: {data[gid]['interval']}, 当前消息: {data[gid]['message']}")
        new_interval = input("输入新间隔 (留空保持原样): ").strip()
        new_msg = input("输入新消息 (留空保持原样): ").strip()
        
        if new_interval: data[gid]['interval'] = new_interval
        if new_msg: data[gid]['message'] = new_msg
        
        save_json(INTERVAL_FILE, data)

def clear_logs():
    if os.path.exists(LOG_FILE):
        try:
            with open(LOG_FILE, 'w', encoding='utf-8') as f:
                f.write("")
            print("\n[成功] 日志已清理。")
        except Exception as e:
            print(f"\n[错误] 清理日志失败: {e}")
    else:
        print("\n日志文件不存在，无需清理。")

def main():
    while True:
        os.system('cls' if os.name == 'nt' else 'clear')
        print("\n===============================")
        print("     QFun 定时任务管理系统     ")
        print("===============================")
        print("  1. 查看群组列表")
        print("  2. 查看消息发送状态")
        print("  3. 新增任务 (包含新增群组)")
        print("  4. 修改任务")
        print("  5. 删除任务")
        print("  6. 清理日志")
        print("  0. 退出程序")
        print("===============================")
        
        cmd = input("请输入指令编号: ").strip()
        
        if cmd == '1':
            view_groups()
        elif cmd == '2':
            view_status()
        elif cmd == '3':
            add_task()
        elif cmd == '4':
            modify_task()
        elif cmd == '5':
            delete_task()
        elif cmd == '6':
            clear_logs()
        elif cmd == '0':
            print("退出管理系统。")
            break
        else:
            print("未知指令，请重新输入。")
        
        input("\n按回车键继续...")

if __name__ == "__main__":
    main()
