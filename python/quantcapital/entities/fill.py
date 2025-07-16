"""
成交实体定义

描述订单的成交记录，包含成交价格、数量、手续费等信息。
"""

from datetime import datetime
from dataclasses import dataclass, field
from typing import Optional
import uuid


@dataclass
class Fill:
    """订单成交记录"""
    order_id: str                   # 关联订单ID
    symbol: str                     # 股票代码
    side: str                       # 买卖方向 ('BUY', 'SELL')
    quantity: int                   # 成交数量
    price: float                    # 成交价格
    commission: float               # 手续费
    timestamp: datetime             # 成交时间
    fill_id: str = field(default_factory=lambda: str(uuid.uuid4()))  # 成交ID
    strategy_id: Optional[str] = None  # 策略ID
    
    def __post_init__(self):
        """验证成交数据"""
        if self.quantity <= 0:
            raise ValueError("成交数量必须大于0")
        if self.price <= 0:
            raise ValueError("成交价格必须大于0")
        if self.commission < 0:
            raise ValueError("手续费不能为负数")
    
    @property
    def total_amount(self) -> float:
        """成交总金额（不含手续费）"""
        return self.quantity * self.price
    
    @property
    def net_amount(self) -> float:
        """净成交金额（含手续费）"""
        if self.side == 'BUY':
            return self.total_amount + self.commission  # 买入时加手续费
        else:
            return self.total_amount - self.commission  # 卖出时减手续费
    
    def __str__(self) -> str:
        """字符串表示"""
        return (f"Fill({self.symbol} {self.side} {self.quantity}@{self.price:.2f} "
                f"commission={self.commission:.2f} at {self.timestamp})")