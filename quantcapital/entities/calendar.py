"""
交易日历实体定义

管理交易日和交易时间信息。
"""

from datetime import datetime, time, timedelta
from dataclasses import dataclass
from typing import List, Tuple, Set
import calendar


@dataclass
class Calendar:
    """交易日历"""
    market: str = 'A_SHARE'        # 市场名称
    
    # A股交易时间
    morning_start: time = time(9, 30)    # 上午开盘
    morning_end: time = time(11, 30)     # 上午收盘
    afternoon_start: time = time(13, 0)   # 下午开盘
    afternoon_end: time = time(15, 0)     # 下午收盘
    
    # 节假日集合（需要从外部配置加载）
    holidays: Set[datetime] = None
    
    def __post_init__(self):
        """初始化节假日集合"""
        if self.holidays is None:
            self.holidays = set()
    
    def is_trading_day(self, date: datetime) -> bool:
        """判断是否交易日"""
        # 周末不是交易日
        if date.weekday() >= 5:  # 周六=5, 周日=6
            return False
        
        # 节假日不是交易日
        date_only = date.date()
        return date_only not in {h.date() for h in self.holidays}
    
    def get_trading_hours(self, date: datetime) -> List[Tuple[datetime, datetime]]:
        """获取交易时间段"""
        if not self.is_trading_day(date):
            return []
        
        date_only = date.date()
        morning_session = (
            datetime.combine(date_only, self.morning_start),
            datetime.combine(date_only, self.morning_end)
        )
        afternoon_session = (
            datetime.combine(date_only, self.afternoon_start),
            datetime.combine(date_only, self.afternoon_end)
        )
        
        return [morning_session, afternoon_session]
    
    def get_next_trading_day(self, date: datetime) -> datetime:
        """获取下一个交易日"""
        next_date = date + timedelta(days=1)
        while not self.is_trading_day(next_date):
            next_date += timedelta(days=1)
        return next_date
    
    def get_prev_trading_day(self, date: datetime) -> datetime:
        """获取上一个交易日"""
        prev_date = date - timedelta(days=1)
        while not self.is_trading_day(prev_date):
            prev_date -= timedelta(days=1)
        return prev_date
    
    def is_trading_time(self, dt: datetime) -> bool:
        """判断是否在交易时间内"""
        if not self.is_trading_day(dt):
            return False
        
        current_time = dt.time()
        return (
            (self.morning_start <= current_time <= self.morning_end) or
            (self.afternoon_start <= current_time <= self.afternoon_end)
        )
    
    def get_trading_days_between(self, start_date: datetime, end_date: datetime) -> List[datetime]:
        """获取指定时间区间内的所有交易日"""
        trading_days = []
        current_date = start_date
        
        while current_date <= end_date:
            if self.is_trading_day(current_date):
                trading_days.append(current_date)
            current_date += timedelta(days=1)
        
        return trading_days
    
    def add_holiday(self, holiday_date: datetime) -> None:
        """添加节假日"""
        self.holidays.add(holiday_date)
    
    def remove_holiday(self, holiday_date: datetime) -> None:
        """移除节假日"""
        self.holidays.discard(holiday_date)
    
    def __str__(self) -> str:
        """字符串表示"""
        return f"Calendar({self.market}, {len(self.holidays)}个节假日)"