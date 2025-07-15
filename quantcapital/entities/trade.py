"""
交易实体定义

描述一次完整的买卖交易，包含买入和卖出的详细信息。
"""

from datetime import datetime
from dataclasses import dataclass, field
from typing import Optional
from .fill import Fill
import uuid


@dataclass
class Trade:
    """完整交易记录（一次买入到卖出的完整周期）"""
    symbol: str                        # 股票代码
    strategy_id: str                   # 策略ID
    
    # 买入信息
    buy_fill: Fill                     # 买入成交
    buy_time: datetime                 # 买入时间
    buy_price: float                   # 买入价格
    buy_quantity: int                  # 买入数量
    
    # 卖出信息
    sell_fill: Optional[Fill] = None   # 卖出成交
    sell_time: Optional[datetime] = None   # 卖出时间
    sell_price: Optional[float] = None     # 卖出价格
    sell_quantity: Optional[int] = None    # 卖出数量
    
    # 交易结果
    realized_pnl: float = 0            # 已实现盈亏
    total_commission: float = 0        # 总手续费
    status: str = 'OPEN'               # 状态: 'OPEN', 'CLOSED'
    
    trade_id: str = field(default_factory=lambda: str(uuid.uuid4()))  # 交易ID
    
    def __post_init__(self):
        """初始化交易信息"""
        self.buy_time = self.buy_fill.timestamp
        self.buy_price = self.buy_fill.price
        self.buy_quantity = self.buy_fill.quantity
        self.total_commission = self.buy_fill.commission
    
    def close_trade(self, sell_fill: Fill) -> None:
        """关闭交易"""
        if self.status == 'CLOSED':
            raise ValueError("交易已关闭，无法重复关闭")
            
        if sell_fill.symbol != self.symbol:
            raise ValueError("卖出标的与买入标的不匹配")
            
        self.sell_fill = sell_fill
        self.sell_time = sell_fill.timestamp
        self.sell_price = sell_fill.price
        self.sell_quantity = sell_fill.quantity
        self.total_commission += sell_fill.commission
        
        # 计算已实现盈亏
        self.realized_pnl = (
            (self.sell_price - self.buy_price) * min(self.buy_quantity, self.sell_quantity)
            - self.total_commission
        )
        
        self.status = 'CLOSED'
    
    @property
    def is_open(self) -> bool:
        """是否为开放状态"""
        return self.status == 'OPEN'
    
    @property
    def is_closed(self) -> bool:
        """是否已关闭"""
        return self.status == 'CLOSED'
    
    @property
    def holding_days(self) -> Optional[float]:
        """持有天数"""
        if not self.is_closed:
            return None
        return (self.sell_time - self.buy_time).total_seconds() / 86400
    
    @property
    def return_pct(self) -> Optional[float]:
        """收益率"""
        if not self.is_closed:
            return None
        buy_cost = self.buy_quantity * self.buy_price
        return self.realized_pnl / buy_cost if buy_cost > 0 else 0
    
    def __str__(self) -> str:
        """字符串表示"""
        status_str = f"开仓 {self.buy_quantity}@{self.buy_price:.2f}"
        if self.is_closed:
            status_str += f" -> 平仓 {self.sell_quantity}@{self.sell_price:.2f} 盈亏={self.realized_pnl:.2f}"
        return f"Trade({self.symbol} {status_str})"