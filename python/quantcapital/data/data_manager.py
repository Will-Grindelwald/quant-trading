"""
数据存储管理器

负责将数据保存到不同的存储格式：
- Parquet: K线数据
- DuckDB: 技术指标和时序数据
- SQLite: 业务数据和元信息
"""

import logging
import os
import sqlite3
from datetime import datetime
from pathlib import Path
from typing import Dict, List, Optional, Union

import duckdb
import pandas as pd
import pyarrow as pa
import pyarrow.parquet as pq

logger = logging.getLogger(__name__)


class DataManager:
    """数据存储管理器"""
    
    def __init__(self, data_root: str):
        """
        初始化数据管理器
        
        Args:
            data_root: 数据根目录
        """
        self.data_root = Path(data_root)
        self.parquet_dir = self.data_root / "kline"
        self.duckdb_path = self.data_root / "indicators.duckdb"
        self.sqlite_path = self.data_root / "business.db"
        
        # 创建目录
        self._init_directories()
        
        # 初始化数据库
        self._init_databases()
        
    def _init_directories(self):
        """创建必要的目录"""
        self.data_root.mkdir(parents=True, exist_ok=True)
        self.parquet_dir.mkdir(parents=True, exist_ok=True)
        
    def _init_databases(self):
        """初始化数据库"""
        # 初始化 SQLite
        self._init_sqlite()
        
        # 初始化 DuckDB
        self._init_duckdb()
        
    def _init_sqlite(self):
        """初始化 SQLite 数据库"""
        with sqlite3.connect(str(self.sqlite_path)) as conn:
            cursor = conn.cursor()
            
            # 创建股票信息表
            cursor.execute("""
                CREATE TABLE IF NOT EXISTS stock_info (
                    symbol TEXT PRIMARY KEY,
                    name TEXT,
                    exchange TEXT,
                    industry TEXT,
                    market_cap REAL,
                    circulating_cap REAL,
                    list_date TEXT,
                    update_time TEXT
                )
            """)
            
            # 创建交易日历表
            cursor.execute("""
                CREATE TABLE IF NOT EXISTS trading_calendar (
                    date TEXT PRIMARY KEY,
                    is_trading INTEGER,
                    prev_trading_date TEXT,
                    next_trading_date TEXT
                )
            """)
            
            # 创建策略回测记录表
            cursor.execute("""
                CREATE TABLE IF NOT EXISTS backtest_records (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    strategy_name TEXT,
                    start_date TEXT,
                    end_date TEXT,
                    initial_capital REAL,
                    final_capital REAL,
                    total_return REAL,
                    sharpe_ratio REAL,
                    max_drawdown REAL,
                    win_rate REAL,
                    create_time TEXT
                )
            """)
            
            conn.commit()
            
    def _init_duckdb(self):
        """初始化 DuckDB 数据库"""
        with duckdb.connect(str(self.duckdb_path)) as conn:
            # 创建技术指标表
            conn.execute("""
                CREATE TABLE IF NOT EXISTS indicators (
                    symbol VARCHAR,
                    date DATE,
                    ma5 DOUBLE,
                    ma10 DOUBLE,
                    ma20 DOUBLE,
                    ma60 DOUBLE,
                    macd_dif DOUBLE,
                    macd_dea DOUBLE,
                    macd_histogram DOUBLE,
                    rsi_14 DOUBLE,
                    boll_upper DOUBLE,
                    boll_middle DOUBLE,
                    boll_lower DOUBLE,
                    PRIMARY KEY (symbol, date)
                )
            """)
            
    def save_stock_data(self, symbol: str, data: pd.DataFrame):
        """
        保存股票数据
        
        Args:
            symbol: 股票代码
            data: 股票数据 DataFrame
        """
        try:
            # 保存 K 线数据到 Parquet
            self._save_kline_to_parquet(symbol, data)
            
            # 计算并保存技术指标到 DuckDB
            self._save_indicators_to_duckdb(symbol, data)
            
            # 更新股票信息到 SQLite
            self._update_stock_info(symbol, data)
            
            logger.info(f"保存 {symbol} 数据成功")
            
        except Exception as e:
            logger.error(f"保存 {symbol} 数据失败: {e}")
            raise
            
    def _save_kline_to_parquet(self, symbol: str, data: pd.DataFrame):
        """保存 K 线数据到 Parquet 文件"""
        file_path = self.parquet_dir / f"{symbol}.parquet"
        
        # 选择需要的列
        columns = ['open', 'high', 'low', 'close', 'volume', 'amount', 'turnover']
        kline_data = data[columns].copy()
        
        # 添加元数据
        metadata = {
            'symbol': symbol,
            'update_time': datetime.now().isoformat(),
            'rows': str(len(kline_data))
        }
        
        # 创建 PyArrow 表
        table = pa.Table.from_pandas(kline_data)
        
        # 写入 Parquet 文件
        pq.write_table(
            table,
            file_path,
            compression='snappy',
            metadata=metadata
        )
        
    def _save_indicators_to_duckdb(self, symbol: str, data: pd.DataFrame):
        """保存技术指标到 DuckDB"""
        with duckdb.connect(str(self.duckdb_path)) as conn:
            # 准备数据
            indicators_data = data.copy()
            indicators_data['symbol'] = symbol
            indicators_data['date'] = indicators_data.index
            
            # 创建临时表
            conn.register('temp_indicators', indicators_data)
            
            # 删除已存在的数据
            conn.execute(f"""
                DELETE FROM indicators 
                WHERE symbol = '{symbol}'
                AND date IN (SELECT date FROM temp_indicators)
            """)
            
            # 插入新数据
            conn.execute("""
                INSERT INTO indicators 
                SELECT 
                    symbol,
                    date,
                    NULL as ma5,
                    NULL as ma10,
                    NULL as ma20,
                    NULL as ma60,
                    NULL as macd_dif,
                    NULL as macd_dea,
                    NULL as macd_histogram,
                    NULL as rsi_14,
                    NULL as boll_upper,
                    NULL as boll_middle,
                    NULL as boll_lower
                FROM temp_indicators
            """)
            
            conn.unregister('temp_indicators')
            
    def _update_stock_info(self, symbol: str, data: pd.DataFrame):
        """更新股票信息到 SQLite"""
        with sqlite3.connect(str(self.sqlite_path)) as conn:
            cursor = conn.cursor()
            
            # 获取最新数据
            latest = data.iloc[-1] if not data.empty else None
            
            if latest is not None:
                # 更新或插入股票信息
                cursor.execute("""
                    INSERT OR REPLACE INTO stock_info 
                    (symbol, update_time)
                    VALUES (?, ?)
                """, (symbol, datetime.now().isoformat()))
                
            conn.commit()
            
    def load_stock_data(self, 
                       symbol: str,
                       start_date: Optional[str] = None,
                       end_date: Optional[str] = None) -> pd.DataFrame:
        """
        加载股票数据
        
        Args:
            symbol: 股票代码
            start_date: 开始日期
            end_date: 结束日期
            
        Returns:
            股票数据 DataFrame
        """
        try:
            # 从 Parquet 加载 K 线数据
            file_path = self.parquet_dir / f"{symbol}.parquet"
            
            if not file_path.exists():
                logger.warning(f"股票 {symbol} 数据文件不存在")
                return pd.DataFrame()
                
            # 读取 Parquet 文件
            df = pd.read_parquet(file_path)
            
            # 筛选日期范围
            if start_date:
                df = df[df.index >= start_date]
            if end_date:
                df = df[df.index <= end_date]
                
            return df
            
        except Exception as e:
            logger.error(f"加载 {symbol} 数据失败: {e}")
            raise
            
    def load_indicators(self,
                       symbol: str,
                       start_date: Optional[str] = None,
                       end_date: Optional[str] = None) -> pd.DataFrame:
        """
        加载技术指标数据
        
        Args:
            symbol: 股票代码
            start_date: 开始日期
            end_date: 结束日期
            
        Returns:
            技术指标 DataFrame
        """
        with duckdb.connect(str(self.duckdb_path)) as conn:
            query = f"""
                SELECT * FROM indicators
                WHERE symbol = '{symbol}'
            """
            
            if start_date:
                query += f" AND date >= '{start_date}'"
            if end_date:
                query += f" AND date <= '{end_date}'"
                
            query += " ORDER BY date"
            
            df = conn.execute(query).df()
            
            if not df.empty:
                df.set_index('date', inplace=True)
                
            return df
            
    def get_stock_list(self) -> List[str]:
        """获取所有股票代码列表"""
        stocks = []
        
        # 从 Parquet 文件列表获取
        for file_path in self.parquet_dir.glob("*.parquet"):
            symbol = file_path.stem
            stocks.append(symbol)
            
        return sorted(stocks)
        
    def get_trading_calendar(self,
                           start_date: str,
                           end_date: str) -> pd.DataFrame:
        """获取交易日历"""
        with sqlite3.connect(str(self.sqlite_path)) as conn:
            query = """
                SELECT * FROM trading_calendar
                WHERE date >= ? AND date <= ?
                AND is_trading = 1
                ORDER BY date
            """
            
            df = pd.read_sql_query(
                query,
                conn,
                params=(start_date, end_date)
            )
            
            if not df.empty:
                df['date'] = pd.to_datetime(df['date'])
                df.set_index('date', inplace=True)
                
            return df
            
    def save_backtest_record(self, record: Dict):
        """保存回测记录"""
        with sqlite3.connect(str(self.sqlite_path)) as conn:
            cursor = conn.cursor()
            
            cursor.execute("""
                INSERT INTO backtest_records
                (strategy_name, start_date, end_date, initial_capital,
                 final_capital, total_return, sharpe_ratio, max_drawdown,
                 win_rate, create_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, (
                record.get('strategy_name'),
                record.get('start_date'),
                record.get('end_date'),
                record.get('initial_capital'),
                record.get('final_capital'),
                record.get('total_return'),
                record.get('sharpe_ratio'),
                record.get('max_drawdown'),
                record.get('win_rate'),
                datetime.now().isoformat()
            ))
            
            conn.commit()