"""
组合风控管理器

作为策略模块和执行模块之间的桥梁，负责信号处理、仓位管理、风险控制和订单生成。
"""

import logging
from datetime import datetime
from typing import Dict, Set, Optional
from ..entities.event import SignalEvent, OrderEvent, FillEvent
from ..entities.signal import Signal, SignalDirection
from ..entities.order import Order, OrderType, OrderSide
from ..entities.account import Account
from ..engine.event_engine import EventEngine, EventHandler


logger = logging.getLogger(__name__)


class PortfolioRiskManager(EventHandler):
    """组合风控管理器"""
    
    def __init__(self, account: Account, event_engine: EventEngine, config: Dict = None):
        """
        初始化组合风控管理器
        
        Args:
            account: 交易账户
            event_engine: 事件引擎
            config: 配置字典
        """
        super().__init__("PortfolioRiskManager")
        self.account = account
        self.event_engine = event_engine
        self.config = config or {}
        
        # 风控参数
        self.max_position_pct = self.config.get('max_position_pct', 0.05)  # 单股最大仓位 5%
        self.max_total_position_pct = self.config.get('max_total_position_pct', 0.95)  # 最大总仓位 95%
        self.min_order_amount = self.config.get('min_order_amount', 1000)  # 最小订单金额
        self.position_size_method = self.config.get('position_size_method', 'fixed_amount')  # 仓位计算方法
        self.default_position_size = self.config.get('default_position_size', 10000)  # 默认仓位大小
        
        # 信号去重
        self.recent_signals: Set[str] = set()  # 最近处理的信号
        self.signal_cooldown = self.config.get('signal_cooldown', 300)  # 信号冷却时间(秒)
        
        # 注册事件处理
        self.event_engine.register("signal", self)
        self.event_engine.register("fill", self)
        
        logger.info(f"组合风控管理器初始化完成: 最大单股仓位={self.max_position_pct:.1%}, "
                   f"最大总仓位={self.max_total_position_pct:.1%}")
    
    def handle_event(self, event):
        """处理事件"""
        if isinstance(event, SignalEvent):
            self.on_signal(event)
        elif isinstance(event, FillEvent):
            self.on_fill(event)
    
    def on_signal(self, event: SignalEvent):
        """处理策略信号"""
        try:
            # 创建信号对象
            signal = Signal(
                strategy_id=event.strategy_id,
                symbol=event.symbol,
                direction=SignalDirection(event.direction),
                strength=event.strength,
                timestamp=event.timestamp,
                price=event.price,
                reason=event.reason
            )
            
            logger.info(f"收到信号: {signal.symbol} {signal.direction.value} "
                       f"强度={signal.strength:.2f} 价格={signal.price:.2f}")
            
            # 信号去重检查
            if self._is_duplicate_signal(signal):
                logger.debug(f"重复信号，跳过: {signal.symbol}")
                return
            
            # 风险检查
            if not self._check_risk(signal):
                logger.warning(f"风控检查未通过: {signal.symbol}")
                return
            
            # 信号转订单
            order = self._signal_to_order(signal)
            if order:
                # 发送订单事件
                self._send_order_event(order)
                # 记录信号
                self._record_signal(signal)
            
        except Exception as e:
            logger.error(f"处理信号事件失败: {e}", exc_info=True)
    
    def on_fill(self, event: FillEvent):
        """处理成交回报"""
        try:
            fill = event.fill
            logger.info(f"收到成交回报: {fill}")
            
            # 更新账户
            self.account.update_position(fill)
            
            # 解冻资金
            if fill.side == 'BUY':
                frozen_amount = fill.quantity * fill.price + fill.commission
                self.account.unfreeze_cash(frozen_amount)
            
            logger.debug(f"账户状态更新完成: 现金={self.account.cash:.2f}, "
                        f"持仓数={len(self.account.positions)}")
            
        except Exception as e:
            logger.error(f"处理成交回报失败: {e}", exc_info=True)
    
    def _is_duplicate_signal(self, signal: Signal) -> bool:
        """检查是否为重复信号"""
        signal_key = f"{signal.symbol}_{signal.direction.value}_{signal.strategy_id}"
        
        # 简化的去重逻辑，实际项目中可能需要更复杂的时间窗口检查
        if signal_key in self.recent_signals:
            return True
        
        return False
    
    def _record_signal(self, signal: Signal):
        """记录信号（用于去重）"""
        signal_key = f"{signal.symbol}_{signal.direction.value}_{signal.strategy_id}"
        self.recent_signals.add(signal_key)
        
        # 清理过期信号记录（简化实现）
        if len(self.recent_signals) > 1000:
            self.recent_signals.clear()
    
    def _check_risk(self, signal: Signal) -> bool:
        """风险检查"""
        try:
            symbol = signal.symbol
            direction = signal.direction
            
            if direction == SignalDirection.BUY:
                return self._check_buy_risk(signal)
            elif direction == SignalDirection.SELL:
                return self._check_sell_risk(signal)
            
            return True
            
        except Exception as e:
            logger.error(f"风险检查失败: {e}")
            return False
    
    def _check_buy_risk(self, signal: Signal) -> bool:
        """买入风险检查"""
        symbol = signal.symbol
        
        # 检查是否已有持仓
        current_position = self.account.get_position(symbol)
        if current_position and not current_position.is_empty:
            logger.debug(f"已有持仓，跳过买入信号: {symbol}")
            return False
        
        # 计算订单大小
        order_amount = self._calculate_position_size(signal)
        if order_amount < self.min_order_amount:
            logger.debug(f"订单金额过小: {order_amount} < {self.min_order_amount}")
            return False
        
        # 检查可用资金
        if self.account.available_cash < order_amount:
            logger.warning(f"可用资金不足: {self.account.available_cash} < {order_amount}")
            return False
        
        # 检查单股仓位限制
        total_value = self.account.get_total_value({symbol: signal.price})
        max_position_amount = total_value * self.max_position_pct
        if order_amount > max_position_amount:
            logger.warning(f"超过单股最大仓位限制: {order_amount} > {max_position_amount}")
            return False
        
        # 检查总仓位限制
        current_position_value = self.account.get_position_value({symbol: signal.price})
        total_position_value = current_position_value + order_amount
        max_total_position_value = total_value * self.max_total_position_pct
        
        if total_position_value > max_total_position_value:
            logger.warning(f"超过总仓位限制: {total_position_value} > {max_total_position_value}")
            return False
        
        return True
    
    def _check_sell_risk(self, signal: Signal) -> bool:
        """卖出风险检查"""
        symbol = signal.symbol
        
        # 检查是否有持仓
        current_position = self.account.get_position(symbol)
        if not current_position or current_position.is_empty:
            logger.debug(f"无持仓，跳过卖出信号: {symbol}")
            return False
        
        return True
    
    def _calculate_position_size(self, signal: Signal) -> float:
        """计算仓位大小"""
        if self.position_size_method == 'fixed_amount':
            # 固定金额
            return self.default_position_size
        
        elif self.position_size_method == 'percent_of_portfolio':
            # 按组合百分比
            total_value = self.account.get_total_value({signal.symbol: signal.price})
            return total_value * self.max_position_pct
        
        elif self.position_size_method == 'signal_strength':
            # 基于信号强度
            base_amount = self.default_position_size
            return base_amount * signal.strength
        
        else:
            return self.default_position_size
    
    def _signal_to_order(self, signal: Signal) -> Optional[Order]:
        """信号转订单"""
        try:
            symbol = signal.symbol
            direction = signal.direction
            price = signal.price
            
            if direction == SignalDirection.BUY:
                # 买入订单
                order_amount = self._calculate_position_size(signal)
                quantity = int(order_amount / price / 100) * 100  # A股最小交易单位100股
                
                if quantity < 100:
                    logger.debug(f"买入数量不足100股: {quantity}")
                    return None
                
                # 冻结资金
                required_cash = quantity * price * 1.001  # 预留手续费
                if not self.account.freeze_cash(required_cash, f"temp_{symbol}"):
                    logger.warning(f"冻结资金失败: {required_cash}")
                    return None
                
                order = Order(
                    symbol=symbol,
                    order_type=OrderType.LIMIT,
                    side=OrderSide.BUY,
                    quantity=quantity,
                    price=price,
                    strategy_id=signal.strategy_id
                )
                
            elif direction == SignalDirection.SELL:
                # 卖出订单
                current_position = self.account.get_position(symbol)
                if not current_position or current_position.is_empty:
                    return None
                
                quantity = abs(current_position.quantity)  # 全部卖出
                
                order = Order(
                    symbol=symbol,
                    order_type=OrderType.LIMIT,
                    side=OrderSide.SELL,
                    quantity=quantity,
                    price=price,
                    strategy_id=signal.strategy_id
                )
            
            else:
                logger.warning(f"不支持的信号方向: {direction}")
                return None
            
            logger.info(f"生成订单: {symbol} {order.side.value} {order.quantity}@{order.price:.2f}")
            return order
            
        except Exception as e:
            logger.error(f"信号转订单失败: {e}", exc_info=True)
            return None
    
    def _send_order_event(self, order: Order):
        """发送订单事件"""
        # 添加到账户订单记录
        self.account.add_order(order)
        
        # 发送订单事件
        order_event = OrderEvent(
            timestamp=datetime.now(),
            data={'order_id': order.order_id},
            order=order
        )
        
        self.event_engine.put(order_event)
        logger.debug(f"发送订单事件: {order.order_id}")
    
    def get_portfolio_stats(self) -> Dict:
        """获取组合统计信息"""
        # 获取当前价格（简化实现，实际应该从数据源获取）
        current_prices = {}
        for symbol in self.account.positions.keys():
            current_prices[symbol] = self.account.positions[symbol].avg_price  # 简化使用成本价
        
        total_value = self.account.get_total_value(current_prices)
        position_value = self.account.get_position_value(current_prices)
        
        return {
            'account_id': self.account.account_id,
            'total_value': total_value,
            'cash': self.account.cash,
            'frozen_cash': self.account.frozen_cash,
            'position_value': position_value,
            'position_count': len(self.account.positions),
            'leverage': self.account.get_leverage(current_prices),
            'total_commission': self.account.total_commission,
            'realized_pnl': self.account.total_realized_pnl,
            'unrealized_pnl': self.account.get_unrealized_pnl(current_prices)
        }