"""
事件驱动引擎实现

高效分发架构：
- EventEngine维护主事件队列，负责快速分发
- 每个订阅者拥有独立的事件队列和处理线程
- 分发过程不等待订阅者处理，避免阻塞
"""

import queue
import threading
import logging
from abc import ABC, abstractmethod
from typing import Dict, List, Callable
from ..entities.event import Event


logger = logging.getLogger(__name__)


class EventHandler(ABC):
    """事件处理器基类"""
    
    def __init__(self, name: str, queue_size: int = 1000):
        self.name = name
        self.queue_size = queue_size
        self.queue = queue.Queue(maxsize=queue_size)
        self.thread = None
        self.running = False
        
    def start(self):
        """启动事件处理器"""
        if self.running:
            return
            
        self.running = True
        self.thread = threading.Thread(target=self._run, name=f"EventHandler-{self.name}")
        self.thread.daemon = True
        self.thread.start()
        logger.info(f"事件处理器 {self.name} 已启动")
    
    def stop(self):
        """停止事件处理器"""
        if not self.running:
            return
            
        self.running = False
        if self.thread and self.thread.is_alive():
            self.thread.join(timeout=5.0)
        logger.info(f"事件处理器 {self.name} 已停止")
    
    def put_event(self, event: Event) -> bool:
        """接收事件到自己的队列"""
        try:
            self.queue.put_nowait(event)
            return True
        except queue.Full:
            logger.warning(f"事件处理器 {self.name} 队列已满，丢弃事件: {event.type}")
            return False
    
    def _run(self):
        """处理自己队列中的事件"""
        while self.running:
            try:
                event = self.queue.get(timeout=1.0)
                if event is None:  # 停止信号
                    break
                self.handle_event(event)
                self.queue.task_done()
            except queue.Empty:
                continue
            except Exception as e:
                logger.error(f"事件处理器 {self.name} 处理事件时发生错误: {e}", exc_info=True)
                self.handle_error(e)
    
    @abstractmethod
    def handle_event(self, event: Event):
        """子类实现具体的事件处理逻辑"""
        pass
    
    def handle_error(self, error: Exception):
        """错误处理，子类可重写"""
        logger.error(f"事件处理器 {self.name} 错误: {error}")


class FunctionHandler(EventHandler):
    """基于函数的事件处理器"""
    
    def __init__(self, name: str, handler_func: Callable[[Event], None], queue_size: int = 1000):
        super().__init__(name, queue_size)
        self.handler_func = handler_func
    
    def handle_event(self, event: Event):
        """调用处理函数"""
        self.handler_func(event)


class EventEngine:
    """事件驱动引擎"""
    
    def __init__(self, queue_size: int = 10000):
        self._handlers: Dict[str, List[EventHandler]] = {}
        self._queue: queue.Queue = queue.Queue(maxsize=queue_size)
        self._running: bool = False
        self._thread: threading.Thread = None
        self._stats = {
            'events_dispatched': 0,
            'events_dropped': 0,
            'dispatch_errors': 0
        }
        
    def register(self, event_type: str, handler: EventHandler):
        """注册事件处理器"""
        if event_type not in self._handlers:
            self._handlers[event_type] = []
        self._handlers[event_type].append(handler)
        logger.info(f"注册事件处理器: {handler.name} -> {event_type}")
    
    def register_function(self, event_type: str, handler_func: Callable[[Event], None], 
                         name: str = None, queue_size: int = 1000):
        """注册基于函数的事件处理器"""
        if name is None:
            name = f"Function-{handler_func.__name__}"
        
        handler = FunctionHandler(name, handler_func, queue_size)
        self.register(event_type, handler)
        return handler
    
    def unregister(self, event_type: str, handler: EventHandler):
        """取消注册事件处理器"""
        if event_type in self._handlers:
            try:
                self._handlers[event_type].remove(handler)
                if not self._handlers[event_type]:
                    del self._handlers[event_type]
                logger.info(f"取消注册事件处理器: {handler.name} -> {event_type}")
            except ValueError:
                logger.warning(f"事件处理器 {handler.name} 未在 {event_type} 中注册")
    
    def put(self, event: Event) -> bool:
        """发送事件"""
        try:
            self._queue.put_nowait(event)
            return True
        except queue.Full:
            self._stats['events_dropped'] += 1
            logger.warning(f"事件队列已满，丢弃事件: {event.type}")
            return False
    
    def start(self):
        """启动事件引擎"""
        if self._running:
            return
            
        self._running = True
        self._thread = threading.Thread(target=self._run, name="EventEngine")
        self._thread.daemon = True
        self._thread.start()
        
        # 启动所有处理器
        for handlers in self._handlers.values():
            for handler in handlers:
                handler.start()
        
        logger.info("事件引擎已启动")
    
    def stop(self):
        """停止事件引擎"""
        if not self._running:
            return
            
        self._running = False
        
        # 停止所有处理器
        for handlers in self._handlers.values():
            for handler in handlers:
                handler.stop()
        
        # 停止主线程
        if self._thread and self._thread.is_alive():
            self._queue.put(None)  # 发送停止信号
            self._thread.join(timeout=5.0)
        
        logger.info("事件引擎已停止")
    
    def _run(self):
        """事件分发循环"""
        while self._running:
            try:
                event = self._queue.get(timeout=1.0)
                if event is None:  # 停止信号
                    break
                self._dispatch_event(event)
                self._queue.task_done()
            except queue.Empty:
                continue
            except Exception as e:
                logger.error(f"事件分发时发生错误: {e}", exc_info=True)
    
    def _dispatch_event(self, event: Event):
        """快速分发事件到各个订阅者队列"""
        event_type = event.type
        if event_type not in self._handlers:
            return
            
        dispatched = 0
        for handler in self._handlers[event_type]:
            try:
                if handler.put_event(event):
                    dispatched += 1
                else:
                    self._stats['events_dropped'] += 1
            except Exception as e:
                self._stats['dispatch_errors'] += 1
                logger.error(f"分发事件到 {handler.name} 时发生错误: {e}")
        
        self._stats['events_dispatched'] += 1
        
        if dispatched == 0:
            logger.debug(f"事件 {event_type} 没有有效的处理器")
    
    def get_stats(self) -> Dict:
        """获取统计信息"""
        return self._stats.copy()
    
    def clear_stats(self):
        """清除统计信息"""
        self._stats = {
            'events_dispatched': 0,
            'events_dropped': 0,
            'dispatch_errors': 0
        }
    
    def __enter__(self):
        """上下文管理器支持"""
        self.start()
        return self
    
    def __exit__(self, exc_type, exc_val, exc_tb):
        """上下文管理器支持"""
        self.stop()