"""
执行模块

负责订单的实际执行，支持回测模拟执行和实盘真实执行。
"""

from .execution_handler import ExecutionHandler, SimulatedExecutionHandler, LiveExecutionHandler

__all__ = ['ExecutionHandler', 'SimulatedExecutionHandler', 'LiveExecutionHandler']