"""
量化交易框架 QuantCapital

模块化、事件驱动的量化交易框架，支持回测与实盘交易。
"""

__version__ = "0.1.0"
__author__ = "QuantCapital Team"

# 核心模块导入
from .entities import *
from .engine import EventEngine
from .data import DataHandler, DataUpdater
from .strategy import BaseStrategy
from .portfolio import PortfolioRiskManager, Account
from .execution import ExecutionHandler