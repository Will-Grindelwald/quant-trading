"""
定时器组件

用于定时触发事件，支持数据更新、策略计算等定时任务。
"""

import time
import threading
import logging
from datetime import datetime, timedelta
from typing import Callable, Optional
from ..entities.event import TimerEvent


logger = logging.getLogger(__name__)


class Timer:
    """定时器类"""
    
    def __init__(self, timer_id: str, interval: float, callback: Callable[[], None], 
                 repeat: bool = True, start_delay: float = 0):
        """
        初始化定时器
        
        Args:
            timer_id: 定时器ID
            interval: 触发间隔(秒)
            callback: 回调函数
            repeat: 是否重复执行
            start_delay: 启动延迟(秒)
        """
        self.timer_id = timer_id
        self.interval = interval
        self.callback = callback
        self.repeat = repeat
        self.start_delay = start_delay
        
        self._thread: Optional[threading.Thread] = None
        self._stop_event = threading.Event()
        self._running = False
        
    def start(self):
        """启动定时器"""
        if self._running:
            logger.warning(f"定时器 {self.timer_id} 已在运行")
            return
            
        self._running = True
        self._stop_event.clear()
        self._thread = threading.Thread(target=self._run, name=f"Timer-{self.timer_id}")
        self._thread.daemon = True
        self._thread.start()
        logger.info(f"定时器 {self.timer_id} 已启动，间隔: {self.interval}秒")
    
    def stop(self):
        """停止定时器"""
        if not self._running:
            return
            
        self._running = False
        self._stop_event.set()
        
        if self._thread and self._thread.is_alive():
            self._thread.join(timeout=5.0)
        
        logger.info(f"定时器 {self.timer_id} 已停止")
    
    def _run(self):
        """定时器运行循环"""
        # 启动延迟
        if self.start_delay > 0:
            if self._stop_event.wait(self.start_delay):
                return
        
        while self._running:
            try:
                # 执行回调
                start_time = time.time()
                self.callback()
                execution_time = time.time() - start_time
                
                if execution_time > self.interval:
                    logger.warning(f"定时器 {self.timer_id} 执行时间({execution_time:.2f}s)超过间隔({self.interval}s)")
                
                # 如果不重复，执行一次后退出
                if not self.repeat:
                    break
                
                # 等待下次执行，考虑执行时间
                sleep_time = max(0, self.interval - execution_time)
                if self._stop_event.wait(sleep_time):
                    break
                    
            except Exception as e:
                logger.error(f"定时器 {self.timer_id} 执行回调时发生错误: {e}", exc_info=True)
                
                # 错误后仍然等待间隔时间
                if self._stop_event.wait(self.interval):
                    break
    
    @property
    def is_running(self) -> bool:
        """是否正在运行"""
        return self._running


class TimerManager:
    """定时器管理器"""
    
    def __init__(self):
        self._timers: dict[str, Timer] = {}
        self._running = False
    
    def add_timer(self, timer: Timer) -> bool:
        """添加定时器"""
        if timer.timer_id in self._timers:
            logger.warning(f"定时器 {timer.timer_id} 已存在")
            return False
            
        self._timers[timer.timer_id] = timer
        
        # 如果管理器正在运行，立即启动定时器
        if self._running:
            timer.start()
            
        logger.info(f"添加定时器: {timer.timer_id}")
        return True
    
    def remove_timer(self, timer_id: str) -> bool:
        """移除定时器"""
        if timer_id not in self._timers:
            logger.warning(f"定时器 {timer_id} 不存在")
            return False
            
        timer = self._timers[timer_id]
        timer.stop()
        del self._timers[timer_id]
        
        logger.info(f"移除定时器: {timer_id}")
        return True
    
    def create_timer(self, timer_id: str, interval: float, callback: Callable[[], None],
                    repeat: bool = True, start_delay: float = 0) -> Timer:
        """创建并添加定时器"""
        timer = Timer(timer_id, interval, callback, repeat, start_delay)
        self.add_timer(timer)
        return timer
    
    def start_all(self):
        """启动所有定时器"""
        self._running = True
        for timer in self._timers.values():
            timer.start()
        logger.info(f"启动了 {len(self._timers)} 个定时器")
    
    def stop_all(self):
        """停止所有定时器"""
        self._running = False
        for timer in self._timers.values():
            timer.stop()
        logger.info("所有定时器已停止")
    
    def get_timer(self, timer_id: str) -> Optional[Timer]:
        """获取定时器"""
        return self._timers.get(timer_id)
    
    def get_running_timers(self) -> list[str]:
        """获取正在运行的定时器ID列表"""
        return [timer_id for timer_id, timer in self._timers.items() if timer.is_running]
    
    def __len__(self) -> int:
        """定时器数量"""
        return len(self._timers)
    
    def __enter__(self):
        """上下文管理器支持"""
        self.start_all()
        return self
    
    def __exit__(self, exc_type, exc_val, exc_tb):
        """上下文管理器支持"""
        self.stop_all()