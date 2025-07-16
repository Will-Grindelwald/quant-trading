"""
账户实体定义

管理资金和持仓信息，提供盈亏计算和风控数据支撑。
"""

from datetime import datetime
from dataclasses import dataclass, field
from typing import Dict, List, Optional
from .position import Position
from .order import Order
from .fill import Fill
from .trade import Trade


@dataclass
class Account:
    """交易账户"""
    account_id: str                    # 账户ID
    initial_capital: float             # 初始资金
    cash: float                        # 现金
    frozen_cash: float = 0.0           # 冻结资金
    positions: Dict[str, Position] = field(default_factory=dict)  # 持仓字典
    
    # 交易记录
    orders: Dict[str, Order] = field(default_factory=dict)        # 订单记录
    fills: List[Fill] = field(default_factory=list)               # 成交记录
    trades: List[Trade] = field(default_factory=list)             # 交易记录
    
    # 统计信息
    total_commission: float = 0        # 总手续费
    total_realized_pnl: float = 0      # 总已实现盈亏
    
    def __post_init__(self):
        """初始化验证"""
        if self.initial_capital <= 0:
            raise ValueError("初始资金必须大于0")
        if self.cash < 0:
            raise ValueError("现金不能为负数")
    
    @property
    def available_cash(self) -> float:
        """可用资金"""
        return self.cash - self.frozen_cash
    
    def freeze_cash(self, amount: float, order_id: str) -> bool:
        """冻结资金"""
        if amount <= 0:
            return False
        if self.available_cash < amount:
            return False
            
        self.frozen_cash += amount
        return True
    
    def unfreeze_cash(self, amount: float) -> None:
        """解冻资金"""
        self.frozen_cash = max(0, self.frozen_cash - amount)
    
    def get_position(self, symbol: str) -> Optional[Position]:
        """获取持仓信息"""
        return self.positions.get(symbol)
    
    def update_position(self, fill: Fill) -> None:
        """根据成交更新持仓"""
        symbol = fill.symbol
        quantity_change = fill.quantity if fill.side == 'BUY' else -fill.quantity
        
        if symbol not in self.positions:
            if quantity_change != 0:
                self.positions[symbol] = Position(
                    symbol=symbol,
                    quantity=quantity_change,
                    avg_price=fill.price,
                    strategy_id=fill.strategy_id
                )
        else:
            self.positions[symbol].update_position(quantity_change, fill.price)
            # 如果仓位清零，删除持仓记录
            if self.positions[symbol].is_empty:
                del self.positions[symbol]
        
        # 更新现金和统计信息
        if fill.side == 'BUY':
            self.cash -= fill.net_amount
        else:
            self.cash += fill.net_amount
            
        self.total_commission += fill.commission
        
        # 添加成交记录
        self.fills.append(fill)
    
    def get_total_value(self, current_prices: Dict[str, float]) -> float:
        """计算总资产"""
        total_market_value = sum(
            pos.quantity * current_prices.get(symbol, pos.avg_price)
            for symbol, pos in self.positions.items()
        )
        return self.cash + total_market_value
    
    def get_unrealized_pnl(self, current_prices: Dict[str, float]) -> float:
        """计算未实现盈亏"""
        return sum(
            pos.unrealized_pnl(current_prices.get(symbol, pos.avg_price))
            for pos in self.positions.values()
        )
    
    def get_total_pnl(self, current_prices: Dict[str, float]) -> float:
        """计算总盈亏"""
        return self.total_realized_pnl + self.get_unrealized_pnl(current_prices)
    
    def get_position_value(self, current_prices: Dict[str, float]) -> float:
        """计算持仓总市值"""
        return sum(
            abs(pos.quantity) * current_prices.get(symbol, pos.avg_price)
            for symbol, pos in self.positions.items()
        )
    
    def get_leverage(self, current_prices: Dict[str, float]) -> float:
        """计算杠杆率"""
        total_value = self.get_total_value(current_prices)
        position_value = self.get_position_value(current_prices)
        return position_value / total_value if total_value > 0 else 0
    
    def add_order(self, order: Order) -> None:
        """添加订单记录"""
        self.orders[order.order_id] = order
    
    def add_trade(self, trade: Trade) -> None:
        """添加交易记录"""
        self.trades.append(trade)
        if trade.is_closed:
            self.total_realized_pnl += trade.realized_pnl
    
    def __str__(self) -> str:
        """字符串表示"""
        return (f"Account(现金={self.cash:.2f} 冻结={self.frozen_cash:.2f} "
                f"持仓数={len(self.positions)} 已实现盈亏={self.total_realized_pnl:.2f})")