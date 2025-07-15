"""
策略模块单元测试

测试策略基类和策略管理器的功能。
"""

import pytest
import time
from datetime import datetime
from unittest.mock import Mock, MagicMock
from quantcapital.strategy import StrategyManager, MACrossStrategy
from quantcapital.entities import StrategyInstance, StrategyType, SignalDirection


class TestStrategyManager:
    """策略管理器测试"""
    
    def test_strategy_manager_creation(self, event_engine):
        """测试策略管理器创建"""
        manager = StrategyManager(event_engine)
        
        assert manager.event_engine == event_engine
        assert len(manager.strategies) == 0
        assert manager.portfolio_manager is None
    
    def test_add_remove_strategy(self, event_engine, sample_strategy_instance):
        """测试添加和移除策略"""
        manager = StrategyManager(event_engine)
        
        # 创建模拟策略
        mock_data_handler = Mock()
        strategy = MACrossStrategy(sample_strategy_instance, event_engine, mock_data_handler)
        
        # 添加策略
        assert manager.add_strategy(strategy)
        assert len(manager.strategies) == 1
        assert manager.get_strategy(strategy.strategy_id) == strategy
        
        # 重复添加应该失败
        assert not manager.add_strategy(strategy)
        assert len(manager.strategies) == 1
        
        # 移除策略
        assert manager.remove_strategy(strategy.strategy_id)
        assert len(manager.strategies) == 0
        assert manager.get_strategy(strategy.strategy_id) is None
        
        # 移除不存在的策略应该失败
        assert not manager.remove_strategy("non_existent")
    
    def test_strategy_activation(self, event_engine, sample_strategy_instance):
        """测试策略激活和停用"""
        manager = StrategyManager(event_engine)
        mock_data_handler = Mock()
        strategy = MACrossStrategy(sample_strategy_instance, event_engine, mock_data_handler)
        
        manager.add_strategy(strategy)
        
        # 策略应该默认激活
        assert strategy.is_active
        
        # 停用策略
        assert manager.deactivate_strategy(strategy.strategy_id)
        assert not strategy.is_active
        
        # 重新激活策略
        assert manager.activate_strategy(strategy.strategy_id)
        assert strategy.is_active
    
    def test_strategy_statistics(self, event_engine, sample_strategy_instance):
        """测试策略统计信息"""
        manager = StrategyManager(event_engine)
        mock_data_handler = Mock()
        strategy = MACrossStrategy(sample_strategy_instance, event_engine, mock_data_handler)
        
        manager.add_strategy(strategy)
        
        stats = manager.get_statistics()
        assert stats['total_strategies'] == 1
        assert stats['active_strategies'] == 1
        assert strategy.strategy_id in stats['strategy_details']
        
        # 停用策略后检查统计
        manager.deactivate_strategy(strategy.strategy_id)
        stats = manager.get_statistics()
        assert stats['active_strategies'] == 0


class TestMACrossStrategy:
    """均线交叉策略测试"""
    
    def test_strategy_creation(self, event_engine, sample_strategy_instance):
        """测试策略创建"""
        mock_data_handler = Mock()
        strategy = MACrossStrategy(sample_strategy_instance, event_engine, mock_data_handler)
        
        assert strategy.strategy_id == sample_strategy_instance.strategy_id
        assert strategy.strategy_type == sample_strategy_instance.strategy_type
        assert strategy.short_window == 5
        assert strategy.long_window == 20
        assert strategy.stop_loss_pct == 0.05
        assert len(strategy.universe) == 2
    
    def test_watch_symbols_entry_strategy(self, event_engine):
        """测试开单策略的关注标的"""
        strategy_instance = StrategyInstance(
            strategy_id="test_entry",
            name="测试开单策略",
            strategy_type=StrategyType.ENTRY,
            enabled=True,
            config={
                'universe': ['000001.SZ', '000002.SZ', '600000.SH']
            }
        )
        
        mock_data_handler = Mock()
        strategy = MACrossStrategy(strategy_instance, event_engine, mock_data_handler)
        
        # 模拟组合管理器
        mock_portfolio = Mock()
        mock_account = Mock()
        mock_account.positions = {
            '000001.SZ': Mock(strategy_id='test_entry'),  # 已有持仓
        }
        mock_portfolio.account = mock_account
        strategy.set_portfolio_manager(mock_portfolio)
        
        # 开单策略应该关注股票池中未持有的标的
        watch_symbols = strategy.get_watch_symbols()
        expected = {'000002.SZ', '600000.SH'}  # 排除已持有的000001.SZ
        assert watch_symbols == expected
    
    def test_watch_symbols_exit_strategy(self, event_engine):
        """测试止盈止损策略的关注标的"""
        strategy_instance = StrategyInstance(
            strategy_id="test_exit",
            name="测试止损策略",
            strategy_type=StrategyType.EXIT,
            enabled=True,
            config={}
        )
        
        mock_data_handler = Mock()
        strategy = MACrossStrategy(strategy_instance, event_engine, mock_data_handler)
        
        # 模拟组合管理器
        mock_portfolio = Mock()
        mock_account = Mock()
        mock_account.positions = {
            '000001.SZ': Mock(strategy_id='test_exit'),      # 本策略持仓
            '000002.SZ': Mock(strategy_id='other_strategy'), # 其他策略持仓
        }
        mock_portfolio.account = mock_account
        strategy.set_portfolio_manager(mock_portfolio)
        
        # 止损策略应该只关注本策略开单的持仓标的
        watch_symbols = strategy.get_watch_symbols()
        expected = {'000001.SZ'}
        assert watch_symbols == expected
    
    def test_ma_calculation(self, event_engine, sample_strategy_instance):
        """测试均线计算"""
        from quantcapital.entities import Bar, Frequency
        
        mock_data_handler = Mock()
        strategy = MACrossStrategy(sample_strategy_instance, event_engine, mock_data_handler)
        
        # 创建测试K线数据
        bars = []
        prices = [10.0, 10.2, 10.1, 10.3, 10.5, 10.4, 10.6, 10.8, 10.7, 10.9]
        
        for i, price in enumerate(prices):
            bar = Bar(
                symbol="000001.SZ",
                datetime=datetime.now(),
                frequency=Frequency.DAILY,
                open=price,
                high=price + 0.1,
                low=price - 0.1,
                close=price,
                volume=1000000
            )
            bars.append(bar)
        
        # 测试5日均线计算
        ma5 = strategy._calculate_ma(bars, 5)
        expected_ma5 = sum(prices[-5:]) / 5  # 最近5个价格的平均
        assert abs(ma5 - expected_ma5) < 0.001
        
        # 测试数据不足的情况
        ma10 = strategy._calculate_ma(bars[:3], 5)  # 只有3个数据点
        assert ma10 is None
    
    def test_set_universe(self, event_engine, sample_strategy_instance):
        """测试设置股票池"""
        mock_data_handler = Mock()
        strategy = MACrossStrategy(sample_strategy_instance, event_engine, mock_data_handler)
        
        new_universe = ['600000.SH', '600036.SH', '600519.SH']
        strategy.set_universe(new_universe)
        
        assert strategy.universe == set(new_universe)
        assert len(strategy.universe) == 3