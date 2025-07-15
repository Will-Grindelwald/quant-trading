"""
Pytest配置文件

提供测试所需的fixtures和配置。
"""

import pytest
import tempfile
import shutil
from datetime import datetime, timedelta
from pathlib import Path
from quantcapital.config import ConfigManager
from quantcapital.entities import Account, StrategyInstance, StrategyType
from quantcapital.engine import EventEngine


@pytest.fixture
def temp_dir():
    """临时目录fixture"""
    temp_dir = tempfile.mkdtemp()
    yield temp_dir
    shutil.rmtree(temp_dir)


@pytest.fixture
def test_config(temp_dir):
    """测试配置fixture"""
    config_data = {
        "data_root": temp_dir,
        "business_db_path": f"{temp_dir}/business.db",
        "initial_capital": 1000000.0,
        "execution": {
            "slippage": 0.001,
            "commission_rate": 0.0003,
            "delay_seconds": 1
        },
        "portfolio": {
            "max_position_pct": 0.05,
            "max_total_position_pct": 0.95,
            "min_order_amount": 1000
        }
    }
    
    config = ConfigManager()
    config.config = config_data
    return config


@pytest.fixture
def test_account():
    """测试账户fixture"""
    return Account(
        account_id="test_account",
        initial_capital=1000000.0,
        cash=1000000.0
    )


@pytest.fixture
def event_engine():
    """事件引擎fixture"""
    engine = EventEngine()
    yield engine
    if engine.running:
        engine.stop()


@pytest.fixture
def sample_strategy_instance():
    """示例策略实例fixture"""
    return StrategyInstance(
        strategy_id="test_ma_cross",
        name="测试均线交叉策略",
        strategy_type=StrategyType.ENTRY,
        enabled=True,
        config={
            'short_window': 5,
            'long_window': 20,
            'stop_loss_pct': 0.05,
            'universe': ['000001.SZ', '000002.SZ']
        }
    )


@pytest.fixture
def sample_symbols():
    """示例股票列表fixture"""
    return ['000001.SZ', '000002.SZ', '600000.SH', '600036.SH']


@pytest.fixture
def sample_date_range():
    """示例日期范围fixture"""
    end_date = datetime.now()
    start_date = end_date - timedelta(days=30)
    return start_date, end_date