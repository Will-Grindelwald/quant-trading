"""
实体类单元测试

测试核心实体类的功能和业务逻辑。
"""

import pytest
from datetime import datetime
from quantcapital.entities import *


class TestSignal:
    """信号实体测试"""
    
    def test_signal_creation(self):
        """测试信号创建"""
        signal = Signal(
            strategy_id="test_strategy",
            symbol="000001.SZ",
            direction=SignalDirection.BUY,
            strength=0.8,
            timestamp=datetime.now(),
            price=10.5,
            reason="测试信号"
        )
        
        assert signal.strategy_id == "test_strategy"
        assert signal.symbol == "000001.SZ"
        assert signal.direction == SignalDirection.BUY
        assert signal.strength == 0.8
        assert signal.price == 10.5
    
    def test_signal_validation(self):
        """测试信号验证"""
        # 有效信号
        valid_signal = Signal(
            strategy_id="test",
            symbol="000001.SZ",
            direction=SignalDirection.BUY,
            strength=0.8,
            timestamp=datetime.now(),
            price=10.5,
            reason="有效信号"
        )
        assert valid_signal.is_valid()
        
        # 无效信号 - 信号强度超出范围
        invalid_signal = Signal(
            strategy_id="test",
            symbol="000001.SZ",
            direction=SignalDirection.BUY,
            strength=1.5,  # 超出[0,1]范围
            timestamp=datetime.now(),
            price=10.5,
            reason="无效信号"
        )
        assert not invalid_signal.is_valid()


class TestOrder:
    """订单实体测试"""
    
    def test_order_creation(self):
        """测试订单创建"""
        order = Order(
            symbol="000001.SZ",
            order_type=OrderType.LIMIT,
            side=OrderSide.BUY,
            quantity=1000,
            price=10.5,
            strategy_id="test_strategy"
        )
        
        assert order.symbol == "000001.SZ"
        assert order.order_type == OrderType.LIMIT
        assert order.side == OrderSide.BUY
        assert order.quantity == 1000
        assert order.price == 10.5
        assert order.status == OrderStatus.PENDING
        assert order.order_id is not None
    
    def test_order_fill(self):
        """测试订单成交"""
        order = Order(
            symbol="000001.SZ",
            order_type=OrderType.LIMIT,
            side=OrderSide.BUY,
            quantity=1000,
            price=10.5
        )
        
        # 部分成交
        order.fill(500, 10.48, 1.5)
        assert order.filled_quantity == 500
        assert order.remaining_quantity == 500
        assert order.status == OrderStatus.PARTIALLY_FILLED
        assert order.avg_filled_price == 10.48
        
        # 完全成交
        order.fill(500, 10.52, 1.5)
        assert order.filled_quantity == 1000
        assert order.remaining_quantity == 0
        assert order.status == OrderStatus.FILLED
        assert order.avg_filled_price == 10.5  # (500*10.48 + 500*10.52) / 1000


class TestPosition:
    """持仓实体测试"""
    
    def test_position_creation(self):
        """测试持仓创建"""
        position = Position(
            symbol="000001.SZ",
            quantity=0,
            avg_price=0.0,
            strategy_id="test_strategy"
        )
        
        assert position.symbol == "000001.SZ"
        assert position.quantity == 0
        assert position.is_empty
    
    def test_position_buy(self):
        """测试买入操作"""
        position = Position("000001.SZ", 0, 0.0)
        
        # 首次买入
        position.buy(1000, 10.5, 3.15)
        assert position.quantity == 1000
        assert position.avg_price == 10.5
        assert not position.is_empty
        
        # 再次买入
        position.buy(500, 11.0, 1.65)
        assert position.quantity == 1500
        expected_avg = (1000 * 10.5 + 500 * 11.0) / 1500
        assert abs(position.avg_price - expected_avg) < 0.01
    
    def test_position_sell(self):
        """测试卖出操作"""
        position = Position("000001.SZ", 1000, 10.5)
        
        # 部分卖出
        pnl = position.sell(300, 11.0, 0.99)
        assert position.quantity == 700
        assert position.avg_price == 10.5  # 成本价不变
        expected_pnl = 300 * (11.0 - 10.5) - 0.99
        assert abs(pnl - expected_pnl) < 0.01
        
        # 全部卖出
        pnl = position.sell(700, 10.8, 2.31)
        assert position.quantity == 0
        assert position.is_empty


class TestAccount:
    """账户实体测试"""
    
    def test_account_creation(self):
        """测试账户创建"""
        account = Account(
            account_id="test_account",
            initial_capital=1000000.0,
            cash=1000000.0
        )
        
        assert account.account_id == "test_account"
        assert account.initial_capital == 1000000.0
        assert account.cash == 1000000.0
        assert account.available_cash == 1000000.0
        assert len(account.positions) == 0
    
    def test_cash_freeze_unfreeze(self):
        """测试资金冻结和解冻"""
        account = Account("test", 1000000.0, 1000000.0)
        
        # 冻结资金
        assert account.freeze_cash(50000.0, "order_1")
        assert account.frozen_cash == 50000.0
        assert account.available_cash == 950000.0
        
        # 解冻资金
        account.unfreeze_cash(50000.0)
        assert account.frozen_cash == 0.0
        assert account.available_cash == 1000000.0
    
    def test_position_update(self):
        """测试持仓更新"""
        account = Account("test", 1000000.0, 1000000.0)
        
        # 买入成交
        buy_fill = Fill(
            order_id="order_1",
            symbol="000001.SZ",
            side="BUY",
            quantity=1000,
            price=10.5,
            commission=3.15,
            timestamp=datetime.now(),
            strategy_id="test_strategy"
        )
        
        account.update_position(buy_fill)
        
        # 检查现金和持仓
        expected_cash = 1000000.0 - (1000 * 10.5 + 3.15)
        assert abs(account.cash - expected_cash) < 0.01
        
        position = account.get_position("000001.SZ")
        assert position.quantity == 1000
        assert position.avg_price == 10.5


class TestBar:
    """K线实体测试"""
    
    def test_bar_creation(self):
        """测试K线创建"""
        bar = Bar(
            symbol="000001.SZ",
            datetime=datetime.now(),
            frequency=Frequency.DAILY,
            open=10.0,
            high=10.8,
            low=9.8,
            close=10.5,
            volume=1000000,
            amount=10500000.0
        )
        
        assert bar.symbol == "000001.SZ"
        assert bar.frequency == Frequency.DAILY
        assert bar.open == 10.0
        assert bar.high == 10.8
        assert bar.low == 9.8
        assert bar.close == 10.5
        assert bar.volume == 1000000
    
    def test_bar_validation(self):
        """测试K线数据验证"""
        # 有效K线
        valid_bar = Bar(
            symbol="000001.SZ",
            datetime=datetime.now(),
            frequency=Frequency.DAILY,
            open=10.0,
            high=10.8,
            low=9.8,
            close=10.5,
            volume=1000000
        )
        assert valid_bar.is_valid()
        
        # 无效K线 - 最高价小于最低价
        invalid_bar = Bar(
            symbol="000001.SZ",
            datetime=datetime.now(),
            frequency=Frequency.DAILY,
            open=10.0,
            high=9.8,   # 最高价小于最低价
            low=10.8,
            close=10.5,
            volume=1000000
        )
        assert not invalid_bar.is_valid()
    
    def test_technical_indicators(self):
        """测试技术指标计算"""
        bar = Bar(
            symbol="000001.SZ",
            datetime=datetime.now(),
            frequency=Frequency.DAILY,
            open=10.0,
            high=10.8,
            low=9.8,
            close=10.5,
            volume=1000000
        )
        
        # 设置技术指标
        bar.ma5 = 10.2
        bar.ma20 = 10.0
        bar.rsi_14 = 65.5
        
        assert bar.ma5 == 10.2
        assert bar.ma20 == 10.0
        assert bar.rsi_14 == 65.5