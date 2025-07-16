"""
数据存储组件

提供Parquet文件存储和DuckDB内存数据库存储，
支持高效的时间序列数据存取和分析查询。
"""

import os
import sqlite3
import logging
from pathlib import Path
from datetime import datetime
from typing import List, Dict, Optional, Any
from abc import ABC, abstractmethod
import pandas as pd
from ..entities.bar import Bar, Frequency


logger = logging.getLogger(__name__)


class Storage(ABC):
    """存储接口基类"""
    
    @abstractmethod
    def save_bars(self, bars: List[Bar], frequency: Frequency) -> bool:
        """保存K线数据"""
        pass
    
    @abstractmethod
    def load_bars(self, symbols: List[str], start_date: datetime, 
                  end_date: datetime, frequency: Frequency) -> pd.DataFrame:
        """加载K线数据"""
        pass


class ParquetStorage(Storage):
    """Parquet文件存储"""
    
    def __init__(self, data_root: str):
        """
        初始化Parquet存储
        
        Args:
            data_root: 数据根目录
        """
        self.data_root = Path(data_root)
        self.kline_root = self.data_root / "kline"
        
        # 创建目录结构
        self.kline_root.mkdir(parents=True, exist_ok=True)
        logger.info(f"Parquet存储初始化完成: {self.data_root}")
    
    def _get_partition_path(self, frequency: Frequency, year: int) -> Path:
        """获取分区路径"""
        if frequency == Frequency.WEEKLY:
            # 周线数据量小，不按年分区
            return self.kline_root / f"frequency={frequency.value}"
        else:
            return self.kline_root / f"frequency={frequency.value}" / f"year={year}"
    
    def save_bars(self, bars: List[Bar], frequency: Frequency) -> bool:
        """保存K线数据到Parquet文件"""
        if not bars:
            return True
        
        try:
            # 按年分组数据
            yearly_data = {}
            for bar in bars:
                year = bar.datetime.year
                if year not in yearly_data:
                    yearly_data[year] = []
                yearly_data[year].append(bar)
            
            # 逐年保存
            for year, year_bars in yearly_data.items():
                df = self._bars_to_dataframe(year_bars)
                partition_path = self._get_partition_path(frequency, year)
                partition_path.mkdir(parents=True, exist_ok=True)
                
                file_path = partition_path / "data.parquet"
                
                # 如果文件已存在，合并数据去重
                if file_path.exists():
                    existing_df = pd.read_parquet(file_path)
                    df = pd.concat([existing_df, df]).drop_duplicates(
                        subset=['symbol', 'datetime'], keep='last'
                    ).sort_values(['symbol', 'datetime'])
                
                df.to_parquet(file_path, index=False, compression='snappy')
                logger.debug(f"保存K线数据: {file_path}, {len(df)}条记录")
            
            return True
            
        except Exception as e:
            logger.error(f"保存K线数据失败: {e}", exc_info=True)
            return False
    
    def load_bars(self, symbols: List[str], start_date: datetime, 
                  end_date: datetime, frequency: Frequency) -> pd.DataFrame:
        """从Parquet文件加载K线数据"""
        try:
            dataframes = []
            
            # 确定需要读取的年份范围
            start_year = start_date.year
            end_year = end_date.year
            
            for year in range(start_year, end_year + 1):
                partition_path = self._get_partition_path(frequency, year)
                file_path = partition_path / "data.parquet"
                
                if file_path.exists():
                    df = pd.read_parquet(file_path)
                    dataframes.append(df)
            
            if not dataframes:
                logger.warning(f"未找到数据: symbols={symbols[:5]}{'...' if len(symbols)>5 else ''}, "
                             f"时间范围={start_date.date()}-{end_date.date()}")
                return pd.DataFrame()
            
            # 合并所有数据
            df = pd.concat(dataframes, ignore_index=True)
            
            # 过滤条件
            df['datetime'] = pd.to_datetime(df['datetime'])
            mask = (
                (df['symbol'].isin(symbols)) &
                (df['datetime'] >= start_date) &
                (df['datetime'] <= end_date)
            )
            result = df[mask].sort_values(['symbol', 'datetime']).reset_index(drop=True)
            
            logger.debug(f"加载K线数据完成: {len(result)}条记录")
            return result
            
        except Exception as e:
            logger.error(f"加载K线数据失败: {e}", exc_info=True)
            return pd.DataFrame()
    
    def _bars_to_dataframe(self, bars: List[Bar]) -> pd.DataFrame:
        """将Bar对象列表转换为DataFrame"""
        data = []
        for bar in bars:
            data.append({
                'symbol': bar.symbol,
                'datetime': bar.datetime,
                'frequency': bar.frequency.value,
                'open': bar.open,
                'high': bar.high,
                'low': bar.low,
                'close': bar.close,
                'volume': bar.volume,
                'amount': bar.amount,
                'turnover': bar.turnover,
                'ma5': bar.ma5,
                'ma20': bar.ma20,
                'ma60': bar.ma60,
                'macd_dif': bar.macd_dif,
                'macd_dea': bar.macd_dea,
                'macd_histogram': bar.macd_histogram,
                'rsi_14': bar.rsi_14,
                'boll_upper': bar.boll_upper,
                'boll_lower': bar.boll_lower,
                'market_cap': bar.market_cap,
                'circulating_market_cap': bar.circulating_market_cap,
                'is_st': bar.is_st,
                'is_new_stock': bar.is_new_stock
            })
        return pd.DataFrame(data)


class DuckDBStorage(Storage):
    """DuckDB内存数据库存储（用于回测和分析）"""
    
    def __init__(self, db_path: Optional[str] = None):
        """
        初始化DuckDB存储
        
        Args:
            db_path: 数据库文件路径，None表示内存数据库
        """
        self.db_path = db_path
        self._connection = None
        self._create_tables()
        logger.info(f"DuckDB存储初始化完成: {db_path or '内存数据库'}")
    
    def _get_connection(self):
        """获取数据库连接"""
        if self._connection is None:
            try:
                import duckdb
                self._connection = duckdb.connect(self.db_path)
            except ImportError:
                logger.error("DuckDB未安装，请运行: pip install duckdb")
                raise
        return self._connection
    
    def _create_tables(self):
        """创建表结构"""
        conn = self._get_connection()
        
        create_sql = """
        CREATE TABLE IF NOT EXISTS kline_data (
            symbol VARCHAR,
            datetime TIMESTAMP,
            frequency VARCHAR,
            open DOUBLE,
            high DOUBLE,
            low DOUBLE,
            close DOUBLE,
            volume BIGINT,
            amount DOUBLE,
            turnover DOUBLE,
            ma5 DOUBLE,
            ma20 DOUBLE,
            ma60 DOUBLE,
            macd_dif DOUBLE,
            macd_dea DOUBLE,
            macd_histogram DOUBLE,
            rsi_14 DOUBLE,
            boll_upper DOUBLE,
            boll_lower DOUBLE,
            market_cap DOUBLE,
            circulating_market_cap DOUBLE,
            is_st BOOLEAN,
            is_new_stock BOOLEAN,
            PRIMARY KEY (symbol, datetime, frequency)
        )
        """
        conn.execute(create_sql)
    
    def save_bars(self, bars: List[Bar], frequency: Frequency) -> bool:
        """保存K线数据到DuckDB"""
        if not bars:
            return True
        
        try:
            df = self._bars_to_dataframe(bars)
            conn = self._get_connection()
            
            # 使用INSERT OR REPLACE语法
            conn.execute("DELETE FROM kline_data WHERE symbol = ? AND datetime = ? AND frequency = ?",
                        [(bar.symbol, bar.datetime, frequency.value) for bar in bars])
            
            conn.register('temp_bars', df)
            conn.execute("""
                INSERT INTO kline_data 
                SELECT * FROM temp_bars
            """)
            
            logger.debug(f"保存K线数据到DuckDB: {len(bars)}条记录")
            return True
            
        except Exception as e:
            logger.error(f"保存K线数据到DuckDB失败: {e}", exc_info=True)
            return False
    
    def load_bars(self, symbols: List[str], start_date: datetime, 
                  end_date: datetime, frequency: Frequency) -> pd.DataFrame:
        """从DuckDB加载K线数据"""
        try:
            conn = self._get_connection()
            
            # 构建SQL查询
            symbols_str = "','".join(symbols)
            sql = f"""
                SELECT * FROM kline_data 
                WHERE symbol IN ('{symbols_str}')
                AND datetime >= '{start_date}'
                AND datetime <= '{end_date}'
                AND frequency = '{frequency.value}'
                ORDER BY symbol, datetime
            """
            
            df = conn.execute(sql).fetchdf()
            logger.debug(f"从DuckDB加载K线数据: {len(df)}条记录")
            return df
            
        except Exception as e:
            logger.error(f"从DuckDB加载K线数据失败: {e}", exc_info=True)
            return pd.DataFrame()
    
    def _bars_to_dataframe(self, bars: List[Bar]) -> pd.DataFrame:
        """将Bar对象列表转换为DataFrame"""
        data = []
        for bar in bars:
            data.append({
                'symbol': bar.symbol,
                'datetime': bar.datetime,
                'frequency': bar.frequency.value,
                'open': bar.open,
                'high': bar.high,
                'low': bar.low,
                'close': bar.close,
                'volume': bar.volume,
                'amount': bar.amount,
                'turnover': bar.turnover,
                'ma5': bar.ma5,
                'ma20': bar.ma20,
                'ma60': bar.ma60,
                'macd_dif': bar.macd_dif,
                'macd_dea': bar.macd_dea,
                'macd_histogram': bar.macd_histogram,
                'rsi_14': bar.rsi_14,
                'boll_upper': bar.boll_upper,
                'boll_lower': bar.boll_lower,
                'market_cap': bar.market_cap,
                'circulating_market_cap': bar.circulating_market_cap,
                'is_st': bar.is_st,
                'is_new_stock': bar.is_new_stock
            })
        return pd.DataFrame(data)
    
    def close(self):
        """关闭数据库连接"""
        if self._connection:
            self._connection.close()
            self._connection = None


class SQLiteBusinessStorage:
    """SQLite业务数据存储（股票池、交易日历等）"""
    
    def __init__(self, db_path: str):
        """
        初始化SQLite存储
        
        Args:
            db_path: 数据库文件路径
        """
        self.db_path = Path(db_path)
        self.db_path.parent.mkdir(parents=True, exist_ok=True)
        self._create_tables()
        logger.info(f"SQLite业务数据库初始化完成: {self.db_path}")
    
    def _get_connection(self):
        """获取数据库连接"""
        return sqlite3.connect(str(self.db_path))
    
    def _create_tables(self):
        """创建业务表结构"""
        with self._get_connection() as conn:
            # 股票池表
            conn.execute("""
                CREATE TABLE IF NOT EXISTS universe (
                    name TEXT,
                    symbol TEXT,
                    update_time TIMESTAMP,
                    PRIMARY KEY (name, symbol)
                )
            """)
            
            # 交易日历表
            conn.execute("""
                CREATE TABLE IF NOT EXISTS calendar (
                    date TEXT PRIMARY KEY,
                    is_trading_day BOOLEAN,
                    market TEXT DEFAULT 'A_SHARE'
                )
            """)
            
            # 股票基本信息表
            conn.execute("""
                CREATE TABLE IF NOT EXISTS stock_info (
                    symbol TEXT PRIMARY KEY,
                    name TEXT,
                    sector TEXT,
                    industry TEXT,
                    list_date TEXT,
                    update_time TIMESTAMP
                )
            """)
            
            # 策略配置表
            conn.execute("""
                CREATE TABLE IF NOT EXISTS strategy_configs (
                    strategy_id TEXT PRIMARY KEY,
                    name TEXT,
                    config_json TEXT,
                    update_time TIMESTAMP
                )
            """)
    
    def save_universe(self, universe_name: str, symbols: List[str]) -> bool:
        """保存股票池"""
        try:
            with self._get_connection() as conn:
                # 清除原有数据
                conn.execute("DELETE FROM universe WHERE name = ?", (universe_name,))
                
                # 插入新数据
                now = datetime.now()
                data = [(universe_name, symbol, now) for symbol in symbols]
                conn.executemany(
                    "INSERT INTO universe (name, symbol, update_time) VALUES (?, ?, ?)",
                    data
                )
            
            logger.debug(f"保存股票池 {universe_name}: {len(symbols)}支股票")
            return True
            
        except Exception as e:
            logger.error(f"保存股票池失败: {e}", exc_info=True)
            return False
    
    def load_universe(self, universe_name: str) -> List[str]:
        """加载股票池"""
        try:
            with self._get_connection() as conn:
                cursor = conn.execute(
                    "SELECT symbol FROM universe WHERE name = ? ORDER BY symbol",
                    (universe_name,)
                )
                symbols = [row[0] for row in cursor.fetchall()]
            
            logger.debug(f"加载股票池 {universe_name}: {len(symbols)}支股票")
            return symbols
            
        except Exception as e:
            logger.error(f"加载股票池失败: {e}", exc_info=True)
            return []