"""
数据处理模块

负责数据获取、清洗、存储和管理。
支持 AKShare 数据源，输出 Parquet/DuckDB/SQLite 格式。
"""

from .akshare_provider import AKShareProvider
from .data_manager import DataManager

__all__ = [
    'AKShareProvider', 'DataManager'
]