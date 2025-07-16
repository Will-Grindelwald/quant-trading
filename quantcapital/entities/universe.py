"""
股票池实体定义

管理股票池的组成和更新。
"""

from datetime import datetime
from dataclasses import dataclass, field
from typing import List, Set


@dataclass
class Universe:
    """股票池"""
    name: str                      # 股票池名称
    symbols: Set[str] = field(default_factory=set)  # 股票代码集合
    update_time: datetime = field(default_factory=datetime.now)  # 更新时间
    
    def add_symbol(self, symbol: str) -> None:
        """添加股票"""
        if symbol:
            self.symbols.add(symbol)
            self.update_time = datetime.now()
    
    def remove_symbol(self, symbol: str) -> None:
        """移除股票"""
        if symbol in self.symbols:
            self.symbols.remove(symbol)
            self.update_time = datetime.now()
    
    def contains(self, symbol: str) -> bool:
        """检查是否包含股票"""
        return symbol in self.symbols
    
    def get_symbols(self) -> List[str]:
        """获取股票列表"""
        return list(self.symbols)
    
    def size(self) -> int:
        """股票池大小"""
        return len(self.symbols)
    
    def update_symbols(self, new_symbols: List[str]) -> None:
        """批量更新股票池"""
        self.symbols = set(new_symbols)
        self.update_time = datetime.now()
    
    def __len__(self) -> int:
        """支持len()函数"""
        return len(self.symbols)
    
    def __contains__(self, symbol: str) -> bool:
        """支持in操作符"""
        return symbol in self.symbols
    
    def __str__(self) -> str:
        """字符串表示"""
        return f"Universe({self.name}: {len(self.symbols)}支股票)"