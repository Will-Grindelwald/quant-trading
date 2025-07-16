"""
事件类定义

事件驱动架构的核心，定义系统中流转的各种事件类型。
"""

from datetime import datetime
from dataclasses import dataclass
from typing import Any, Dict, Optional
from enum import Enum


class EventType(Enum):
    """事件类型枚举"""
    MARKET = "market"      # 行情更新事件
    SIGNAL = "signal"      # 策略信号事件  
    ORDER = "order"        # 订单事件
    FILL = "fill"          # 成交事件
    TIMER = "timer"        # 定时事件


@dataclass
class Event:
    """事件基类"""
    type: str              # 事件类型
    timestamp: datetime    # 事件时间
    data: Dict[str, Any]   # 事件数据


@dataclass  
class MarketEvent(Event):
    """行情更新事件"""
    symbol: str            # 标的代码
    bar_data: 'Bar'        # K线数据
    
    def __post_init__(self):
        self.type = EventType.MARKET.value


@dataclass
class SignalEvent(Event):
    """策略信号事件"""
    strategy_id: str       # 策略ID
    symbol: str            # 标的代码
    direction: str         # 方向: 'BUY', 'SELL', 'HOLD'
    strength: float        # 信号强度 0-1
    price: float           # 信号参考价格
    reason: str            # 信号原因
    
    def __post_init__(self):
        self.type = EventType.SIGNAL.value


@dataclass
class OrderEvent(Event):
    """订单事件"""
    order: 'Order'         # 订单对象
    
    def __post_init__(self):
        self.type = EventType.ORDER.value


@dataclass  
class FillEvent(Event):
    """成交事件"""
    fill: 'Fill'           # 成交对象
    
    def __post_init__(self):
        self.type = EventType.FILL.value


@dataclass
class TimerEvent(Event):
    """定时事件"""
    timer_id: str          # 定时器ID
    interval: int          # 间隔(秒)
    
    def __post_init__(self):
        self.type = EventType.TIMER.value