"""
AKShare 数据提供者

使用 AKShare 获取 A 股市场数据。
"""

import logging
import time
from datetime import datetime, timedelta
from typing import Dict, List, Optional, Tuple

import akshare as ak
import pandas as pd

from ..entities import Bar, Frequency

logger = logging.getLogger(__name__)


class AKShareProvider:
    """AKShare 数据提供者"""
    
    def __init__(self, config: Dict):
        """
        初始化 AKShare 数据提供者
        
        Args:
            config: 配置字典
        """
        self.config = config
        self.request_delay = config.get('data_source', {}).get('request_delay', 0.5)
        
    def get_stock_list(self, include_index: bool = False) -> pd.DataFrame:
        """
        获取股票列表
        
        Args:
            include_index: 是否包含指数
            
        Returns:
            股票列表 DataFrame
        """
        try:
            # 获取 A 股股票列表
            df = ak.stock_zh_a_spot_em()
            
            # 重命名列
            df = df.rename(columns={
                '代码': 'symbol',
                '名称': 'name',
                '最新价': 'price',
                '涨跌幅': 'change_pct',
                '涨跌额': 'change',
                '成交量': 'volume',
                '成交额': 'amount',
                '振幅': 'amplitude',
                '最高': 'high',
                '最低': 'low',
                '今开': 'open',
                '昨收': 'prev_close',
                '换手率': 'turnover',
                '市盈率-动态': 'pe_ttm',
                '市净率': 'pb',
                '总市值': 'market_cap',
                '流通市值': 'circulating_cap'
            })
            
            # 添加交易所后缀
            df['symbol'] = df['symbol'].apply(self._add_exchange_suffix)
            
            logger.info(f"获取到 {len(df)} 只股票")
            return df
            
        except Exception as e:
            logger.error(f"获取股票列表失败: {e}")
            raise
            
    def get_stock_history(self, 
                         symbol: str,
                         start_date: str,
                         end_date: str,
                         frequency: Frequency = Frequency.DAILY,
                         adjust: str = "qfq") -> pd.DataFrame:
        """
        获取股票历史数据
        
        Args:
            symbol: 股票代码（含交易所后缀）
            start_date: 开始日期 (YYYYMMDD)
            end_date: 结束日期 (YYYYMMDD)
            frequency: 数据频率
            adjust: 复权类型 ("qfq": 前复权, "hfq": 后复权, "": 不复权)
            
        Returns:
            历史数据 DataFrame
        """
        try:
            # 移除交易所后缀
            pure_symbol = symbol.split('.')[0]
            
            # 转换频率
            period = self._convert_frequency(frequency)
            
            # 获取数据
            df = ak.stock_zh_a_hist(
                symbol=pure_symbol,
                period=period,
                start_date=start_date,
                end_date=end_date,
                adjust=adjust
            )
            
            if df.empty:
                logger.warning(f"股票 {symbol} 无历史数据")
                return df
                
            # 重命名列
            df = df.rename(columns={
                '日期': 'date',
                '开盘': 'open',
                '收盘': 'close',
                '最高': 'high',
                '最低': 'low',
                '成交量': 'volume',
                '成交额': 'amount',
                '振幅': 'amplitude',
                '涨跌幅': 'change_pct',
                '涨跌额': 'change',
                '换手率': 'turnover'
            })
            
            # 设置日期为索引
            df['date'] = pd.to_datetime(df['date'])
            df.set_index('date', inplace=True)
            
            # 添加股票代码列
            df['symbol'] = symbol
            
            # 延迟以避免请求过快
            time.sleep(self.request_delay)
            
            logger.info(f"获取 {symbol} 历史数据成功，共 {len(df)} 条记录")
            return df
            
        except Exception as e:
            logger.error(f"获取 {symbol} 历史数据失败: {e}")
            raise
            
    def get_index_history(self,
                         index_code: str,
                         start_date: str,
                         end_date: str) -> pd.DataFrame:
        """
        获取指数历史数据
        
        Args:
            index_code: 指数代码
            start_date: 开始日期
            end_date: 结束日期
            
        Returns:
            指数历史数据
        """
        try:
            df = ak.stock_zh_index_daily(symbol=index_code)
            
            # 筛选日期范围
            df['date'] = pd.to_datetime(df['date'])
            mask = (df['date'] >= start_date) & (df['date'] <= end_date)
            df = df[mask]
            
            # 设置日期为索引
            df.set_index('date', inplace=True)
            
            return df
            
        except Exception as e:
            logger.error(f"获取指数 {index_code} 历史数据失败: {e}")
            raise
            
    def get_realtime_quotes(self, symbols: List[str]) -> pd.DataFrame:
        """
        获取实时行情
        
        Args:
            symbols: 股票代码列表
            
        Returns:
            实时行情 DataFrame
        """
        try:
            # 获取所有实时行情
            df = ak.stock_zh_a_spot_em()
            
            # 处理股票代码
            pure_symbols = [s.split('.')[0] for s in symbols]
            df = df[df['代码'].isin(pure_symbols)]
            
            # 重命名列
            df = df.rename(columns={
                '代码': 'symbol',
                '名称': 'name',
                '最新价': 'price',
                '涨跌幅': 'change_pct',
                '涨跌额': 'change',
                '成交量': 'volume',
                '成交额': 'amount',
                '振幅': 'amplitude',
                '最高': 'high',
                '最低': 'low',
                '今开': 'open',
                '昨收': 'prev_close',
                '换手率': 'turnover'
            })
            
            # 添加交易所后缀
            df['symbol'] = df['symbol'].apply(self._add_exchange_suffix)
            
            return df
            
        except Exception as e:
            logger.error(f"获取实时行情失败: {e}")
            raise
            
    def get_financial_data(self, symbol: str) -> Dict:
        """
        获取财务数据
        
        Args:
            symbol: 股票代码
            
        Returns:
            财务数据字典
        """
        try:
            pure_symbol = symbol.split('.')[0]
            
            # 获取基本面数据
            df_balance = ak.stock_financial_report_sina(
                stock=pure_symbol,
                symbol="资产负债表"
            )
            
            df_income = ak.stock_financial_report_sina(
                stock=pure_symbol,
                symbol="利润表"
            )
            
            df_cashflow = ak.stock_financial_report_sina(
                stock=pure_symbol,
                symbol="现金流量表"
            )
            
            return {
                'balance_sheet': df_balance,
                'income_statement': df_income,
                'cash_flow': df_cashflow
            }
            
        except Exception as e:
            logger.error(f"获取 {symbol} 财务数据失败: {e}")
            return {}
            
    def _add_exchange_suffix(self, symbol: str) -> str:
        """添加交易所后缀"""
        if symbol.startswith('6'):
            return f"{symbol}.SH"
        elif symbol.startswith('0') or symbol.startswith('3'):
            return f"{symbol}.SZ"
        else:
            return symbol
            
    def _convert_frequency(self, frequency: Frequency) -> str:
        """转换频率到 AKShare 格式"""
        mapping = {
            Frequency.MINUTE_1: "1",
            Frequency.MINUTE_5: "5",
            Frequency.MINUTE_15: "15",
            Frequency.MINUTE_30: "30",
            Frequency.MINUTE_60: "60",
            Frequency.DAILY: "daily",
            Frequency.WEEKLY: "weekly",
            Frequency.MONTHLY: "monthly"
        }
        return mapping.get(frequency, "daily")