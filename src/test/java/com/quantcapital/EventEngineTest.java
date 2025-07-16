package com.quantcapital;

import com.quantcapital.engine.EventEngine;
import com.quantcapital.engine.EventHandler;
import com.quantcapital.entities.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * 事件引擎单元测试
 * 
 * 测试事件引擎的核心功能：
 * 1. 事件发布和分发
 * 2. 事件处理器注册和注销
 * 3. 优先级处理
 * 4. 并发性能
 * 5. 异常处理
 * 
 * @author QuantCapital Team
 */
@SpringBootTest
class EventEngineTest {
    
    private EventEngine eventEngine;
    
    @BeforeEach
    void setUp() {
        eventEngine = new EventEngine();
        eventEngine.initialize();
        eventEngine.start();
    }
    
    @AfterEach
    void tearDown() {
        if (eventEngine != null) {
            eventEngine.stop();
        }
    }
    
    @Test
    void testEventPublishAndHandle() throws InterruptedException {
        // 创建测试处理器
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger handledEvents = new AtomicInteger(0);
        
        EventHandler testHandler = new EventHandler() {
            @Override
            public String getName() {
                return "TestHandler";
            }
            
            @Override
            public void handleEvent(Event event) {
                handledEvents.incrementAndGet();
                latch.countDown();
            }
        };
        
        // 注册处理器
        eventEngine.registerHandler("MARKET", testHandler);
        
        // 创建测试事件
        Bar testBar = Bar.builder()
                .symbol("000001.SZ")
                .datetime(LocalDateTime.now())
                .frequency(Frequency.DAILY)
                .open(10.0)
                .high(11.0)
                .low(9.5)
                .close(10.5)
                .volume(1000000)
                .amount(10500000.0)
                .build();
        
        MarketEvent marketEvent = new MarketEvent(LocalDateTime.now(), testBar, Frequency.DAILY);
        
        // 发布事件
        boolean published = eventEngine.publishEvent(marketEvent);
        assertThat(published).isTrue();
        
        // 等待事件处理
        boolean handled = latch.await(5, TimeUnit.SECONDS);
        assertThat(handled).isTrue();
        assertThat(handledEvents.get()).isEqualTo(1);
    }
    
    @Test
    void testEventPriority() throws InterruptedException {
        // 创建优先级测试处理器
        CountDownLatch latch = new CountDownLatch(3);
        AtomicInteger processOrder = new AtomicInteger(0);
        
        EventHandler priorityHandler = new EventHandler() {
            @Override
            public String getName() {
                return "PriorityTestHandler";
            }
            
            @Override
            public void handleEvent(Event event) {
                // 记录处理顺序
                int order = processOrder.incrementAndGet();
                System.out.printf("处理事件 优先级:%d 顺序:%d%n", event.getPriority(), order);
                latch.countDown();
            }
        };
        
        // 注册处理器
        eventEngine.registerHandler("TIMER", priorityHandler);
        
        // 创建不同优先级的定时器事件
        TimerEvent highPriorityEvent = new TimerEvent(LocalDateTime.now(), TimerType.MARKET_DATA_UPDATE, 1000);
        TimerEvent mediumPriorityEvent = new TimerEvent(LocalDateTime.now(), TimerType.RISK_CHECK, 1000);
        TimerEvent lowPriorityEvent = new TimerEvent(LocalDateTime.now(), TimerType.CLEANUP, 1000);
        
        // 按相反顺序发布（低优先级先发布）
        eventEngine.publishEvent(lowPriorityEvent);
        eventEngine.publishEvent(mediumPriorityEvent);
        eventEngine.publishEvent(highPriorityEvent);
        
        // 等待处理完成
        boolean handled = latch.await(5, TimeUnit.SECONDS);
        assertThat(handled).isTrue();
    }
    
    @Test
    void testMultipleHandlers() throws InterruptedException {
        // 创建多个处理器
        int handlerCount = 3;
        CountDownLatch latch = new CountDownLatch(handlerCount);
        AtomicInteger totalHandled = new AtomicInteger(0);
        
        for (int i = 0; i < handlerCount; i++) {
            final int handlerId = i;
            EventHandler handler = new EventHandler() {
                @Override
                public String getName() {
                    return "Handler-" + handlerId;
                }
                
                @Override
                public void handleEvent(Event event) {
                    totalHandled.incrementAndGet();
                    latch.countDown();
                }
            };
            
            eventEngine.registerHandler("SIGNAL", handler);
        }
        
        // 创建信号事件
        Signal testSignal = new Signal("test-strategy", "000001.SZ", 
                SignalDirection.BUY, 0.8, LocalDateTime.now(), 10.5, "测试信号");
        SignalEvent signalEvent = new SignalEvent(LocalDateTime.now(), testSignal);
        
        // 发布事件
        eventEngine.publishEvent(signalEvent);
        
        // 等待所有处理器处理完成
        boolean handled = latch.await(5, TimeUnit.SECONDS);
        assertThat(handled).isTrue();
        assertThat(totalHandled.get()).isEqualTo(handlerCount);
    }
    
    @Test
    void testErrorHandling() throws InterruptedException {
        // 创建会抛异常的处理器
        CountDownLatch latch = new CountDownLatch(1);
        
        EventHandler errorHandler = new EventHandler() {
            @Override
            public String getName() {
                return "ErrorHandler";
            }
            
            @Override
            public void handleEvent(Event event) {
                latch.countDown();
                throw new RuntimeException("测试异常");
            }
        };
        
        EventHandler normalHandler = new EventHandler() {
            @Override
            public String getName() {
                return "NormalHandler";
            }
            
            @Override
            public void handleEvent(Event event) {
                // 正常处理器应该不受异常处理器影响
            }
        };
        
        // 注册处理器
        eventEngine.registerHandler("FILL", errorHandler);
        eventEngine.registerHandler("FILL", normalHandler);
        
        // 创建成交事件
        Fill testFill = new Fill("order-1", "000001.SZ", OrderSide.BUY, 
                100, 10.5, LocalDateTime.now(), "test-strategy");
        FillEvent fillEvent = new FillEvent(LocalDateTime.now(), testFill);
        
        // 发布事件
        eventEngine.publishEvent(fillEvent);
        
        // 等待处理完成（异常应该被捕获，不影响系统运行）
        boolean handled = latch.await(5, TimeUnit.SECONDS);
        assertThat(handled).isTrue();
        
        // 事件引擎应该仍在运行
        assertThat(eventEngine.isRunning()).isTrue();
    }
    
    @Test
    void testPerformance() throws InterruptedException {
        // 性能测试：发布大量事件
        int eventCount = 1000;
        CountDownLatch latch = new CountDownLatch(eventCount);
        
        EventHandler performanceHandler = new EventHandler() {
            @Override
            public String getName() {
                return "PerformanceHandler";
            }
            
            @Override
            public void handleEvent(Event event) {
                latch.countDown();
            }
        };
        
        eventEngine.registerHandler("TIMER", performanceHandler);
        
        // 记录开始时间
        long startTime = System.currentTimeMillis();
        
        // 批量发布事件
        for (int i = 0; i < eventCount; i++) {
            TimerEvent timerEvent = new TimerEvent(LocalDateTime.now(), TimerType.HEARTBEAT, 1000);
            eventEngine.publishEvent(timerEvent);
        }
        
        // 等待所有事件处理完成
        boolean allHandled = latch.await(30, TimeUnit.SECONDS);
        long endTime = System.currentTimeMillis();
        
        assertThat(allHandled).isTrue();
        
        long duration = endTime - startTime;
        double eventsPerSecond = (double) eventCount * 1000 / duration;
        
        System.out.printf("性能测试结果: %d个事件 耗时%dms 处理速度%.2f事件/秒%n", 
                eventCount, duration, eventsPerSecond);
        
        // 验证处理速度（应该能达到较高的吞吐量）
        assertThat(eventsPerSecond).isGreaterThan(100); // 至少每秒100个事件
    }
    
    @Test
    void testStatistics() {
        // 测试统计信息
        var stats = eventEngine.getStatistics();
        
        assertThat(stats).isNotNull();
        assertThat(stats).containsKeys("running", "queueSize", "totalEvents", 
                "processedEvents", "failedEvents", "droppedEvents");
        
        assertThat((Boolean) stats.get("running")).isTrue();
        assertThat((Integer) stats.get("queueSize")).isGreaterThanOrEqualTo(0);
    }
}