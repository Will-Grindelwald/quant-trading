"""
订单实体定义

描述交易订单的详细信息，包含状态管理和生命周期追踪。
"""

from datetime import datetime
from dataclasses import dataclass, field
from enum import Enum
from typing import Optional
import uuid


class OrderType(Enum):
    """订单类型枚举"""
    LIMIT = "LIMIT"      # 限价委托（A股主要类型）
    MARKET = "MARKET"    # 市价委托


class OrderSide(Enum):
    """买卖方向枚举"""
    BUY = "BUY"          # 买入
    SELL = "SELL"        # 卖出


class OrderStatus(Enum):
    """订单状态枚举"""
    PENDING = "PENDING"      # 待提交
    SUBMITTED = "SUBMITTED"  # 已提交
    FILLED = "FILLED"        # 已成交
    PARTIALLY_FILLED = "PARTIALLY_FILLED"  # 部分成交
    CANCELLED = "CANCELLED"  # 已撤销
    REJECTED = "REJECTED"    # 被拒绝


@dataclass
class Order:
    """交易订单"""
    symbol: str                    # 股票代码
    order_type: OrderType          # 订单类型
    side: OrderSide                # 买卖方向
    quantity: int                  # 数量(股)
    price: float                   # 限价
    strategy_id: Optional[str] = None  # 策略ID
    order_id: str = field(default_factory=lambda: str(uuid.uuid4()))  # 订单ID
    status: OrderStatus = OrderStatus.PENDING  # 状态
    created_time: datetime = field(default_factory=datetime.now)  # 创建时间
    submitted_time: Optional[datetime] = None   # 提交时间
    filled_time: Optional[datetime] = None      # 成交时间
    filled_quantity: int = 0                    # 已成交数量
    _total_filled_amount: float = 0.0           # 总成交金额（用于计算平均价格）
    
    def __post_init__(self):
        """验证订单参数"""
        if self.quantity <= 0:
            raise ValueError("订单数量必须大于0")
        if self.price <= 0:
            raise ValueError("订单价格必须大于0")
    
    def is_active(self) -> bool:
        """检查订单是否活跃（未完成）"""
        return self.status in [OrderStatus.PENDING, OrderStatus.SUBMITTED, OrderStatus.PARTIALLY_FILLED]
    
    def is_filled(self) -> bool:
        """检查订单是否完全成交"""
        return self.status == OrderStatus.FILLED
    
    @property
    def remaining_quantity(self) -> int:
        """获取剩余未成交数量"""
        return self.quantity - self.filled_quantity
    
    @property
    def avg_filled_price(self) -> float:
        """获取平均成交价格"""
        if self.filled_quantity == 0:
            return 0.0
        return self._total_filled_amount / self.filled_quantity
    
    def fill_order(self, fill_quantity: int, fill_price: float) -> None:
        """部分成交订单"""
        if fill_quantity <= 0:
            raise ValueError("成交数量必须大于0")
        if fill_quantity > self.remaining_quantity:
            raise ValueError("成交数量不能超过剩余数量")
            
        self.filled_quantity += fill_quantity
        self._total_filled_amount += fill_quantity * fill_price
        
        if self.filled_quantity == self.quantity:
            self.status = OrderStatus.FILLED
            self.filled_time = datetime.now()
        else:
            self.status = OrderStatus.PARTIALLY_FILLED
    
    def fill(self, fill_quantity: int, fill_price: float, commission: float = 0.0) -> None:
        """成交订单（测试兼容方法）"""
        self.fill_order(fill_quantity, fill_price)