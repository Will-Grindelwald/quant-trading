"""
信号实体定义

策略产生的交易信号，包含交易方向、强度和原因等信息。
"""

from datetime import datetime
from dataclasses import dataclass
from enum import Enum


class SignalDirection(Enum):
    """信号方向枚举"""
    BUY = "BUY"      # 买入
    SELL = "SELL"    # 卖出  
    HOLD = "HOLD"    # 持有


@dataclass
class Signal:
    """交易信号"""
    strategy_id: str         # 策略ID
    symbol: str              # 股票代码
    direction: SignalDirection # 方向: BUY, SELL, HOLD
    strength: float          # 信号强度 0-1
    timestamp: datetime      # 信号时间
    price: float             # 信号参考价格（策略决策时的价格）
    reason: str              # 信号原因，需要详尽，用于人工复盘
    
    def __post_init__(self):
        """验证信号强度范围"""
        if not 0 <= self.strength <= 1:
            raise ValueError("信号强度必须在0-1之间")
            
    def is_valid(self) -> bool:
        """检查信号是否有效"""
        return (
            self.symbol and 
            self.direction in SignalDirection and
            0 <= self.strength <= 1 and
            self.price > 0
        )