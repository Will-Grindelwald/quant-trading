"""
市场数据模拟器

在回测中模拟逐日行情数据推送。
"""

import logging
from datetime import datetime
from typing import List
from ..entities.event import MarketEvent
from ..entities.bar import Bar, Frequency
from ..data import DataHandler
from ..engine import EventEngine


logger = logging.getLogger(__name__)


class MarketDataSimulator:
    """市场数据模拟器"""
    
    def __init__(self, event_engine: EventEngine, data_handler: DataHandler):
        """
        初始化市场数据模拟器
        
        Args:
            event_engine: 事件引擎
            data_handler: 数据处理器
        """
        self.event_engine = event_engine
        self.data_handler = data_handler
        
        logger.info("市场数据模拟器初始化完成")
    
    def simulate_trading_day(self, trading_date: datetime, symbols: List[str]):
        """
        模拟一个交易日的行情数据
        
        Args:
            trading_date: 交易日期
            symbols: 股票列表
        """
        try:
            logger.debug(f"模拟交易日行情: {trading_date.date()}, {len(symbols)}支股票")
            
            # 模拟市场开盘前的准备工作
            self._simulate_market_open(trading_date, symbols)
            
            # 模拟日内行情推送
            self._simulate_intraday_data(trading_date, symbols)
            
            # 模拟收盘
            self._simulate_market_close(trading_date, symbols)
            
        except Exception as e:
            logger.error(f"模拟交易日行情失败 {trading_date.date()}: {e}")
    
    def _simulate_market_open(self, trading_date: datetime, symbols: List[str]):
        """模拟市场开盘"""
        logger.debug(f"模拟市场开盘: {trading_date.date()}")
        
        # 这里可以添加开盘前的处理逻辑
        # 比如处理隔夜新闻、公告等事件
        pass
    
    def _simulate_intraday_data(self, trading_date: datetime, symbols: List[str]):
        """模拟日内行情数据"""
        # 获取当日的K线数据
        for symbol in symbols:
            try:
                # 获取该股票当日的K线数据
                latest_bar = self.data_handler.get_latest_bar(symbol, Frequency.DAILY)
                
                if latest_bar and latest_bar.datetime.date() == trading_date.date():
                    # 发送市场行情事件
                    self._send_market_event(symbol, latest_bar)
                    
            except Exception as e:
                logger.debug(f"获取 {symbol} 行情数据失败: {e}")
                continue
    
    def _simulate_market_close(self, trading_date: datetime, symbols: List[str]):
        """模拟市场收盘"""
        logger.debug(f"模拟市场收盘: {trading_date.date()}")
        
        # 这里可以添加收盘后的处理逻辑
        # 比如结算、资金清算等
        pass
    
    def _send_market_event(self, symbol: str, bar: Bar):
        """发送市场行情事件"""
        market_event = MarketEvent(
            timestamp=bar.datetime,
            data={'bar_id': f"{symbol}_{bar.datetime}"},
            symbol=symbol,
            bar_data=bar
        )
        
        self.event_engine.put(market_event)
        logger.debug(f"发送行情事件: {symbol} {bar.datetime} 收盘={bar.close:.2f}")
    
    def simulate_tick_data(self, trading_date: datetime, symbol: str):
        """
        模拟Tick级别数据（未来扩展）
        
        Args:
            trading_date: 交易日期
            symbol: 股票代码
        """
        # 预留接口，用于未来支持更高频率的数据模拟
        pass