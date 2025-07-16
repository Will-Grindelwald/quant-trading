"""
量化交易框架 QuantCapital - Python 数据服务

专注于数据获取、处理、分析和报告生成。
核心交易逻辑已迁移至 Java 实现。
"""

__version__ = "0.2.0"
__author__ = "QuantCapital Team"

# 数据处理模块
from .data import DataHandler, DataUpdater

# 基础实体（数据相关）
from .entities import Bar, Frequency

__all__ = [
    'DataHandler', 'DataUpdater',
    'Bar', 'Frequency'
]