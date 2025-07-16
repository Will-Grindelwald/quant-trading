"""
策略模块

提供策略基类和策略管理功能，支持多种策略类型的协调运行。
"""

from .base_strategy import BaseStrategy, StrategyManager
from .ma_cross_strategy import MACrossStrategy

__all__ = ['BaseStrategy', 'StrategyManager', 'MACrossStrategy']