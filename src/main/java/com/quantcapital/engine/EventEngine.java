package com.quantcapital.engine;

import com.quantcapital.entities.Event;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 事件驱动引擎
 * 
 * 高性能事件分发系统，支持：
 * 1. 异步事件处理，避免阻塞
 * 2. 优先级队列，重要事件优先处理
 * 3. 虚拟线程，提高并发性能
 * 4. 故障隔离，单个处理器异常不影响整体
 * 5. 背压处理，防止内存溢出
 * 
 * @author QuantCapital Team
 */
@Component
@Slf4j
public class EventEngine {
    
    /** 事件队列容量 */
    @Value("${quantcapital.engine.queue-capacity:10000}")
    private int queueCapacity;
    
    /** 工作线程数 */
    @Value("${quantcapital.engine.worker-threads:4}")
    private int workerThreads;
    
    /** 批处理大小 */
    @Value("${quantcapital.engine.batch-size:100}")
    private int batchSize;
    
    /** 超时时间（毫秒） */
    @Value("${quantcapital.engine.timeout-ms:5000}")
    private long timeoutMs;
    
    /** 优先级事件队列 */
    private PriorityBlockingQueue<Event> eventQueue;
    
    /** 事件处理器映射 */
    private final Map<String, List<EventHandler>> handlers = new ConcurrentHashMap<>();
    
    /** 虚拟线程执行器 */
    private ExecutorService executorService;
    
    /** 事件分发线程 */
    private Thread dispatcherThread;
    
    /** 运行状态 */
    private final AtomicBoolean running = new AtomicBoolean(false);
    
    /** 统计信息 */
    private final AtomicLong totalEvents = new AtomicLong(0);
    private final AtomicLong processedEvents = new AtomicLong(0);
    private final AtomicLong failedEvents = new AtomicLong(0);
    private final AtomicLong droppedEvents = new AtomicLong(0);
    
    /** 性能监控 */
    private volatile long lastStatsTime = System.currentTimeMillis();
    private volatile double eventsPerSecond = 0.0;
    
    /**
     * 初始化事件引擎
     */
    public void initialize() {
        log.info("初始化事件引擎 - 队列容量:{} 工作线程:{} 批处理大小:{}", 
                queueCapacity, workerThreads, batchSize);
        
        // 创建优先级队列，按事件优先级排序
        this.eventQueue = new PriorityBlockingQueue<>(queueCapacity, 
                (e1, e2) -> Integer.compare(e1.getPriority(), e2.getPriority()));
        
        // 创建虚拟线程执行器
        this.executorService = Executors.newVirtualThreadPerTaskExecutor();
        
        log.info("事件引擎初始化完成");
    }
    
    /**
     * 启动事件引擎
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            log.info("启动事件引擎...");
            
            // 确保已初始化
            if (eventQueue == null) {
                initialize();
            }
            
            // 启动事件分发线程
            dispatcherThread = Thread.ofVirtual()
                    .name("EventDispatcher")
                    .start(this::dispatchLoop);
            
            // 启动性能监控线程
            executorService.submit(this::performanceMonitor);
            
            log.info("事件引擎启动完成");
        }
    }
    
    /**
     * 停止事件引擎
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            log.info("停止事件引擎...");
            
            // 停止分发线程
            if (dispatcherThread != null) {
                dispatcherThread.interrupt();
                try {
                    dispatcherThread.join(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            
            // 停止执行器
            if (executorService != null) {
                executorService.shutdown();
                try {
                    if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                        executorService.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    executorService.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
            
            log.info("事件引擎停止完成");
            printStatistics();
        }
    }
    
    /**
     * 发布事件
     * 
     * @param event 事件对象
     * @return 是否成功发布
     */
    public boolean publishEvent(Event event) {
        if (!running.get()) {
            log.warn("事件引擎未运行，事件被丢弃: {}", event);
            droppedEvents.incrementAndGet();
            return false;
        }
        
        if (event == null) {
            log.warn("事件为null，被忽略");
            return false;
        }
        
        // 检查队列容量，防止内存溢出
        if (eventQueue.size() >= queueCapacity * 0.9) { // 90%容量时开始丢弃低优先级事件
            if (event.getPriority() > 5) { // 优先级大于5的为低优先级
                log.warn("队列接近满载，丢弃低优先级事件: {}", event);
                droppedEvents.incrementAndGet();
                return false;
            }
        }
        
        try {
            boolean offered = eventQueue.offer(event);
            if (offered) {
                totalEvents.incrementAndGet();
                log.debug("事件已发布: {}", event);
            } else {
                log.warn("事件队列已满，事件被丢弃: {}", event);
                droppedEvents.incrementAndGet();
            }
            return offered;
        } catch (Exception e) {
            log.error("发布事件失败: {}", event, e);
            failedEvents.incrementAndGet();
            return false;
        }
    }
    
    /**
     * 注册事件处理器
     * 
     * @param eventType 事件类型
     * @param handler 事件处理器
     */
    public void registerHandler(String eventType, EventHandler handler) {
        handlers.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(handler);
        log.info("注册事件处理器: {} -> {}", eventType, handler.getName());
    }
    
    /**
     * 注销事件处理器
     * 
     * @param eventType 事件类型
     * @param handler 事件处理器
     */
    public void unregisterHandler(String eventType, EventHandler handler) {
        List<EventHandler> handlerList = handlers.get(eventType);
        if (handlerList != null) {
            handlerList.remove(handler);
            log.info("注销事件处理器: {} -> {}", eventType, handler.getName());
        }
    }
    
    /**
     * 事件分发主循环
     */
    private void dispatchLoop() {
        log.info("事件分发线程启动");
        
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                // 批量获取事件
                Event event = eventQueue.poll(100, TimeUnit.MILLISECONDS);
                if (event != null) {
                    dispatchEvent(event);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("事件分发异常", e);
                // 继续运行，不因为单个事件异常而停止
            }
        }
        
        log.info("事件分发线程停止");
    }
    
    /**
     * 分发单个事件
     * 
     * @param event 事件对象
     */
    private void dispatchEvent(Event event) {
        String eventType = event.getType().name();
        List<EventHandler> handlerList = handlers.get(eventType);
        
        if (handlerList == null || handlerList.isEmpty()) {
            log.debug("没有找到事件处理器: {}", eventType);
            return;
        }
        
        log.debug("分发事件: {} 给 {} 个处理器", event, handlerList.size());
        
        // 并行处理，使用虚拟线程
        for (EventHandler handler : handlerList) {
            executorService.submit(() -> {
                try {
                    long startTime = System.nanoTime();
                    handler.handleEvent(event);
                    long duration = System.nanoTime() - startTime;
                    
                    processedEvents.incrementAndGet();
                    
                    if (duration > timeoutMs * 1_000_000) { // 纳秒转换
                        log.warn("事件处理超时: {} 处理器:{} 耗时:{}ms", 
                                event, handler.getName(), duration / 1_000_000);
                    }
                    
                } catch (Exception e) {
                    log.error("事件处理异常: {} 处理器:{}", event, handler.getName(), e);
                    failedEvents.incrementAndGet();
                }
            });
        }
    }
    
    /**
     * 性能监控循环
     */
    private void performanceMonitor() {
        while (running.get()) {
            try {
                Thread.sleep(10000); // 每10秒统计一次
                
                long currentTime = System.currentTimeMillis();
                long timeElapsed = currentTime - lastStatsTime;
                long currentProcessed = processedEvents.get();
                
                if (timeElapsed > 0) {
                    eventsPerSecond = (double) currentProcessed * 1000 / timeElapsed;
                }
                
                log.debug("事件引擎性能: 处理速度:{:.2f}事件/秒 队列大小:{} 总事件:{} 已处理:{} 失败:{} 丢弃:{}", 
                        eventsPerSecond, eventQueue.size(), totalEvents.get(), 
                        processedEvents.get(), failedEvents.get(), droppedEvents.get());
                
                lastStatsTime = currentTime;
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    
    /**
     * 打印统计信息
     */
    private void printStatistics() {
        log.info("事件引擎统计信息:");
        log.info("  总事件数: {}", totalEvents.get());
        log.info("  已处理: {}", processedEvents.get());
        log.info("  失败: {}", failedEvents.get());
        log.info("  丢弃: {}", droppedEvents.get());
        log.info("  平均处理速度: {:.2f} 事件/秒", eventsPerSecond);
        log.info("  队列剩余: {}", eventQueue.size());
    }
    
    /**
     * 获取运行状态
     * 
     * @return 是否运行中
     */
    public boolean isRunning() {
        return running.get();
    }
    
    /**
     * 获取队列大小
     * 
     * @return 队列中待处理事件数量
     */
    public int getQueueSize() {
        return eventQueue != null ? eventQueue.size() : 0;
    }
    
    /**
     * 获取统计信息
     * 
     * @return 统计信息映射
     */
    public Map<String, Object> getStatistics() {
        return Map.of(
                "running", running.get(),
                "queueSize", getQueueSize(),
                "totalEvents", totalEvents.get(),
                "processedEvents", processedEvents.get(),
                "failedEvents", failedEvents.get(),
                "droppedEvents", droppedEvents.get(),
                "eventsPerSecond", eventsPerSecond
        );
    }
}