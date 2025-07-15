#!/usr/bin/env python3
"""
简单回测示例

展示如何使用量化交易框架进行基本的回测。
此示例演示了框架的核心功能：事件驱动、数据处理、执行等。
"""

import sys
import time
from pathlib import Path
from datetime import datetime, timedelta

# 添加项目根目录到Python路径
project_root = Path(__file__).parent.parent
sys.path.insert(0, str(project_root))

from quantcapital.config import ConfigManager, setup_logging
from quantcapital.engine import EventEngine
from quantcapital.data import BacktestDataHandler, DataUpdater
from quantcapital.execution import SimulatedExecutionHandler
from quantcapital.entities import Bar, Frequency, Account


def main():
    """主函数"""
    print("=" * 60)
    print("QuantCapital 量化交易框架 - 简单回测示例")
    print("=" * 60)
    
    # 1. 初始化配置和日志
    config = ConfigManager(env='backtest')
    setup_logging(
        log_level=config.get('logging.level', 'INFO'),
        log_file='./logs/backtest.log',
        log_format=config.get('logging.format')
    )
    
    print(f"✓ 配置加载完成，环境: {config.env}")
    print(f"✓ 初始资金: {config.get('initial_capital'):,.0f} 元")
    
    # 2. 创建事件引擎
    event_engine = EventEngine()
    print("✓ 事件引擎创建完成")
    
    # 3. 创建数据更新器并生成模拟数据
    print("\n开始生成模拟数据...")
    data_updater = DataUpdater(config.get_all(), event_engine)
    
    # 同步更新数据（生成模拟K线数据）
    test_symbols = ['600000.SH', '000001.SZ', '300001.SZ']
    data_updater.update_kline_data_sync(test_symbols, Frequency.DAILY)
    print(f"✓ 模拟数据生成完成: {len(test_symbols)}支股票")
    
    # 4. 创建数据处理器
    data_handler = BacktestDataHandler(
        data_root=config.get('data_root'),
        business_db_path=config.get('business_db_path')
    )
    
    # 预加载数据到内存
    start_date = datetime.now() - timedelta(days=30)
    end_date = datetime.now()
    data_handler.load_data_to_memory(test_symbols, start_date, end_date, Frequency.DAILY)
    print("✓ 数据预加载完成")
    
    # 5. 创建执行器
    execution_handler = SimulatedExecutionHandler(event_engine, config.get('execution'))
    print("✓ 模拟执行器创建完成")
    
    # 6. 创建账户
    account = Account(
        account_id="test_account",
        initial_capital=config.get('initial_capital'),
        cash=config.get('initial_capital')
    )
    print(f"✓ 账户创建完成: {account.account_id}")
    
    # 7. 启动事件引擎和各个组件
    print("\n启动事件引擎...")
    event_engine.start()
    execution_handler.start()
    print("✓ 所有组件启动完成")
    
    # 8. 运行简单的回测逻辑
    print("\n开始回测...")
    run_simple_backtest(data_handler, account, test_symbols)
    
    # 9. 停止组件
    print("\n停止组件...")
    execution_handler.stop()
    event_engine.stop()
    print("✓ 所有组件已停止")
    
    # 10. 输出回测结果
    print_backtest_results(account)
    
    print("\n" + "=" * 60)
    print("回测示例运行完成！")
    print("=" * 60)


def run_simple_backtest(data_handler, account, symbols):
    """运行简单的回测逻辑"""
    print("执行简单的买入操作...")
    
    # 设置回测时间
    current_time = datetime.now() - timedelta(days=1)
    data_handler.set_current_time(current_time)
    
    # 获取最新数据
    latest_bars = data_handler.get_latest_bars(symbols, Frequency.DAILY, count=1)
    
    bought_count = 0
    for symbol, bars in latest_bars.items():
        if bars:
            bar = bars[0]
            print(f"  {symbol}: 价格={bar.close:.2f}, 成交量={bar.volume:,}")
            
            # 简单策略：如果价格合理就买入
            if bar.close > 5.0 and account.available_cash > bar.close * 100:
                # 模拟买入100股
                purchase_amount = bar.close * 100
                if account.available_cash >= purchase_amount:
                    # 这里简化处理，直接更新账户
                    # 实际应该通过事件和执行器处理
                    account.cash -= purchase_amount
                    print(f"    模拟买入 {symbol} 100股，价格 {bar.close:.2f}")
                    bought_count += 1
    
    print(f"✓ 买入操作完成，共买入 {bought_count} 支股票")


def print_backtest_results(account):
    """输出回测结果"""
    print("\n" + "=" * 40)
    print("回测结果摘要")
    print("=" * 40)
    
    print(f"账户ID: {account.account_id}")
    print(f"初始资金: {account.initial_capital:,.2f} 元")
    print(f"剩余现金: {account.cash:,.2f} 元")
    print(f"已用资金: {account.initial_capital - account.cash:,.2f} 元")
    print(f"持仓数量: {len(account.positions)}")
    print(f"总手续费: {account.total_commission:.2f} 元")
    print(f"订单数量: {len(account.orders)}")
    print(f"成交数量: {len(account.fills)}")
    
    if account.positions:
        print("\n持仓明细:")
        for symbol, position in account.positions.items():
            print(f"  {symbol}: {position}")


if __name__ == "__main__":
    main()