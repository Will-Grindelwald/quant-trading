"""
组合风控模块

负责信号处理、仓位管理、风险控制和订单生成。
"""

from .portfolio_manager import PortfolioRiskManager

__all__ = ['PortfolioRiskManager']