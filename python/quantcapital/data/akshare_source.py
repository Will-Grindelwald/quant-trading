"""
AKShare数据源实现

集成akshare库，获取A股、ETF、指数的真实市场数据。
"""

import time
import logging
from datetime import datetime, timedelta
from typing import List, Dict, Optional
import pandas as pd
from ..entities.bar import Bar, Frequency


logger = logging.getLogger(__name__)


class AKShareDataSource:
    """AKShare数据源"""
    
    def __init__(self, config: Dict = None):
        """
        初始化AKShare数据源
        
        Args:
            config: 配置字典
        """
        self.config = config or {}
        self.request_delay = self.config.get('request_delay', 0.1)  # 请求间隔，避免频率限制
        
        try:
            import akshare as ak
            self.ak = ak
            logger.info("AKShare数据源初始化成功")
        except ImportError:
            logger.error("请安装akshare: pip install akshare")
            raise
    
    def get_stock_list(self) -> List[str]:
        """获取所有股票列表（沪深京A股）"""
        try:
            symbols = []
            
            # 获取沪深A股列表
            logger.info("获取沪深A股列表...")
            stock_zh_a_spot = self.ak.stock_zh_a_spot_em()
            
            if stock_zh_a_spot is not None and not stock_zh_a_spot.empty:
                # 提取股票代码，格式化为标准形式
                for _, row in stock_zh_a_spot.iterrows():
                    code = row['代码']
                    # 根据代码判断交易所
                    if code.startswith('60') or code.startswith('68'):
                        symbols.append(f"{code}.SH")  # 上交所
                    elif code.startswith('00') or code.startswith('30'):
                        symbols.append(f"{code}.SZ")  # 深交所
                    elif code.startswith('8') or code.startswith('4'):
                        symbols.append(f"{code}.BJ")  # 北交所
                        
            logger.info(f"获取到 {len(symbols)} 支A股")
            time.sleep(self.request_delay)
            
            return symbols[:1000]  # 限制数量避免首次下载过多
            
        except Exception as e:
            logger.error(f"获取股票列表失败: {e}")
            return []
    
    def get_etf_list(self) -> List[str]:
        """获取ETF列表"""
        try:
            symbols = []
            
            logger.info("获取ETF列表...")
            # 获取ETF列表
            fund_etf_spot = self.ak.fund_etf_spot_em()
            
            if fund_etf_spot is not None and not fund_etf_spot.empty:
                for _, row in fund_etf_spot.iterrows():
                    code = row['代码']
                    # ETF代码格式化
                    if code.startswith('51') or code.startswith('50'):
                        symbols.append(f"{code}.SH")
                    elif code.startswith('15') or code.startswith('16'):
                        symbols.append(f"{code}.SZ")
                        
            logger.info(f"获取到 {len(symbols)} 支ETF")
            time.sleep(self.request_delay)
            
            return symbols[:200]  # 限制ETF数量
            
        except Exception as e:
            logger.error(f"获取ETF列表失败: {e}")
            return []
    
    def get_index_list(self) -> List[str]:
        """获取主要指数列表"""
        try:
            # 主要指数代码
            major_indices = [
                "000001.SH",  # 上证指数
                "399001.SZ",  # 深证成指
                "399006.SZ",  # 创业板指
                "000300.SH",  # 沪深300
                "000905.SH",  # 中证500
                "000852.SH",  # 中证1000
                "399905.SZ",  # 中证500（深交所）
                "000016.SH",  # 上证50
                "000010.SH",  # 上证180
            ]
            
            logger.info(f"使用预定义主要指数: {len(major_indices)} 个")
            return major_indices
            
        except Exception as e:
            logger.error(f"获取指数列表失败: {e}")
            return []
    
    def get_all_symbols(self) -> List[str]:
        """获取所有标的代码"""
        all_symbols = []
        
        # 获取股票
        stocks = self.get_stock_list()
        all_symbols.extend(stocks)
        
        # 获取ETF
        etfs = self.get_etf_list()
        all_symbols.extend(etfs)
        
        # 获取指数
        indices = self.get_index_list()
        all_symbols.extend(indices)
        
        logger.info(f"总计获取 {len(all_symbols)} 个标的")
        return all_symbols
    
    def get_kline_data(self, symbols: List[str], frequency: str, 
                      start_date: str, end_date: str) -> pd.DataFrame:
        """
        获取K线数据
        
        Args:
            symbols: 股票代码列表
            frequency: 频率 ('D', 'W', '60')
            start_date: 开始日期 'YYYY-MM-DD'
            end_date: 结束日期 'YYYY-MM-DD'
            
        Returns:
            包含所有股票K线数据的DataFrame
        """
        all_data = []
        
        # 频率映射
        freq_map = {
            'D': 'daily',    # 日线
            'W': 'weekly',   # 周线  
            '60': '60'       # 60分钟线
        }
        
        ak_freq = freq_map.get(frequency, 'daily')
        
        logger.info(f"开始获取K线数据: {len(symbols)}个标的, 频率={frequency}, "
                   f"时间范围={start_date}~{end_date}")
        
        success_count = 0
        for i, symbol in enumerate(symbols):
            try:
                # 解析股票代码
                code, exchange = symbol.split('.')
                
                # 根据不同类型调用不同接口
                if self._is_index(symbol):
                    df = self._get_index_kline(code, exchange, ak_freq, start_date, end_date)
                elif self._is_etf(symbol):
                    df = self._get_etf_kline(code, exchange, ak_freq, start_date, end_date)
                else:
                    df = self._get_stock_kline(code, exchange, ak_freq, start_date, end_date)
                
                if df is not None and not df.empty:
                    df['symbol'] = symbol
                    all_data.append(df)
                    success_count += 1
                    
                    if (i + 1) % 10 == 0:
                        logger.info(f"已获取 {i + 1}/{len(symbols)} 个标的数据")
                
                # 请求间隔，避免频率限制
                time.sleep(self.request_delay)
                
            except Exception as e:
                logger.warning(f"获取 {symbol} 数据失败: {e}")
                continue
        
        if all_data:
            result_df = pd.concat(all_data, ignore_index=True)
            logger.info(f"K线数据获取完成: 成功{success_count}/{len(symbols)}个标的, "
                       f"共{len(result_df)}条记录")
            return result_df
        else:
            logger.warning("未获取到任何K线数据")
            return pd.DataFrame()
    
    def _is_index(self, symbol: str) -> bool:
        """判断是否为指数"""
        code = symbol.split('.')[0]
        return (code.startswith('000') or code.startswith('399')) and len(code) == 6
    
    def _is_etf(self, symbol: str) -> bool:
        """判断是否为ETF"""
        code = symbol.split('.')[0]
        return code.startswith(('50', '51', '15', '16'))
    
    def _get_stock_kline(self, code: str, exchange: str, frequency: str, 
                        start_date: str, end_date: str) -> Optional[pd.DataFrame]:
        """获取股票K线数据"""
        try:
            if frequency == 'daily':
                # 日线数据
                df = self.ak.stock_zh_a_hist(symbol=code, period="daily", 
                                           start_date=start_date.replace('-', ''), 
                                           end_date=end_date.replace('-', ''))
            elif frequency == 'weekly':
                # 周线数据
                df = self.ak.stock_zh_a_hist(symbol=code, period="weekly",
                                           start_date=start_date.replace('-', ''), 
                                           end_date=end_date.replace('-', ''))
            elif frequency == '60':
                # 60分钟数据
                df = self.ak.stock_zh_a_hist_min_em(symbol=code, period="60",
                                                  start_date=start_date + " 09:30:00",
                                                  end_date=end_date + " 15:00:00")
            else:
                return None
                
            return self._standardize_kline_data(df, frequency)
            
        except Exception as e:
            logger.debug(f"获取股票{code}数据失败: {e}")
            return None
    
    def _get_etf_kline(self, code: str, exchange: str, frequency: str,
                      start_date: str, end_date: str) -> Optional[pd.DataFrame]:
        """获取ETF K线数据"""
        try:
            if frequency == 'daily':
                df = self.ak.fund_etf_hist_em(symbol=code, period="daily",
                                            start_date=start_date.replace('-', ''),
                                            end_date=end_date.replace('-', ''))
            else:
                # ETF暂时只支持日线
                return None
                
            return self._standardize_kline_data(df, frequency)
            
        except Exception as e:
            logger.debug(f"获取ETF{code}数据失败: {e}")
            return None
    
    def _get_index_kline(self, code: str, exchange: str, frequency: str,
                        start_date: str, end_date: str) -> Optional[pd.DataFrame]:
        """获取指数K线数据"""
        try:
            if frequency == 'daily':
                df = self.ak.stock_zh_index_daily_em(symbol=code,
                                                   start_date=start_date.replace('-', ''),
                                                   end_date=end_date.replace('-', ''))
            else:
                # 指数暂时只支持日线
                return None
                
            return self._standardize_kline_data(df, frequency)
            
        except Exception as e:
            logger.debug(f"获取指数{code}数据失败: {e}")
            return None
    
    def _standardize_kline_data(self, df: pd.DataFrame, frequency: str) -> pd.DataFrame:
        """标准化K线数据格式"""
        if df is None or df.empty:
            return pd.DataFrame()
        
        try:
            # 统一列名映射
            column_mapping = {
                '日期': 'datetime',
                '时间': 'datetime', 
                '开盘': 'open',
                '最高': 'high',
                '最低': 'low', 
                '收盘': 'close',
                '成交量': 'volume',
                '成交额': 'amount',
                '换手率': 'turnover'
            }
            
            # 重命名列
            df = df.rename(columns=column_mapping)
            
            # 确保必需列存在
            required_cols = ['datetime', 'open', 'high', 'low', 'close', 'volume']
            for col in required_cols:
                if col not in df.columns:
                    if col == 'volume':
                        df[col] = 0
                    else:
                        logger.warning(f"缺少必需列: {col}")
                        return pd.DataFrame()
            
            # 处理时间格式
            df['datetime'] = pd.to_datetime(df['datetime'])
            
            # 数据类型转换
            numeric_cols = ['open', 'high', 'low', 'close', 'volume']
            for col in numeric_cols:
                df[col] = pd.to_numeric(df[col], errors='coerce')
            
            # 添加可选列的默认值
            if 'amount' not in df.columns:
                df['amount'] = df['close'] * df['volume']
            if 'turnover' not in df.columns:
                df['turnover'] = 0.0
                
            # 移除无效数据
            df = df.dropna(subset=['open', 'high', 'low', 'close'])
            
            # 按时间排序
            df = df.sort_values('datetime').reset_index(drop=True)
            
            return df
            
        except Exception as e:
            logger.warning(f"标准化K线数据失败: {e}")
            return pd.DataFrame()