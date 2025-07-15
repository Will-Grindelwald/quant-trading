"""
数据更新进程

负责定时获取和更新市场数据，包括K线数据和技术指标计算。
独立进程运行，通过事件驱动与主系统通信。
"""

import time
import logging
from datetime import datetime, timedelta
from typing import List, Dict, Optional, Any
import pandas as pd
import numpy as np
from ..entities.bar import Bar, Frequency
from ..entities.event import TimerEvent
from ..engine.event_engine import EventEngine, EventHandler
from ..engine.timer import TimerManager
from .storage import ParquetStorage, SQLiteBusinessStorage


logger = logging.getLogger(__name__)


class TechnicalIndicators:
    """技术指标计算器"""
    
    @staticmethod
    def calculate_ma(prices: pd.Series, window: int) -> pd.Series:
        """计算移动平均线"""
        return prices.rolling(window=window, min_periods=1).mean()
    
    @staticmethod
    def calculate_macd(prices: pd.Series, fast=12, slow=26, signal=9) -> Dict[str, pd.Series]:
        """计算MACD指标"""
        exp_fast = prices.ewm(span=fast).mean()
        exp_slow = prices.ewm(span=slow).mean()
        dif = exp_fast - exp_slow
        dea = dif.ewm(span=signal).mean()
        histogram = (dif - dea) * 2
        
        return {
            'dif': dif,
            'dea': dea,
            'histogram': histogram
        }
    
    @staticmethod
    def calculate_rsi(prices: pd.Series, window: int = 14) -> pd.Series:
        """计算RSI指标"""
        delta = prices.diff()
        gain = (delta.where(delta > 0, 0)).rolling(window=window, min_periods=1).mean()
        loss = (-delta.where(delta < 0, 0)).rolling(window=window, min_periods=1).mean()
        rs = gain / loss
        rsi = 100 - (100 / (1 + rs))
        return rsi
    
    @staticmethod
    def calculate_bollinger_bands(prices: pd.Series, window: int = 20, std_dev: float = 2) -> Dict[str, pd.Series]:
        """计算布林带"""
        ma = prices.rolling(window=window, min_periods=1).mean()
        std = prices.rolling(window=window, min_periods=1).std()
        
        return {
            'upper': ma + (std * std_dev),
            'lower': ma - (std * std_dev),
            'middle': ma
        }


class DataSource:
    """数据源接口（模拟实现）"""
    
    def get_stock_list(self) -> List[str]:
        """获取股票列表"""
        # 模拟返回A股股票代码
        symbols = []
        
        # 沪市主板
        for i in range(600000, 600100):
            symbols.append(f"{i:06d}.SH")
        
        # 深市主板
        for i in range(000001, 000100):
            symbols.append(f"{i:06d}.SZ")
            
        # 创业板
        for i in range(300001, 300050):
            symbols.append(f"{i:06d}.SZ")
        
        return symbols
    
    def get_kline_data(self, symbols: List[str], frequency: str, 
                      start_date: str, end_date: str) -> pd.DataFrame:
        """获取K线数据（模拟实现）"""
        logger.info(f"模拟获取K线数据: {len(symbols)}支股票, {frequency}, {start_date}-{end_date}")
        
        # 模拟生成K线数据
        data = []
        base_price = 10.0
        
        for symbol in symbols[:10]:  # 只模拟前10支股票
            current_price = base_price + np.random.normal(0, 1)
            
            # 生成几天的数据
            for i in range(5):
                date = datetime.now() - timedelta(days=i)
                
                open_price = current_price + np.random.normal(0, 0.1)
                high_price = open_price + abs(np.random.normal(0, 0.2))
                low_price = open_price - abs(np.random.normal(0, 0.2))
                close_price = open_price + np.random.normal(0, 0.1)
                volume = int(np.random.uniform(1000000, 10000000))
                amount = volume * close_price
                
                data.append({
                    'symbol': symbol,
                    'datetime': date,
                    'open': round(open_price, 2),
                    'high': round(high_price, 2),
                    'low': round(low_price, 2),
                    'close': round(close_price, 2),
                    'volume': volume,
                    'amount': round(amount, 2),
                    'turnover': round(np.random.uniform(0.1, 5.0), 2)
                })
                
                current_price = close_price
        
        return pd.DataFrame(data)


class DataUpdater(EventHandler):
    """数据更新器"""
    
    def __init__(self, config: Dict[str, Any], event_engine: EventEngine):
        """
        初始化数据更新器
        
        Args:
            config: 配置字典
            event_engine: 事件引擎
        """
        super().__init__("DataUpdater")
        self.config = config
        self.event_engine = event_engine
        
        # 初始化存储
        data_root = config.get('data_root', './data')
        business_db_path = config.get('business_db_path', './data/business.db')
        self.parquet_storage = ParquetStorage(data_root)
        self.business_storage = SQLiteBusinessStorage(business_db_path)
        
        # 初始化数据源
        self.data_source = DataSource()
        
        # 技术指标计算器
        self.indicators = TechnicalIndicators()
        
        # 定时器管理器
        self.timer_manager = TimerManager()
        
        logger.info("数据更新器初始化完成")
    
    def start(self):
        """启动数据更新器"""
        super().start()
        
        # 创建定时任务
        update_interval = self.config.get('update_interval', 3600)  # 默认1小时
        
        self.timer_manager.create_timer(
            "kline_updater",
            interval=update_interval,
            callback=self._update_kline_data,
            repeat=True,
            start_delay=10  # 启动后10秒开始第一次更新
        )
        
        # 启动定时器
        self.timer_manager.start_all()
        
        logger.info(f"数据更新器已启动，更新间隔: {update_interval}秒")
    
    def stop(self):
        """停止数据更新器"""
        self.timer_manager.stop_all()
        super().stop()
        logger.info("数据更新器已停止")
    
    def handle_event(self, event):
        """处理事件"""
        if isinstance(event, TimerEvent):
            if event.timer_id == "kline_updater":
                self._update_kline_data()
    
    def _update_kline_data(self):
        """更新K线数据"""
        try:
            logger.info("开始更新K线数据")
            
            # 获取股票列表
            symbols = self._get_symbols_to_update()
            
            # 更新不同频率的数据
            frequencies = [Frequency.HOURLY, Frequency.DAILY]
            
            for frequency in frequencies:
                self._update_frequency_data(symbols, frequency)
            
            logger.info("K线数据更新完成")
            
        except Exception as e:
            logger.error(f"更新K线数据失败: {e}", exc_info=True)
    
    def _get_symbols_to_update(self) -> List[str]:
        """获取需要更新的股票列表"""
        # 从股票池获取
        symbols = self.business_storage.load_universe("default")
        
        if not symbols:
            # 如果股票池为空，获取所有股票
            all_symbols = self.data_source.get_stock_list()
            # 保存默认股票池
            self.business_storage.save_universe("default", all_symbols[:100])  # 限制数量
            symbols = all_symbols[:100]
        
        return symbols
    
    def _update_frequency_data(self, symbols: List[str], frequency: Frequency):
        """更新指定频率的数据"""
        try:
            logger.info(f"更新{frequency.value}数据: {len(symbols)}支股票")
            
            # 计算时间范围
            end_date = datetime.now().strftime('%Y-%m-%d')
            start_date = (datetime.now() - timedelta(days=30)).strftime('%Y-%m-%d')
            
            # 获取原始数据
            df = self.data_source.get_kline_data(symbols, frequency.value, start_date, end_date)
            
            if df.empty:
                logger.warning(f"未获取到{frequency.value}数据")
                return
            
            # 转换为Bar对象并计算技术指标
            bars = self._dataframe_to_bars_with_indicators(df, frequency)
            
            # 保存数据
            if self.parquet_storage.save_bars(bars, frequency):
                logger.info(f"{frequency.value}数据保存成功: {len(bars)}条")
            else:
                logger.error(f"{frequency.value}数据保存失败")
                
        except Exception as e:
            logger.error(f"更新{frequency.value}数据失败: {e}", exc_info=True)
    
    def _dataframe_to_bars_with_indicators(self, df: pd.DataFrame, frequency: Frequency) -> List[Bar]:
        """将DataFrame转换为Bar对象并计算技术指标"""
        bars = []
        
        # 按股票分组计算指标
        for symbol, group in df.groupby('symbol'):
            # 按时间排序
            group = group.sort_values('datetime').copy()
            
            # 计算技术指标
            close_prices = group['close']
            
            # 移动平均线
            group['ma5'] = self.indicators.calculate_ma(close_prices, 5)
            group['ma20'] = self.indicators.calculate_ma(close_prices, 20)
            group['ma60'] = self.indicators.calculate_ma(close_prices, 60)
            
            # MACD
            macd = self.indicators.calculate_macd(close_prices)
            group['macd_dif'] = macd['dif']
            group['macd_dea'] = macd['dea']
            group['macd_histogram'] = macd['histogram']
            
            # RSI
            group['rsi_14'] = self.indicators.calculate_rsi(close_prices, 14)
            
            # 布林带
            boll = self.indicators.calculate_bollinger_bands(close_prices, 20)
            group['boll_upper'] = boll['upper']
            group['boll_lower'] = boll['lower']
            
            # 转换为Bar对象
            for _, row in group.iterrows():
                bar = Bar(
                    symbol=row['symbol'],
                    datetime=pd.to_datetime(row['datetime']),
                    frequency=frequency,
                    open=row['open'],
                    high=row['high'],
                    low=row['low'],
                    close=row['close'],
                    volume=row['volume'],
                    amount=row['amount'],
                    turnover=row.get('turnover', 0.0),
                    ma5=row['ma5'] if pd.notna(row['ma5']) else None,
                    ma20=row['ma20'] if pd.notna(row['ma20']) else None,
                    ma60=row['ma60'] if pd.notna(row['ma60']) else None,
                    macd_dif=row['macd_dif'] if pd.notna(row['macd_dif']) else None,
                    macd_dea=row['macd_dea'] if pd.notna(row['macd_dea']) else None,
                    macd_histogram=row['macd_histogram'] if pd.notna(row['macd_histogram']) else None,
                    rsi_14=row['rsi_14'] if pd.notna(row['rsi_14']) else None,
                    boll_upper=row['boll_upper'] if pd.notna(row['boll_upper']) else None,
                    boll_lower=row['boll_lower'] if pd.notna(row['boll_lower']) else None,
                )
                bars.append(bar)
        
        return bars
    
    def update_kline_data_sync(self, symbols: List[str], frequency: Frequency) -> bool:
        """同步更新K线数据（外部调用）"""
        try:
            self._update_frequency_data(symbols, frequency)
            return True
        except Exception as e:
            logger.error(f"同步更新K线数据失败: {e}", exc_info=True)
            return False