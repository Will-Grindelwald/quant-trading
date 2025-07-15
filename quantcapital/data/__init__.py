"""
数据模块

提供统一的数据访问接口，支持K线数据的获取、更新和存储。
包含数据更新进程和回测/实盘数据处理器。
"""

from .data_handler import DataHandler, BacktestDataHandler, LiveDataHandler
from .data_updater import DataUpdater
from .storage import ParquetStorage, DuckDBStorage

__all__ = [
    'DataHandler', 'BacktestDataHandler', 'LiveDataHandler',
    'DataUpdater', 'ParquetStorage', 'DuckDBStorage'
]