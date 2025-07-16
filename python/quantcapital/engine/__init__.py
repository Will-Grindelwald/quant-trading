"""
事件驱动引擎模块

高性能的事件分发和处理框架，支持异步事件处理和故障隔离。
"""

from .event_engine import EventEngine, EventHandler
from .timer import Timer

__all__ = ['EventEngine', 'EventHandler', 'Timer']