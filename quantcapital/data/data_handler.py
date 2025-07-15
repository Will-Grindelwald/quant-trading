"""
数据处理器

提供统一的数据访问接口，避免未来函数，支持回测和实盘两种模式。
"""

import logging
from abc import ABC, abstractmethod
from datetime import datetime, timedelta
from typing import List, Dict, Optional, Any
import pandas as pd
from ..entities.bar import Bar, Frequency
from ..entities.calendar import Calendar
from ..entities.universe import Universe
from .storage import ParquetStorage, DuckDBStorage, SQLiteBusinessStorage


logger = logging.getLogger(__name__)


class DataHandler(ABC):
    """数据处理器基类"""
    
    def __init__(self):
        self.current_time: Optional[datetime] = None
        
    @abstractmethod
    def get_bars(self, symbols: List[str], start_date: datetime, 
                 end_date: datetime, frequency: Frequency) -> pd.DataFrame:
        """获取历史K线数据"""
        pass
    
    @abstractmethod
    def get_latest_bar(self, symbol: str, frequency: Frequency) -> Optional[Bar]:
        """获取最新的K线数据"""
        pass
    
    @abstractmethod
    def get_latest_bars(self, symbols: List[str], frequency: Frequency, 
                       count: int = 1) -> Dict[str, List[Bar]]:
        """获取多个股票的最新K线数据"""
        pass
    
    @abstractmethod
    def get_universe(self, date: datetime) -> List[str]:
        """获取指定日期的股票池"""
        pass
    
    @abstractmethod
    def is_trading_day(self, date: datetime) -> bool:
        """判断是否交易日"""
        pass
    
    def set_current_time(self, dt: datetime):
        """设置当前时间（避免未来函数）"""
        self.current_time = dt


class BacktestDataHandler(DataHandler):
    """回测数据处理器"""
    
    def __init__(self, data_root: str, business_db_path: str):
        """
        初始化回测数据处理器
        
        Args:
            data_root: K线数据根目录
            business_db_path: 业务数据库路径
        """
        super().__init__()
        self.parquet_storage = ParquetStorage(data_root)
        self.duckdb_storage = DuckDBStorage()  # 内存数据库用于快速查询
        self.business_storage = SQLiteBusinessStorage(business_db_path)
        self.calendar = Calendar()
        self._cached_data: Dict[str, Any] = {}
        
        logger.info("回测数据处理器初始化完成")
    
    def load_data_to_memory(self, symbols: List[str], start_date: datetime, 
                          end_date: datetime, frequency: Frequency):
        """预加载数据到内存（回测开始前调用）"""
        logger.info(f"预加载数据到内存: {len(symbols)}支股票, "
                   f"{start_date.date()}-{end_date.date()}, {frequency.value}")
        
        # 从Parquet加载数据到DuckDB
        df = self.parquet_storage.load_bars(symbols, start_date, end_date, frequency)
        if not df.empty:
            bars = self._dataframe_to_bars(df)
            self.duckdb_storage.save_bars(bars, frequency)
            logger.info(f"数据预加载完成: {len(bars)}条K线")
        else:
            logger.warning("未找到任何数据")
    
    def get_bars(self, symbols: List[str], start_date: datetime, 
                 end_date: datetime, frequency: Frequency) -> pd.DataFrame:
        """获取历史K线数据"""
        # 确保不访问未来数据
        if self.current_time and end_date > self.current_time:
            end_date = self.current_time
            
        return self.duckdb_storage.load_bars(symbols, start_date, end_date, frequency)
    
    def get_latest_bar(self, symbol: str, frequency: Frequency) -> Optional[Bar]:
        """获取最新的K线数据"""
        if not self.current_time:
            logger.warning("当前时间未设置")
            return None
            
        # 获取当前时间之前的最新数据
        df = self.get_bars([symbol], 
                          self.current_time - timedelta(days=30),  # 向前30天查找
                          self.current_time, 
                          frequency)
        
        if df.empty:
            return None
            
        # 返回最新的一条
        latest_row = df.iloc[-1]
        return self._row_to_bar(latest_row)
    
    def get_latest_bars(self, symbols: List[str], frequency: Frequency, 
                       count: int = 1) -> Dict[str, List[Bar]]:
        """获取多个股票的最新K线数据"""
        if not self.current_time:
            logger.warning("当前时间未设置")
            return {}
            
        result = {}
        
        # 获取所有股票的数据
        df = self.get_bars(symbols,
                          self.current_time - timedelta(days=60),  # 向前60天查找
                          self.current_time,
                          frequency)
        
        # 按股票分组并获取最新数据
        for symbol in symbols:
            symbol_df = df[df['symbol'] == symbol].tail(count)
            if not symbol_df.empty:
                bars = [self._row_to_bar(row) for _, row in symbol_df.iterrows()]
                result[symbol] = bars
            else:
                result[symbol] = []
        
        return result
    
    def get_universe(self, date: datetime) -> List[str]:
        """获取指定日期的股票池"""
        # 简化实现，返回默认股票池
        return self.business_storage.load_universe("default")
    
    def is_trading_day(self, date: datetime) -> bool:
        """判断是否交易日"""
        return self.calendar.is_trading_day(date)
    
    def _dataframe_to_bars(self, df: pd.DataFrame) -> List[Bar]:
        """将DataFrame转换为Bar对象列表"""
        bars = []
        for _, row in df.iterrows():
            bar = Bar(
                symbol=row['symbol'],
                datetime=pd.to_datetime(row['datetime']),
                frequency=Frequency(row['frequency']),
                open=row['open'],
                high=row['high'],
                low=row['low'],
                close=row['close'],
                volume=row['volume'],
                amount=row['amount'],
                turnover=row.get('turnover', 0.0),
                ma5=row.get('ma5'),
                ma20=row.get('ma20'),
                ma60=row.get('ma60'),
                macd_dif=row.get('macd_dif'),
                macd_dea=row.get('macd_dea'),
                macd_histogram=row.get('macd_histogram'),
                rsi_14=row.get('rsi_14'),
                boll_upper=row.get('boll_upper'),
                boll_lower=row.get('boll_lower'),
                market_cap=row.get('market_cap'),
                circulating_market_cap=row.get('circulating_market_cap'),
                is_st=row.get('is_st', False),
                is_new_stock=row.get('is_new_stock', False)
            )
            bars.append(bar)
        return bars
    
    def _row_to_bar(self, row: pd.Series) -> Bar:
        """将DataFrame行转换为Bar对象"""
        return Bar(
            symbol=row['symbol'],
            datetime=pd.to_datetime(row['datetime']),
            frequency=Frequency(row['frequency']),
            open=row['open'],
            high=row['high'],
            low=row['low'],
            close=row['close'],
            volume=row['volume'],
            amount=row['amount'],
            turnover=row.get('turnover', 0.0),
            ma5=row.get('ma5'),
            ma20=row.get('ma20'),
            ma60=row.get('ma60'),
            macd_dif=row.get('macd_dif'),
            macd_dea=row.get('macd_dea'),
            macd_histogram=row.get('macd_histogram'),
            rsi_14=row.get('rsi_14'),
            boll_upper=row.get('boll_upper'),
            boll_lower=row.get('boll_lower'),
            market_cap=row.get('market_cap'),
            circulating_market_cap=row.get('circulating_market_cap'),
            is_st=row.get('is_st', False),
            is_new_stock=row.get('is_new_stock', False)
        )


class LiveDataHandler(DataHandler):
    """实盘数据处理器"""
    
    def __init__(self, data_root: str, business_db_path: str):
        """
        初始化实盘数据处理器
        
        Args:
            data_root: K线数据根目录
            business_db_path: 业务数据库路径
        """
        super().__init__()
        self.parquet_storage = ParquetStorage(data_root)
        self.business_storage = SQLiteBusinessStorage(business_db_path)
        self.calendar = Calendar()
        self.current_time = datetime.now()
        
        logger.info("实盘数据处理器初始化完成")
    
    def get_bars(self, symbols: List[str], start_date: datetime, 
                 end_date: datetime, frequency: Frequency) -> pd.DataFrame:
        """获取历史K线数据"""
        # 实盘模式确保不访问未来数据
        now = datetime.now()
        if end_date > now:
            end_date = now
            
        return self.parquet_storage.load_bars(symbols, start_date, end_date, frequency)
    
    def get_latest_bar(self, symbol: str, frequency: Frequency) -> Optional[Bar]:
        """获取最新的K线数据"""
        now = datetime.now()
        
        # 获取最近的数据
        df = self.get_bars([symbol], 
                          now - timedelta(days=7),  # 向前7天查找
                          now, 
                          frequency)
        
        if df.empty:
            return None
            
        # 返回最新的一条
        latest_row = df.iloc[-1]
        return self._row_to_bar(latest_row)
    
    def get_latest_bars(self, symbols: List[str], frequency: Frequency, 
                       count: int = 1) -> Dict[str, List[Bar]]:
        """获取多个股票的最新K线数据"""
        now = datetime.now()
        result = {}
        
        # 获取所有股票的数据
        df = self.get_bars(symbols,
                          now - timedelta(days=14),  # 向前14天查找
                          now,
                          frequency)
        
        # 按股票分组并获取最新数据
        for symbol in symbols:
            symbol_df = df[df['symbol'] == symbol].tail(count)
            if not symbol_df.empty:
                bars = [self._row_to_bar(row) for _, row in symbol_df.iterrows()]
                result[symbol] = bars
            else:
                result[symbol] = []
        
        return result
    
    def get_universe(self, date: datetime) -> List[str]:
        """获取指定日期的股票池"""
        return self.business_storage.load_universe("default")
    
    def is_trading_day(self, date: datetime) -> bool:
        """判断是否交易日"""
        return self.calendar.is_trading_day(date)
    
    def _row_to_bar(self, row: pd.Series) -> Bar:
        """将DataFrame行转换为Bar对象"""
        return Bar(
            symbol=row['symbol'],
            datetime=pd.to_datetime(row['datetime']),
            frequency=Frequency(row['frequency']),
            open=row['open'],
            high=row['high'],
            low=row['low'],
            close=row['close'],
            volume=row['volume'],
            amount=row['amount'],
            turnover=row.get('turnover', 0.0),
            ma5=row.get('ma5'),
            ma20=row.get('ma20'),
            ma60=row.get('ma60'),
            macd_dif=row.get('macd_dif'),
            macd_dea=row.get('macd_dea'),
            macd_histogram=row.get('macd_histogram'),
            rsi_14=row.get('rsi_14'),
            boll_upper=row.get('boll_upper'),
            boll_lower=row.get('boll_lower'),
            market_cap=row.get('market_cap'),
            circulating_market_cap=row.get('circulating_market_cap'),
            is_st=row.get('is_st', False),
            is_new_stock=row.get('is_new_stock', False)
        )