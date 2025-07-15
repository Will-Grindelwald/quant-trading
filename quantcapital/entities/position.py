"""
仓位实体定义

描述持仓信息，包含数量、成本价和盈亏计算。
"""

from dataclasses import dataclass
from typing import Optional


@dataclass
class Position:
    """持仓信息"""
    symbol: str                    # 股票代码
    quantity: int                  # 持仓数量(正数=多头，负数=空头)
    avg_price: float               # 平均成本价
    strategy_id: Optional[str] = None  # 策略ID（用于标记开仓策略）
    
    def __post_init__(self):
        """验证仓位数据"""
        if self.avg_price < 0:
            raise ValueError("平均成本价不能为负数")
    
    @property
    def is_long(self) -> bool:
        """是否多头仓位"""
        return self.quantity > 0
    
    @property
    def is_short(self) -> bool:
        """是否空头仓位"""
        return self.quantity < 0
    
    @property
    def is_empty(self) -> bool:
        """是否空仓"""
        return self.quantity == 0
    
    @property
    def market_value(self) -> float:
        """持仓市值（基于成本价）"""
        return abs(self.quantity) * self.avg_price
    
    def unrealized_pnl(self, current_price: float) -> float:
        """计算未实现盈亏"""
        if self.is_empty:
            return 0.0
        return self.quantity * (current_price - self.avg_price)
    
    def unrealized_pnl_pct(self, current_price: float) -> float:
        """计算未实现盈亏百分比"""
        if self.is_empty or self.avg_price == 0:
            return 0.0
        return (current_price - self.avg_price) / self.avg_price
    
    def update_position(self, quantity_change: int, fill_price: float) -> None:
        """更新仓位（基于成交记录）"""
        if quantity_change == 0:
            return
            
        new_quantity = self.quantity + quantity_change
        
        # 如果方向相同，计算新的平均成本价
        if (self.quantity >= 0 and quantity_change > 0) or (self.quantity <= 0 and quantity_change < 0):
            if new_quantity != 0:
                total_cost = (self.quantity * self.avg_price) + (quantity_change * fill_price)
                self.avg_price = abs(total_cost / new_quantity)
        # 如果是减仓或反向开仓，成本价保持不变（减仓）或使用新价格（反向）
        elif new_quantity * self.quantity < 0:  # 反向开仓
            self.avg_price = fill_price
        
        self.quantity = new_quantity
    
    def __str__(self) -> str:
        """字符串表示"""
        direction = "多头" if self.is_long else "空头" if self.is_short else "空仓"
        return f"Position({self.symbol} {direction} {abs(self.quantity)}@{self.avg_price:.2f})"