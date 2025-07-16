"""
均线交叉策略

经典的双均线交叉策略实现，支持开单和止损。
"""

import logging
from datetime import datetime
from typing import List, Set
from ..entities.event import MarketEvent
from ..entities.signal import Signal, SignalDirection
from ..entities.bar import Bar, Frequency
from ..entities.strategy import StrategyType
from .base_strategy import BaseStrategy


logger = logging.getLogger(__name__)


class MACrossStrategy(BaseStrategy):
    """双均线交叉策略"""
    
    def __init__(self, strategy_instance, event_engine, data_handler):
        """
        初始化均线交叉策略
        
        Args:
            strategy_instance: 策略实例配置
            event_engine: 事件引擎
            data_handler: 数据处理器
        """
        super().__init__(strategy_instance, event_engine)
        self.data_handler = data_handler
        
        # 策略参数
        self.short_window = self.config.get('short_window', 5)    # 短周期均线
        self.long_window = self.config.get('long_window', 20)     # 长周期均线
        self.stop_loss_pct = self.config.get('stop_loss_pct', 0.05)  # 止损比例 5%
        self.take_profit_pct = self.config.get('take_profit_pct', 0.10)  # 止盈比例 10%
        
        # 股票池
        self.universe = set(self.config.get('universe', []))
        
        logger.info(f"均线交叉策略初始化: 短周期={self.short_window}, "
                   f"长周期={self.long_window}, 股票池={len(self.universe)}支")
    
    def get_watch_symbols(self) -> Set[str]:
        """获取策略关注的标的列表"""
        if self.strategy_type == StrategyType.ENTRY:
            # 开单策略：关注股票池中未持有的标的
            strategy_positions = self.get_strategy_positions()
            return self.universe - strategy_positions
            
        elif self.strategy_type == StrategyType.EXIT:
            # 止盈止损策略：仅关注本策略开单的持仓标的
            return self.get_strategy_positions()
            
        elif self.strategy_type == StrategyType.UNIVERSAL_STOP:
            # 通用止损策略：关注所有持仓标的
            return self.get_position_symbols()
        
        return set()
    
    def generate_signals(self, market_event: MarketEvent) -> List[Signal]:
        """生成交易信号"""
        signals = []
        
        try:
            symbol = market_event.symbol
            current_bar = market_event.bar_data
            
            if self.strategy_type == StrategyType.ENTRY:
                # 开单信号
                entry_signal = self._check_entry_signal(symbol, current_bar)
                if entry_signal:
                    signals.append(entry_signal)
                    
            elif self.strategy_type == StrategyType.EXIT:
                # 止盈止损信号
                exit_signal = self._check_exit_signal(symbol, current_bar)
                if exit_signal:
                    signals.append(exit_signal)
                    
            elif self.strategy_type == StrategyType.UNIVERSAL_STOP:
                # 强制止损信号
                stop_signal = self._check_universal_stop(symbol, current_bar)
                if stop_signal:
                    signals.append(stop_signal)
        
        except Exception as e:
            logger.error(f"生成信号失败 {symbol}: {e}")
        
        return signals
    
    def _check_entry_signal(self, symbol: str, current_bar: Bar) -> Signal:
        """检查开单信号"""
        try:
            # 获取历史数据计算均线
            historical_bars = self._get_historical_data(symbol, self.long_window + 5)
            
            if len(historical_bars) < self.long_window:
                return None
            
            # 计算短期和长期均线
            short_ma = self._calculate_ma(historical_bars, self.short_window)
            long_ma = self._calculate_ma(historical_bars, self.long_window)
            
            if short_ma is None or long_ma is None:
                return None
            
            # 获取前一根K线的均线值
            prev_short_ma = self._calculate_ma(historical_bars[:-1], self.short_window)
            prev_long_ma = self._calculate_ma(historical_bars[:-1], self.long_window)
            
            if prev_short_ma is None or prev_long_ma is None:
                return None
            
            # 检查金叉：短均线从下方穿越长均线
            if (prev_short_ma <= prev_long_ma and 
                short_ma > long_ma and 
                current_bar.close > short_ma):  # 价格在短均线上方
                
                return Signal(
                    strategy_id=self.strategy_id,
                    symbol=symbol,
                    direction=SignalDirection.BUY,
                    strength=0.8,  # 信号强度
                    timestamp=current_bar.datetime,
                    price=current_bar.close,
                    reason=f"金叉信号: 短均线({short_ma:.2f}) > 长均线({long_ma:.2f}), 价格({current_bar.close:.2f})"
                )
        
        except Exception as e:
            logger.debug(f"检查开单信号失败 {symbol}: {e}")
        
        return None
    
    def _check_exit_signal(self, symbol: str, current_bar: Bar) -> Signal:
        """检查止盈止损信号"""
        try:
            # 获取当前持仓
            if not self.portfolio_manager or not self.portfolio_manager.account:
                return None
                
            position = self.portfolio_manager.account.get_position(symbol)
            if not position or position.is_empty:
                return None
            
            current_price = current_bar.close
            cost_price = position.avg_price
            
            # 计算盈亏比例
            pnl_pct = (current_price - cost_price) / cost_price
            
            # 止损检查
            if pnl_pct <= -self.stop_loss_pct:
                return Signal(
                    strategy_id=self.strategy_id,
                    symbol=symbol,
                    direction=SignalDirection.SELL,
                    strength=1.0,  # 止损信号强度最高
                    timestamp=current_bar.datetime,
                    price=current_price,
                    reason=f"止损: 亏损{pnl_pct:.2%}, 成本价{cost_price:.2f}, 当前价{current_price:.2f}"
                )
            
            # 止盈检查
            if pnl_pct >= self.take_profit_pct:
                return Signal(
                    strategy_id=self.strategy_id,
                    symbol=symbol,
                    direction=SignalDirection.SELL,
                    strength=0.9,
                    timestamp=current_bar.datetime,
                    price=current_price,
                    reason=f"止盈: 盈利{pnl_pct:.2%}, 成本价{cost_price:.2f}, 当前价{current_price:.2f}"
                )
            
            # 技术止损：死叉
            historical_bars = self._get_historical_data(symbol, self.long_window + 5)
            if len(historical_bars) >= self.long_window:
                short_ma = self._calculate_ma(historical_bars, self.short_window)
                long_ma = self._calculate_ma(historical_bars, self.long_window)
                prev_short_ma = self._calculate_ma(historical_bars[:-1], self.short_window)
                prev_long_ma = self._calculate_ma(historical_bars[:-1], self.long_window)
                
                # 检查死叉：短均线从上方跌破长均线
                if (prev_short_ma >= prev_long_ma and 
                    short_ma < long_ma and
                    pnl_pct > 0):  # 有盈利时才技术止盈
                    
                    return Signal(
                        strategy_id=self.strategy_id,
                        symbol=symbol,
                        direction=SignalDirection.SELL,
                        strength=0.7,
                        timestamp=current_bar.datetime,
                        price=current_price,
                        reason=f"技术止盈: 死叉信号, 盈利{pnl_pct:.2%}"
                    )
        
        except Exception as e:
            logger.debug(f"检查止盈止损信号失败 {symbol}: {e}")
        
        return None
    
    def _check_universal_stop(self, symbol: str, current_bar: Bar) -> Signal:
        """检查强制止损信号"""
        try:
            # 获取当前持仓
            if not self.portfolio_manager or not self.portfolio_manager.account:
                return None
                
            position = self.portfolio_manager.account.get_position(symbol)
            if not position or position.is_empty:
                return None
            
            current_price = current_bar.close
            cost_price = position.avg_price
            pnl_pct = (current_price - cost_price) / cost_price
            
            # 强制止损阈值（比普通止损更严格）
            universal_stop_pct = self.config.get('universal_stop_pct', 0.08)  # 8%
            
            if pnl_pct <= -universal_stop_pct:
                return Signal(
                    strategy_id=self.strategy_id,
                    symbol=symbol,
                    direction=SignalDirection.SELL,
                    strength=1.0,
                    timestamp=current_bar.datetime,
                    price=current_price,
                    reason=f"强制止损: 亏损{pnl_pct:.2%}超过阈值{universal_stop_pct:.2%}"
                )
        
        except Exception as e:
            logger.debug(f"检查强制止损信号失败 {symbol}: {e}")
        
        return None
    
    def _get_historical_data(self, symbol: str, count: int) -> List[Bar]:
        """获取历史数据"""
        try:
            # 获取最新的历史数据
            bars_dict = self.data_handler.get_latest_bars([symbol], Frequency.DAILY, count)
            return bars_dict.get(symbol, [])
        except Exception as e:
            logger.debug(f"获取历史数据失败 {symbol}: {e}")
            return []
    
    def _calculate_ma(self, bars: List[Bar], window: int) -> float:
        """计算移动平均线"""
        if len(bars) < window:
            return None
        
        prices = [bar.close for bar in bars[-window:]]
        return sum(prices) / len(prices)
    
    def set_universe(self, symbols: List[str]):
        """设置股票池"""
        self.universe = set(symbols)
        logger.info(f"更新股票池: {len(symbols)}支股票")