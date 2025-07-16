"""
K线数据实体定义

包含OHLC基础数据、技术指标和基本面数据的完整K线数据结构。
"""

from datetime import datetime
from dataclasses import dataclass
from typing import Optional
from enum import Enum


class Frequency(Enum):
    """K线频率枚举"""
    HOURLY = "H"     # 小时线
    DAILY = "D"      # 日线
    WEEKLY = "W"     # 周线


@dataclass
class Bar:
    """K线数据"""
    symbol: str                # 股票代码
    datetime: datetime         # 时间
    frequency: Frequency       # 频率
    
    # 基础OHLC数据
    open: float               # 开盘价
    high: float               # 最高价
    low: float                # 最低价
    close: float              # 收盘价
    volume: int               # 成交量
    amount: float             # 成交额
    turnover: float = 0.0     # 换手率
    
    # 技术指标
    ma5: Optional[float] = None              # 5日均线
    ma20: Optional[float] = None             # 20日均线
    ma60: Optional[float] = None             # 60日均线
    macd_dif: Optional[float] = None         # MACD DIF线
    macd_dea: Optional[float] = None         # MACD DEA线
    macd_histogram: Optional[float] = None   # MACD柱状图
    rsi_14: Optional[float] = None           # 14日RSI(0-100)
    boll_upper: Optional[float] = None       # 布林带上轨
    boll_lower: Optional[float] = None       # 布林带下轨
    
    # 基本面数据
    market_cap: Optional[float] = None               # 市值
    circulating_market_cap: Optional[float] = None  # 流通市值
    is_st: bool = False                              # 是否ST
    is_new_stock: bool = False                       # 是否新股次新股
    
    def __post_init__(self):
        """验证K线数据的有效性"""
        if self.high < max(self.open, self.close):
            raise ValueError("最高价不能低于开盘价或收盘价")
        if self.low > min(self.open, self.close):
            raise ValueError("最低价不能高于开盘价或收盘价")
        if self.volume < 0:
            raise ValueError("成交量不能为负数")
        if self.amount < 0:
            raise ValueError("成交额不能为负数")
    
    @property
    def is_bullish(self) -> bool:
        """是否为阳线"""
        return self.close > self.open
    
    @property
    def is_bearish(self) -> bool:
        """是否为阴线"""
        return self.close < self.open
    
    @property
    def body_size(self) -> float:
        """实体大小（绝对值）"""
        return abs(self.close - self.open)
    
    @property
    def body_size_pct(self) -> float:
        """实体大小百分比"""
        return self.body_size / self.open if self.open > 0 else 0
    
    @property
    def upper_shadow(self) -> float:
        """上影线长度"""
        return self.high - max(self.open, self.close)
    
    @property
    def lower_shadow(self) -> float:
        """下影线长度"""
        return min(self.open, self.close) - self.low
    
    @property
    def change_pct(self) -> float:
        """涨跌幅"""
        return (self.close - self.open) / self.open if self.open > 0 else 0
    
    @property
    def has_technical_indicators(self) -> bool:
        """是否包含技术指标"""
        return any([
            self.ma5 is not None,
            self.ma20 is not None,
            self.macd_dif is not None,
            self.rsi_14 is not None
        ])
    
    def __str__(self) -> str:
        """字符串表示"""
        return (f"Bar({self.symbol} {self.datetime} "
                f"OHLC={self.open:.2f}/{self.high:.2f}/{self.low:.2f}/{self.close:.2f} "
                f"Vol={self.volume})")