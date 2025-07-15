"""
回测引擎

整合所有组件，提供完整的回测功能。
"""

import logging
from datetime import datetime, timedelta
from typing import List, Dict, Optional
from ..config import ConfigManager
from ..engine import EventEngine
from ..data import BacktestDataHandler, DataUpdater
from ..execution import SimulatedExecutionHandler
from ..entities import Account, StrategyInstance, StrategyType, Frequency
from ..strategy import StrategyManager, BaseStrategy
from ..portfolio import PortfolioRiskManager
from .market_simulator import MarketDataSimulator


logger = logging.getLogger(__name__)


class BacktestEngine:
    """回测引擎"""
    
    def __init__(self, config: ConfigManager):
        """
        初始化回测引擎
        
        Args:
            config: 配置管理器
        """
        self.config = config
        
        # 回测参数
        self.start_date: Optional[datetime] = None
        self.end_date: Optional[datetime] = None
        self.initial_capital = config.get('initial_capital', 1000000.0)
        self.universe: List[str] = []
        
        # 核心组件
        self.event_engine = EventEngine()
        self.data_handler = None
        self.execution_handler = None
        self.account = None
        self.portfolio_manager = None
        self.strategy_manager = None
        self.market_simulator = None
        
        # 回测状态
        self.is_running = False
        self.current_time: Optional[datetime] = None
        
        logger.info("回测引擎初始化完成")
    
    def setup(self, start_date: datetime, end_date: datetime, 
              universe: List[str], strategies: List[BaseStrategy] = None):
        """
        设置回测参数
        
        Args:
            start_date: 回测开始日期
            end_date: 回测结束日期
            universe: 股票池
            strategies: 策略列表
        """
        self.start_date = start_date
        self.end_date = end_date
        self.universe = universe
        
        logger.info(f"回测设置: {start_date.date()} - {end_date.date()}, "
                   f"{len(universe)}支股票")
        
        # 初始化各个组件
        self._initialize_components()
        
        # 添加策略
        if strategies:
            for strategy in strategies:
                self.add_strategy(strategy)
    
    def _initialize_components(self):
        """初始化各个组件"""
        # 数据处理器
        self.data_handler = BacktestDataHandler(
            data_root=self.config.get('data_root'),
            business_db_path=self.config.get('business_db_path')
        )
        
        # 执行器
        execution_config = self.config.get('execution', {})
        self.execution_handler = SimulatedExecutionHandler(
            self.event_engine, execution_config
        )
        
        # 交易账户
        self.account = Account(
            account_id="backtest_account",
            initial_capital=self.initial_capital,
            cash=self.initial_capital
        )
        
        # 组合风控管理器
        portfolio_config = self.config.get('portfolio', {})
        self.portfolio_manager = PortfolioRiskManager(
            self.account, self.event_engine, portfolio_config
        )
        
        # 策略管理器
        self.strategy_manager = StrategyManager(self.event_engine)
        self.strategy_manager.set_portfolio_manager(self.portfolio_manager)
        
        # 市场数据模拟器
        self.market_simulator = MarketDataSimulator(
            self.event_engine, self.data_handler
        )
        
        logger.info("回测组件初始化完成")
    
    def add_strategy(self, strategy: BaseStrategy) -> bool:
        """添加策略"""
        if not self.strategy_manager:
            logger.error("策略管理器未初始化")
            return False
        
        return self.strategy_manager.add_strategy(strategy)
    
    def prepare_data(self) -> bool:
        """准备回测数据"""
        try:
            logger.info("开始准备回测数据...")
            
            # 预加载数据到内存
            self.data_handler.load_data_to_memory(
                self.universe, self.start_date, self.end_date, Frequency.DAILY
            )
            
            logger.info("回测数据准备完成")
            return True
            
        except Exception as e:
            logger.error(f"准备回测数据失败: {e}", exc_info=True)
            return False
    
    def run(self) -> bool:
        """运行回测"""
        try:
            if not self._validate_setup():
                return False
            
            logger.info("开始运行回测...")
            self.is_running = True
            
            # 启动事件引擎和各个组件
            self._start_components()
            
            # 运行回测循环
            self._run_backtest_loop()
            
            # 停止各个组件
            self._stop_components()
            
            self.is_running = False
            logger.info("回测运行完成")
            return True
            
        except Exception as e:
            logger.error(f"回测运行失败: {e}", exc_info=True)
            self.is_running = False
            return False
    
    def _validate_setup(self) -> bool:
        """验证回测设置"""
        if not self.start_date or not self.end_date:
            logger.error("未设置回测时间范围")
            return False
        
        if self.start_date >= self.end_date:
            logger.error("回测开始时间必须早于结束时间")
            return False
        
        if not self.universe:
            logger.error("未设置股票池")
            return False
        
        if not self.strategy_manager or len(self.strategy_manager.strategies) == 0:
            logger.error("未添加任何策略")
            return False
        
        return True
    
    def _start_components(self):
        """启动各个组件"""
        self.event_engine.start()
        self.execution_handler.start()
        self.portfolio_manager.start()
        self.strategy_manager.start_all()
        logger.debug("所有组件已启动")
    
    def _stop_components(self):
        """停止各个组件"""
        self.strategy_manager.stop_all()
        self.portfolio_manager.stop()
        self.execution_handler.stop()
        self.event_engine.stop()
        logger.debug("所有组件已停止")
    
    def _run_backtest_loop(self):
        """运行回测循环"""
        current_date = self.start_date
        day_count = 0
        
        while current_date <= self.end_date:
            # 跳过非交易日
            if not self.data_handler.is_trading_day(current_date):
                current_date += timedelta(days=1)
                continue
            
            # 设置当前时间
            self.current_time = current_date
            self.data_handler.set_current_time(current_date)
            
            # 模拟市场数据
            self.market_simulator.simulate_trading_day(current_date, self.universe)
            
            # 进入下一天
            current_date += timedelta(days=1)
            day_count += 1
            
            if day_count % 10 == 0:
                logger.info(f"回测进度: {current_date.date()}, "
                           f"账户价值: {self._get_account_value():.2f}")
    
    def _get_account_value(self) -> float:
        """获取账户总价值"""
        if not self.account:
            return 0.0
        
        # 简化实现，使用成本价计算
        current_prices = {}
        for symbol in self.account.positions.keys():
            current_prices[symbol] = self.account.positions[symbol].avg_price
        
        return self.account.get_total_value(current_prices)
    
    def get_results(self) -> Dict:
        """获取回测结果"""
        if not self.account:
            return {}
        
        # 计算基本统计
        final_value = self._get_account_value()
        total_return = (final_value - self.initial_capital) / self.initial_capital
        
        # 交易统计
        total_trades = len(self.account.trades)
        winning_trades = len([t for t in self.account.trades if t.realized_pnl > 0])
        win_rate = winning_trades / total_trades if total_trades > 0 else 0
        
        results = {
            'start_date': self.start_date,
            'end_date': self.end_date,
            'initial_capital': self.initial_capital,
            'final_value': final_value,
            'total_return': total_return,
            'total_return_pct': total_return * 100,
            'total_trades': total_trades,
            'winning_trades': winning_trades,
            'losing_trades': total_trades - winning_trades,
            'win_rate': win_rate,
            'win_rate_pct': win_rate * 100,
            'total_commission': self.account.total_commission,
            'realized_pnl': self.account.total_realized_pnl,
            'portfolio_stats': self.portfolio_manager.get_portfolio_stats() if self.portfolio_manager else {},
            'strategy_stats': self.strategy_manager.get_statistics() if self.strategy_manager else {}
        }
        
        return results
    
    def print_results(self):
        """打印回测结果"""
        results = self.get_results()
        
        print("\n" + "=" * 60)
        print("回测结果摘要")
        print("=" * 60)
        print(f"回测期间: {results['start_date'].date()} - {results['end_date'].date()}")
        print(f"初始资金: {results['initial_capital']:,.2f} 元")
        print(f"最终价值: {results['final_value']:,.2f} 元")
        print(f"总收益率: {results['total_return_pct']:.2f}%")
        print(f"总交易次数: {results['total_trades']}")
        print(f"胜率: {results['win_rate_pct']:.2f}%")
        print(f"总手续费: {results['total_commission']:.2f} 元")
        print(f"已实现盈亏: {results['realized_pnl']:.2f} 元")
        
        # 策略统计
        strategy_stats = results.get('strategy_stats', {})
        if strategy_stats:
            print(f"\n策略统计:")
            print(f"  总策略数: {strategy_stats.get('total_strategies', 0)}")
            print(f"  活跃策略数: {strategy_stats.get('active_strategies', 0)}")
        
        # 持仓统计
        portfolio_stats = results.get('portfolio_stats', {})
        if portfolio_stats:
            print(f"\n持仓统计:")
            print(f"  持仓数量: {portfolio_stats.get('position_count', 0)}")
            print(f"  持仓价值: {portfolio_stats.get('position_value', 0):,.2f} 元")
            print(f"  杠杆率: {portfolio_stats.get('leverage', 0):.2f}")
        
        print("=" * 60)