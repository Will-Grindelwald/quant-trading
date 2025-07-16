"""
配置管理器

统一管理系统配置，支持多环境配置和动态参数调整。
"""

import json
import logging
from pathlib import Path
from typing import Any, Dict, Optional


logger = logging.getLogger(__name__)


class ConfigManager:
    """配置管理器"""
    
    def __init__(self, config_path: Optional[str] = None, env: str = 'backtest'):
        """
        初始化配置管理器
        
        Args:
            config_path: 配置文件路径
            env: 环境名称 ('backtest', 'live_trading')
        """
        self.env = env
        self.config_path = config_path
        self._config: Dict[str, Any] = {}
        
        # 默认配置
        self._default_config = {
            'backtest': {
                'data_root': './.data',
                'business_db_path': './.data/business.db',
                'initial_capital': 1000000.0,
                'execution': {
                    'slippage': 0.001,
                    'commission_rate': 0.0003,
                    'min_commission': 5.0
                },
                'logging': {
                    'level': 'INFO',
                    'format': '%(asctime)s - %(name)s - %(levelname)s - %(message)s'
                }
            },
            'live_trading': {
                'data_root': './.data',
                'business_db_path': './.data/business.db',
                'initial_capital': 1000000.0,
                'execution': {
                    'max_order_value': 1000000,
                    'max_daily_orders': 100
                },
                'trading_account': {
                    'account': '',
                    'password': ''
                },
                'logging': {
                    'level': 'INFO',
                    'format': '%(asctime)s - %(name)s - %(levelname)s - %(message)s'
                }
            }
        }
        
        self.load_config()
    
    def load_config(self):
        """加载配置"""
        # 使用默认配置
        self._config = self._default_config.get(self.env, {}).copy()
        
        # 如果指定了配置文件，加载并合并
        if self.config_path and Path(self.config_path).exists():
            try:
                with open(self.config_path, 'r', encoding='utf-8') as f:
                    file_config = json.load(f)
                    env_config = file_config.get(self.env, {})
                    self._merge_config(self._config, env_config)
                logger.info(f"配置文件加载成功: {self.config_path}")
            except Exception as e:
                logger.error(f"配置文件加载失败: {e}")
        
        logger.info(f"配置管理器初始化完成, 环境: {self.env}")
    
    def _merge_config(self, base: Dict, update: Dict):
        """递归合并配置"""
        for key, value in update.items():
            if key in base and isinstance(base[key], dict) and isinstance(value, dict):
                self._merge_config(base[key], value)
            else:
                base[key] = value
    
    def get(self, key: str, default=None) -> Any:
        """获取配置值（支持点分隔的嵌套键）"""
        keys = key.split('.')
        value = self._config
        
        for k in keys:
            if isinstance(value, dict) and k in value:
                value = value[k]
            else:
                return default
        
        return value
    
    def set(self, key: str, value: Any):
        """设置配置值（支持点分隔的嵌套键）"""
        keys = key.split('.')
        config = self._config
        
        for k in keys[:-1]:
            if k not in config:
                config[k] = {}
            config = config[k]
        
        config[keys[-1]] = value
    
    def get_all(self) -> Dict[str, Any]:
        """获取所有配置"""
        return self._config.copy()
    
    def save_config(self, file_path: str):
        """保存配置到文件"""
        try:
            config_data = {self.env: self._config}
            
            # 如果文件已存在，加载其他环境的配置
            if Path(file_path).exists():
                with open(file_path, 'r', encoding='utf-8') as f:
                    existing_config = json.load(f)
                    existing_config[self.env] = self._config
                    config_data = existing_config
            
            with open(file_path, 'w', encoding='utf-8') as f:
                json.dump(config_data, f, indent=2, ensure_ascii=False)
            
            logger.info(f"配置已保存到: {file_path}")
            
        except Exception as e:
            logger.error(f"保存配置失败: {e}")
    
    def validate_config(self) -> bool:
        """验证配置有效性"""
        try:
            # 检查必需的配置项
            required_keys = ['data_root', 'business_db_path', 'initial_capital']
            
            for key in required_keys:
                if self.get(key) is None:
                    logger.error(f"缺少必需的配置项: {key}")
                    return False
            
            # 检查数据类型
            if not isinstance(self.get('initial_capital'), (int, float)):
                logger.error("initial_capital 必须是数字")
                return False
            
            if self.get('initial_capital') <= 0:
                logger.error("initial_capital 必须大于0")
                return False
            
            logger.info("配置验证通过")
            return True
            
        except Exception as e:
            logger.error(f"配置验证失败: {e}")
            return False