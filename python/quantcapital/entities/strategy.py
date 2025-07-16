"""
策略实例实体定义

描述策略的基本信息、配置和类型。
"""

from dataclasses import dataclass
from enum import Enum
from typing import Dict, Any


class StrategyType(Enum):
    """策略类型枚举"""
    ENTRY = "entry"                # 开单策略
    EXIT = "exit"                  # 止盈止损策略
    UNIVERSAL_STOP = "stop"        # 通用强制止损


@dataclass
class StrategyInstance:
    """策略实例"""
    strategy_id: str               # 策略ID
    name: str                      # 策略名称
    strategy_type: StrategyType    # 策略类型
    config: Dict[str, Any]         # 策略配置
    enabled: bool = True           # 是否启用
    
    def __post_init__(self):
        """验证策略配置"""
        if not self.strategy_id:
            raise ValueError("策略ID不能为空")
        if not self.name:
            raise ValueError("策略名称不能为空")
    
    def get_config(self, key: str, default=None):
        """获取配置值"""
        return self.config.get(key, default)
    
    def set_config(self, key: str, value: Any) -> None:
        """设置配置值"""
        self.config[key] = value
    
    def is_entry_strategy(self) -> bool:
        """是否为开单策略"""
        return self.strategy_type == StrategyType.ENTRY
    
    def is_exit_strategy(self) -> bool:
        """是否为止盈止损策略"""
        return self.strategy_type == StrategyType.EXIT
    
    def is_stop_strategy(self) -> bool:
        """是否为强制止损策略"""
        return self.strategy_type == StrategyType.UNIVERSAL_STOP
    
    def __str__(self) -> str:
        """字符串表示"""
        status = "启用" if self.enabled else "禁用"
        return f"Strategy({self.name}[{self.strategy_type.value}] {status})"