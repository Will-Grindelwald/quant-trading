"""
执行处理器

负责订单的实际执行，是策略逻辑和市场交易的最终桥梁。
支持回测模拟执行和实盘真实执行。
"""

import time
import logging
import uuid
from abc import ABC, abstractmethod
from datetime import datetime, timedelta
from typing import Dict, List, Optional
import numpy as np
from ..entities.order import Order, OrderStatus, OrderSide
from ..entities.fill import Fill
from ..entities.event import OrderEvent, FillEvent
from ..engine.event_engine import EventEngine, EventHandler


logger = logging.getLogger(__name__)


class ExecutionHandler(EventHandler, ABC):
    """执行器基类"""
    
    def __init__(self, name: str, event_engine: EventEngine):
        """
        初始化执行器
        
        Args:
            name: 执行器名称
            event_engine: 事件引擎
        """
        super().__init__(name)
        self.event_engine = event_engine
        self.active_orders: Dict[str, Order] = {}
        
        # 注册事件处理
        self.event_engine.register("order", self)
    
    def handle_event(self, event):
        """处理事件"""
        if isinstance(event, OrderEvent):
            self.execute_order(event.order)
    
    @abstractmethod
    def execute_order(self, order: Order):
        """执行订单"""
        pass
    
    @abstractmethod
    def cancel_order(self, order_id: str) -> bool:
        """撤销订单"""
        pass
    
    def get_order_status(self, order_id: str) -> Optional[str]:
        """查询订单状态"""
        order = self.active_orders.get(order_id)
        return order.status.value if order else None
    
    def _send_fill_event(self, fill: Fill):
        """发送成交事件"""
        fill_event = FillEvent(
            timestamp=datetime.now(),
            data={'fill_id': fill.fill_id},
            fill=fill
        )
        self.event_engine.put(fill_event)
        logger.info(f"发送成交事件: {fill}")


class SimulatedExecutionHandler(ExecutionHandler):
    """模拟执行器（回测使用）"""
    
    def __init__(self, event_engine: EventEngine, config: Dict = None):
        """
        初始化模拟执行器
        
        Args:
            event_engine: 事件引擎
            config: 配置字典
        """
        super().__init__("SimulatedExecution", event_engine)
        self.config = config or {}
        
        # 配置参数
        self.slippage = self.config.get('slippage', 0.001)  # 滑点 0.1%
        self.commission_rate = self.config.get('commission_rate', 0.0003)  # 手续费率 0.03%
        self.min_commission = self.config.get('min_commission', 5.0)  # 最低手续费 5元
        self.execution_delay = self.config.get('execution_delay', 0)  # 执行延迟(秒)
        
        logger.info(f"模拟执行器初始化完成: 滑点={self.slippage}, 手续费率={self.commission_rate}")
    
    def execute_order(self, order: Order):
        """执行订单（模拟）"""
        try:
            logger.info(f"模拟执行订单: {order.order_id} {order.symbol} {order.side.value} "
                       f"{order.quantity}@{order.price}")
            
            # 添加到活跃订单
            self.active_orders[order.order_id] = order
            
            # 更新订单状态
            order.status = OrderStatus.SUBMITTED
            order.submitted_time = datetime.now()
            
            # 模拟执行延迟
            if self.execution_delay > 0:
                time.sleep(self.execution_delay)
            
            # 模拟市场成交
            self._simulate_fill(order)
            
        except Exception as e:
            logger.error(f"模拟执行订单失败: {e}", exc_info=True)
            order.status = OrderStatus.REJECTED
    
    def _simulate_fill(self, order: Order):
        """模拟成交"""
        # 计算成交价格（考虑滑点）
        fill_price = self._calculate_fill_price(order)
        
        # 计算手续费
        commission = self._calculate_commission(order.quantity, fill_price)
        
        # 创建成交记录
        fill = Fill(
            order_id=order.order_id,
            symbol=order.symbol,
            side=order.side.value,
            quantity=order.quantity,
            price=fill_price,
            commission=commission,
            timestamp=datetime.now(),
            strategy_id=order.strategy_id
        )
        
        # 更新订单状态
        order.fill_order(order.quantity, fill_price)
        
        # 发送成交事件
        self._send_fill_event(fill)
        
        # 从活跃订单中移除
        if order.order_id in self.active_orders:
            del self.active_orders[order.order_id]
        
        logger.info(f"模拟成交完成: {fill}")
    
    def _calculate_fill_price(self, order: Order) -> float:
        """计算成交价格（考虑滑点）"""
        base_price = order.price
        
        # 随机滑点
        slippage_factor = np.random.uniform(-self.slippage, self.slippage)
        
        # 买入时向上滑点，卖出时向下滑点
        if order.side == OrderSide.BUY:
            slippage_factor = abs(slippage_factor)
        else:
            slippage_factor = -abs(slippage_factor)
        
        fill_price = base_price * (1 + slippage_factor)
        return round(fill_price, 2)
    
    def _calculate_commission(self, quantity: int, price: float) -> float:
        """计算手续费"""
        amount = quantity * price
        commission = amount * self.commission_rate
        return max(commission, self.min_commission)
    
    def cancel_order(self, order_id: str) -> bool:
        """撤销订单"""
        if order_id not in self.active_orders:
            logger.warning(f"订单不存在或已完成: {order_id}")
            return False
        
        order = self.active_orders[order_id]
        if order.status in [OrderStatus.FILLED, OrderStatus.CANCELLED, OrderStatus.REJECTED]:
            logger.warning(f"订单状态不允许撤销: {order_id}, 状态={order.status.value}")
            return False
        
        # 更新订单状态
        order.status = OrderStatus.CANCELLED
        
        # 从活跃订单中移除
        del self.active_orders[order_id]
        
        logger.info(f"订单撤销成功: {order_id}")
        return True


class LiveExecutionHandler(ExecutionHandler):
    """实盘执行器（对接真实交易接口）"""
    
    def __init__(self, event_engine: EventEngine, config: Dict = None):
        """
        初始化实盘执行器
        
        Args:
            event_engine: 事件引擎
            config: 配置字典
        """
        super().__init__("LiveExecution", event_engine)
        self.config = config or {}
        
        # 连接参数
        self.account = self.config.get('account')
        self.password = self.config.get('password')
        
        # 风控参数
        self.max_order_value = self.config.get('max_order_value', 1000000)  # 单笔最大金额
        self.max_daily_orders = self.config.get('max_daily_orders', 100)   # 日最大订单数
        
        self.daily_order_count = 0
        self.last_reset_date = datetime.now().date()
        
        logger.info("实盘执行器初始化完成")
    
    def execute_order(self, order: Order):
        """执行订单（实盘）"""
        try:
            logger.info(f"实盘执行订单: {order.order_id} {order.symbol} {order.side.value} "
                       f"{order.quantity}@{order.price}")
            
            # 风控检查
            if not self._risk_check(order):
                order.status = OrderStatus.REJECTED
                logger.warning(f"订单被风控拒绝: {order.order_id}")
                return
            
            # 添加到活跃订单
            self.active_orders[order.order_id] = order
            
            # 更新订单状态
            order.status = OrderStatus.SUBMITTED
            order.submitted_time = datetime.now()
            
            # TODO: 对接真实交易接口（如MiniQMT）
            # 这里先用模拟实现
            self._simulate_live_execution(order)
            
        except Exception as e:
            logger.error(f"实盘执行订单失败: {e}", exc_info=True)
            order.status = OrderStatus.REJECTED
    
    def _risk_check(self, order: Order) -> bool:
        """风控检查"""
        # 重置日计数器
        today = datetime.now().date()
        if today != self.last_reset_date:
            self.daily_order_count = 0
            self.last_reset_date = today
        
        # 检查订单金额
        order_value = order.quantity * order.price
        if order_value > self.max_order_value:
            logger.warning(f"订单金额超限: {order_value} > {self.max_order_value}")
            return False
        
        # 检查日订单数量
        if self.daily_order_count >= self.max_daily_orders:
            logger.warning(f"日订单数量超限: {self.daily_order_count} >= {self.max_daily_orders}")
            return False
        
        return True
    
    def _simulate_live_execution(self, order: Order):
        """模拟实盘执行（实际应该对接真实接口）"""
        # 这里用简化的模拟实现，实际应该调用真实的交易接口
        
        # 模拟网络延迟
        time.sleep(0.1)
        
        # 模拟成交
        fill = Fill(
            order_id=order.order_id,
            symbol=order.symbol,
            side=order.side.value,
            quantity=order.quantity,
            price=order.price,  # 实盘通常按限价成交
            commission=order.quantity * order.price * 0.0003,  # 简化手续费计算
            timestamp=datetime.now(),
            strategy_id=order.strategy_id
        )
        
        # 更新订单状态
        order.fill_order(order.quantity, order.price)
        
        # 更新计数器
        self.daily_order_count += 1
        
        # 发送成交事件
        self._send_fill_event(fill)
        
        # 从活跃订单中移除
        if order.order_id in self.active_orders:
            del self.active_orders[order.order_id]
        
        logger.info(f"实盘成交完成: {fill}")
    
    def cancel_order(self, order_id: str) -> bool:
        """撤销订单"""
        if order_id not in self.active_orders:
            logger.warning(f"订单不存在或已完成: {order_id}")
            return False
        
        order = self.active_orders[order_id]
        
        try:
            # TODO: 调用真实交易接口撤单
            # 这里先用模拟实现
            
            order.status = OrderStatus.CANCELLED
            del self.active_orders[order_id]
            
            logger.info(f"实盘订单撤销成功: {order_id}")
            return True
            
        except Exception as e:
            logger.error(f"实盘撤单失败: {e}", exc_info=True)
            return False
    
    def get_positions(self) -> Dict:
        """获取实盘持仓（TODO: 对接真实接口）"""
        # 实际应该从交易接口获取持仓信息
        return {}
    
    def get_account_info(self) -> Dict:
        """获取账户信息（TODO: 对接真实接口）"""
        # 实际应该从交易接口获取账户信息
        return {
            'total_assets': 1000000.0,
            'available_cash': 500000.0,
            'market_value': 500000.0
        }