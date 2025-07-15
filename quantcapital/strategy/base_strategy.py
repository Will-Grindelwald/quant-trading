"""
策略基类

定义策略的基础架构和接口，支持多种策略类型的协调运行。
"""

import logging
from abc import ABC, abstractmethod
from datetime import datetime
from typing import List, Dict, Optional, Set
from ..entities.event import MarketEvent, SignalEvent
from ..entities.signal import Signal, SignalDirection
from ..entities.bar import Bar
from ..entities.strategy import StrategyType, StrategyInstance
from ..engine.event_engine import EventEngine, EventHandler


logger = logging.getLogger(__name__)


class BaseStrategy(EventHandler, ABC):
    """策略基类"""
    
    def __init__(self, strategy_instance: StrategyInstance, event_engine: EventEngine):
        """
        初始化策略
        
        Args:
            strategy_instance: 策略实例配置
            event_engine: 事件引擎
        """
        super().__init__(f"Strategy-{strategy_instance.strategy_id}")
        self.strategy_instance = strategy_instance
        self.event_engine = event_engine
        self.portfolio_manager = None  # 将由策略管理器设置
        
        # 策略状态
        self.is_active = strategy_instance.enabled
        self.last_update_time: Optional[datetime] = None
        
        # 注册事件处理
        self.event_engine.register("market", self)
        
        logger.info(f"策略初始化: {self.strategy_instance}")
    
    @property
    def strategy_id(self) -> str:
        """策略ID"""
        return self.strategy_instance.strategy_id
    
    @property
    def strategy_type(self) -> StrategyType:
        """策略类型"""
        return self.strategy_instance.strategy_type
    
    @property
    def config(self) -> Dict:
        """策略配置"""
        return self.strategy_instance.config
    
    def handle_event(self, event):
        """处理事件"""
        if not self.is_active:
            return
            
        if isinstance(event, MarketEvent):
            self.on_market_event(event)
    
    def on_market_event(self, event: MarketEvent):
        """处理行情更新事件"""
        # 只处理关注标的的行情
        watch_symbols = self.get_watch_symbols()
        if event.symbol not in watch_symbols:
            return
        
        try:
            # 生成交易信号
            signals = self.generate_signals(event)
            
            # 发送信号
            for signal in signals:
                if signal.is_valid():
                    self._send_signal(signal)
                else:
                    logger.warning(f"无效信号: {signal}")
                    
            self.last_update_time = datetime.now()
            
        except Exception as e:
            logger.error(f"策略 {self.strategy_id} 处理市场事件失败: {e}", exc_info=True)
    
    @abstractmethod
    def get_watch_symbols(self) -> Set[str]:
        """
        获取策略关注的标的列表
        
        子类必须实现此方法，根据策略类型返回不同的关注标的：
        - 开单策略: 全市场 - 本策略已持有标的
        - 止盈止损策略: 仅本策略开单的持仓标的
        - 通用止损策略: 所有持有标的
        """
        pass
    
    @abstractmethod
    def generate_signals(self, market_event: MarketEvent) -> List[Signal]:
        """
        生成交易信号
        
        Args:
            market_event: 市场行情事件
            
        Returns:
            生成的信号列表
        """
        pass
    
    def _send_signal(self, signal: Signal):
        """发送交易信号"""
        signal_event = SignalEvent(
            timestamp=datetime.now(),
            data={'signal_id': f"{self.strategy_id}_{signal.symbol}_{signal.timestamp}"},
            strategy_id=self.strategy_id,
            symbol=signal.symbol,
            direction=signal.direction.value,
            strength=signal.strength,
            price=signal.price,
            reason=signal.reason
        )
        
        self.event_engine.put(signal_event)
        logger.debug(f"发送信号: {signal}")
    
    def get_position_symbols(self) -> Set[str]:
        """获取当前持仓标的"""
        if self.portfolio_manager and self.portfolio_manager.account:
            return set(self.portfolio_manager.account.positions.keys())
        return set()
    
    def get_strategy_positions(self) -> Set[str]:
        """获取本策略开仓的标的"""
        if self.portfolio_manager and self.portfolio_manager.account:
            strategy_positions = set()
            for symbol, position in self.portfolio_manager.account.positions.items():
                if position.strategy_id == self.strategy_id:
                    strategy_positions.add(symbol)
            return strategy_positions
        return set()
    
    def set_portfolio_manager(self, portfolio_manager):
        """设置组合管理器（由策略管理器调用）"""
        self.portfolio_manager = portfolio_manager
    
    def activate(self):
        """激活策略"""
        self.is_active = True
        logger.info(f"策略已激活: {self.strategy_id}")
    
    def deactivate(self):
        """停用策略"""
        self.is_active = False
        logger.info(f"策略已停用: {self.strategy_id}")


class StrategyManager:
    """策略管理器"""
    
    def __init__(self, event_engine: EventEngine):
        """
        初始化策略管理器
        
        Args:
            event_engine: 事件引擎
        """
        self.event_engine = event_engine
        self.strategies: Dict[str, BaseStrategy] = {}
        self.portfolio_manager = None
        
        logger.info("策略管理器初始化完成")
    
    def add_strategy(self, strategy: BaseStrategy) -> bool:
        """
        添加策略
        
        Args:
            strategy: 策略实例
            
        Returns:
            是否添加成功
        """
        if strategy.strategy_id in self.strategies:
            logger.warning(f"策略已存在: {strategy.strategy_id}")
            return False
        
        # 设置组合管理器引用
        if self.portfolio_manager:
            strategy.set_portfolio_manager(self.portfolio_manager)
        
        self.strategies[strategy.strategy_id] = strategy
        strategy.start()  # 启动策略处理线程
        
        logger.info(f"添加策略: {strategy.strategy_id}")
        return True
    
    def remove_strategy(self, strategy_id: str) -> bool:
        """
        移除策略
        
        Args:
            strategy_id: 策略ID
            
        Returns:
            是否移除成功
        """
        if strategy_id not in self.strategies:
            logger.warning(f"策略不存在: {strategy_id}")
            return False
        
        strategy = self.strategies[strategy_id]
        strategy.stop()  # 停止策略处理线程
        del self.strategies[strategy_id]
        
        logger.info(f"移除策略: {strategy_id}")
        return True
    
    def get_strategy(self, strategy_id: str) -> Optional[BaseStrategy]:
        """获取策略实例"""
        return self.strategies.get(strategy_id)
    
    def get_all_strategies(self) -> Dict[str, BaseStrategy]:
        """获取所有策略"""
        return self.strategies.copy()
    
    def activate_strategy(self, strategy_id: str) -> bool:
        """激活策略"""
        strategy = self.strategies.get(strategy_id)
        if strategy:
            strategy.activate()
            return True
        return False
    
    def deactivate_strategy(self, strategy_id: str) -> bool:
        """停用策略"""
        strategy = self.strategies.get(strategy_id)
        if strategy:
            strategy.deactivate()
            return True
        return False
    
    def set_portfolio_manager(self, portfolio_manager):
        """设置组合管理器"""
        self.portfolio_manager = portfolio_manager
        
        # 为所有现有策略设置组合管理器
        for strategy in self.strategies.values():
            strategy.set_portfolio_manager(portfolio_manager)
    
    def start_all(self):
        """启动所有策略"""
        for strategy in self.strategies.values():
            if not strategy.running:
                strategy.start()
        logger.info(f"启动了 {len(self.strategies)} 个策略")
    
    def stop_all(self):
        """停止所有策略"""
        for strategy in self.strategies.values():
            strategy.stop()
        logger.info("所有策略已停止")
    
    def get_statistics(self) -> Dict:
        """获取策略统计信息"""
        stats = {
            'total_strategies': len(self.strategies),
            'active_strategies': sum(1 for s in self.strategies.values() if s.is_active),
            'strategy_details': {}
        }
        
        for strategy_id, strategy in self.strategies.items():
            stats['strategy_details'][strategy_id] = {
                'type': strategy.strategy_type.value,
                'active': strategy.is_active,
                'last_update': strategy.last_update_time
            }
        
        return stats