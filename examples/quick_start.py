#!/usr/bin/env python3
"""
QuantCapital 快速开始示例

这个脚本演示了如何快速使用框架进行回测。
使用模拟数据，无需下载真实历史数据。
"""

import os
import sys
import logging
from datetime import datetime, timedelta
from pathlib import Path

# 添加项目根目录到Python路径
project_root = Path(__file__).parent.parent
sys.path.insert(0, str(project_root))

from quantcapital.config import ConfigManager
from quantcapital.backtest import BacktestEngine
from quantcapital.strategy import MACrossStrategy
from quantcapital.entities import StrategyInstance, StrategyType

# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

def create_temp_config():
    """创建临时配置"""
    config = ConfigManager()
    
    # 创建临时目录
    temp_dir = project_root / "temp"
    temp_dir.mkdir(exist_ok=True)
    
    config.config = {
        "env": "backtest",
        "data_root": str(temp_dir),
        "business_db_path": str(temp_dir / "business.db"),
        "initial_capital": 1000000.0,
        "data_source": {
            "request_delay": 0.01  # 快速模拟
        },
        "execution": {
            "slippage": 0.001,
            "commission_rate": 0.0003,
            "delay_seconds": 0
        },
        "portfolio": {
            "max_position_pct": 0.1,  # 允许更大仓位用于演示
            "max_total_position_pct": 0.95,
            "min_order_amount": 1000,
            "position_size_method": "fixed_amount",
            "default_position_size": 50000  # 5万元一手
        },
        "logging": {
            "level": "INFO",
            "file_enabled": False,
            "console_enabled": True
        }
    }
    
    return config

def create_sample_strategies(event_engine, data_handler):
    """创建示例策略"""
    # 均线交叉开单策略
    entry_config = StrategyInstance(
        strategy_id="demo_ma_cross_entry",
        name="演示均线交叉开单策略",
        strategy_type=StrategyType.ENTRY,
        enabled=True,
        config={
            'short_window': 5,
            'long_window': 20,
            'stop_loss_pct': 0.05,
            'take_profit_pct': 0.10,
            'universe': [
                '000001.SZ',  # 平安银行
                '000002.SZ',  # 万科A
                '600000.SH',  # 浦发银行
                '600036.SH',  # 招商银行
                '600519.SH'   # 贵州茅台
            ]
        }
    )
    
    entry_strategy = MACrossStrategy(entry_config, event_engine, data_handler)
    
    # 对应的止损策略
    exit_config = StrategyInstance(
        strategy_id="demo_ma_cross_exit", 
        name="演示均线交叉止损策略",
        strategy_type=StrategyType.EXIT,
        enabled=True,
        config=entry_config.config
    )
    
    exit_strategy = MACrossStrategy(exit_config, event_engine, data_handler)
    
    return [entry_strategy, exit_strategy]

def main():
    """主函数"""
    print("🚀 QuantCapital 快速开始演示")
    print("=" * 50)
    
    try:
        # 创建配置
        config = create_temp_config()
        logger.info("✅ 配置创建完成")
        
        # 创建回测引擎
        backtest_engine = BacktestEngine(config)
        logger.info("✅ 回测引擎初始化完成")
        
        # 设置回测参数（使用较短的时间范围以便快速演示）
        end_date = datetime.now()
        start_date = end_date - timedelta(days=60)  # 回测2个月
        
        # 股票池
        universe = [
            '000001.SZ',  # 平安银行
            '000002.SZ',  # 万科A
            '600000.SH',  # 浦发银行
            '600036.SH',  # 招商银行
            '600519.SH'   # 贵州茅台
        ]
        
        print(f"\n📊 回测设置:")
        print(f"   时间范围: {start_date.date()} - {end_date.date()}")
        print(f"   股票池: {len(universe)} 支股票")
        print(f"   初始资金: {config.get('initial_capital'):,.0f} 元")
        
        # 设置回测参数
        backtest_engine.setup(
            start_date=start_date,
            end_date=end_date,
            universe=universe
        )
        
        # 创建策略
        strategies = create_sample_strategies(
            backtest_engine.event_engine,
            backtest_engine.data_handler
        )
        
        # 添加策略到引擎
        for strategy in strategies:
            backtest_engine.add_strategy(strategy)
        
        logger.info(f"✅ 添加了 {len(strategies)} 个策略")
        
        # 注意：这个演示使用模拟数据，不需要真实的历史数据下载
        print("\n⚠️  注意: 本演示使用模拟数据，结果仅供参考")
        print("   如需真实回测，请先运行 python examples/download_data.py")
        
        # 运行回测
        print("\n🔄 开始回测...")
        if backtest_engine.run():
            print("\n🎉 回测完成!")
            
            # 显示结果
            backtest_engine.print_results()
            
            # 显示一些策略统计
            for strategy in strategies:
                if hasattr(strategy, 'get_statistics'):
                    stats = strategy.get_statistics()
                    print(f"\n策略 {strategy.strategy_id} 统计:")
                    print(f"  类型: {strategy.strategy_type.value}")
                    print(f"  状态: {'激活' if strategy.is_active else '停用'}")
        else:
            print("❌ 回测运行失败")
            
    except Exception as e:
        logger.error(f"❌ 演示执行失败: {e}", exc_info=True)
        print("\n💡 提示:")
        print("   1. 确保已安装所有依赖: pip install -r requirements.txt")
        print("   2. 检查Python版本 >= 3.8")
        print("   3. 如有问题，请查看日志或提交Issue")
    
    print("\n🎓 下一步:")
    print("   1. 查看 用户入门手册.md 了解详细用法")
    print("   2. 运行 python examples/download_data.py 下载真实数据")
    print("   3. 编写自己的策略并进行回测")
    print("   4. 运行测试: pytest tests/ -v")

if __name__ == "__main__":
    main()