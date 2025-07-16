package com.quantcapital.engine;

import com.quantcapital.entities.Event;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 高性能事件驱动引擎
 * <p>
 * 架构特点：
 * 1. EventEngine维护主事件队列，负责快速分发
 * 2. 每个订阅者拥有独立的事件队列和处理线程
 * 3. 分发过程不等待订阅者处理，避免阻塞
 * 4. 同一个事件的多个订阅者不会出现等待，因为每个订阅者拥有独立的事件队列
 * 5. 支持背压处理和故障隔离
 *
 * @author QuantCapital Team
 */
@Component
@Slf4j
public class EventEngine {

    // 配置参数
    @Value("${quantcapital.engine.queue-capacity:10000}")
    private int queueCapacity;

    @Value("${quantcapital.engine.worker-threads:4}")
    private int workerThreads;

    @Value("${quantcapital.engine.timeout-ms:5000}")
    private long timeoutMs;

    // 主事件队列 - 用于快速分发
    private PriorityBlockingQueue<Event> mainEventQueue;

    // 事件订阅者映射 - 每个事件类型对应多个订阅者
    private final Map<String, List<EventSubscriber>> subscribers = new ConcurrentHashMap<>();

    // 事件分发线程
    private Thread dispatcherThread;

    // 运行状态
    private final AtomicBoolean running = new AtomicBoolean(false);

    // 统计信息
    private final AtomicLong totalEvents = new AtomicLong(0);
    private final AtomicLong dispatchedEvents = new AtomicLong(0);
    private final AtomicLong droppedEvents = new AtomicLong(0);

    // 性能监控
    private volatile long lastStatsTime = System.currentTimeMillis();
    private volatile double eventsPerSecond = 0.0;

    /**
     * 事件订阅者 - 每个订阅者拥有独立的队列和处理线程
     */
    private static class EventSubscriber {
        private final String name;
        private final EventHandler handler;
        private final BlockingQueue<Event> eventQueue;
        private final Thread processingThread;
        private final AtomicBoolean active = new AtomicBoolean(true);
        private final AtomicLong processedCount = new AtomicLong(0);
        private final AtomicLong failedCount = new AtomicLong(0);

        public EventSubscriber(String name, EventHandler handler, int queueCapacity) {
            this.name = name;
            this.handler = handler;
            this.eventQueue = new LinkedBlockingQueue<>(queueCapacity);
            
            // 创建独立的处理线程
            this.processingThread = Thread.ofVirtual()
                    .name("EventSubscriber-" + name)
                    .start(this::processEvents);
            
            log.info("创建事件订阅者: {}", name);
        }

        public boolean offerEvent(Event event) {
            if (!active.get()) {
                return false;
            }
            
            boolean offered = eventQueue.offer(event);
            if (!offered) {
                log.warn("订阅者 {} 队列已满，丢弃事件: {}", name, event);
            }
            return offered;
        }

        private void processEvents() {
            log.info("订阅者 {} 处理线程启动", name);
            
            while (active.get() || !eventQueue.isEmpty()) {
                try {
                    Event event = eventQueue.poll(100, TimeUnit.MILLISECONDS);
                    if (event != null) {
                        processEvent(event);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("订阅者 {} 处理事件异常", name, e);
                }
            }
            
            log.info("订阅者 {} 处理线程停止", name);
        }

        private void processEvent(Event event) {
            try {
                long startTime = System.nanoTime();
                handler.handleEvent(event);
                long duration = System.nanoTime() - startTime;
                
                processedCount.incrementAndGet();
                
                if (duration > 5_000_000_000L) { // 5秒超时警告
                    log.warn("订阅者 {} 处理事件超时: {}ms", name, duration / 1_000_000);
                }
                
            } catch (Exception e) {
                log.error("订阅者 {} 处理事件失败: {}", name, event, e);
                failedCount.incrementAndGet();
            }
        }

        public void shutdown() {
            active.set(false);
            if (processingThread != null && processingThread.isAlive()) {
                processingThread.interrupt();
                try {
                    processingThread.join(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        public Map<String, Object> getStatistics() {
            return Map.of(
                    "name", name,
                    "active", active.get(),
                    "queueSize", eventQueue.size(),
                    "processedCount", processedCount.get(),
                    "failedCount", failedCount.get()
            );
        }
    }

    /**
     * 初始化事件引擎
     */
    public void initialize() {
        log.info("初始化高性能事件引擎 - 队列容量:{}", queueCapacity);

        // 创建主事件队列
        this.mainEventQueue = new PriorityBlockingQueue<>(queueCapacity,
                (e1, e2) -> Integer.compare(e1.getPriority(), e2.getPriority()));

        log.info("事件引擎初始化完成");
    }

    /**
     * 启动事件引擎
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            log.info("启动事件引擎...");

            // 确保已初始化
            if (mainEventQueue == null) {
                initialize();
            }

            // 启动事件分发线程
            dispatcherThread = Thread.ofVirtual()
                    .name("EventDispatcher")
                    .start(this::dispatchLoop);

            // 启动性能监控
            CompletableFuture.runAsync(this::performanceMonitor);

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

            // 停止所有订阅者
            subscribers.values().stream()
                    .flatMap(List::stream)
                    .forEach(EventSubscriber::shutdown);

            log.info("事件引擎停止完成");
            printStatistics();
        }
    }

    /**
     * 发布事件 - 快速入队，不阻塞
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

        // 检查队列容量
        if (mainEventQueue.size() >= queueCapacity * 0.9) {
            if (event.getPriority() > 5) {
                log.warn("主队列接近满载，丢弃低优先级事件: {}", event);
                droppedEvents.incrementAndGet();
                return false;
            }
        }

        try {
            boolean offered = mainEventQueue.offer(event);
            if (offered) {
                totalEvents.incrementAndGet();
                log.debug("事件已发布到主队列: {}", event);
            } else {
                log.warn("主队列已满，事件被丢弃: {}", event);
                droppedEvents.incrementAndGet();
            }
            return offered;
        } catch (Exception e) {
            log.error("发布事件失败: {}", event, e);
            droppedEvents.incrementAndGet();
            return false;
        }
    }

    /**
     * 注册事件处理器 - 为每个处理器创建独立的订阅者
     *
     * @param eventType 事件类型
     * @param handler   事件处理器
     */
    public void registerHandler(String eventType, EventHandler handler) {
        if (handler == null || eventType == null) {
            log.warn("无法注册空的事件类型或处理器");
            return;
        }

        // 初始化处理器
        try {
            handler.initialize();
        } catch (Exception e) {
            log.error("初始化事件处理器失败: {}", handler.getName(), e);
            return;
        }

        // 创建独立的订阅者
        EventSubscriber subscriber = new EventSubscriber(
                eventType + "-" + handler.getName(),
                handler,
                queueCapacity / 10 // 每个订阅者的队列容量为主队列的1/10
        );

        subscribers.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>())
                .add(subscriber);

        log.info("注册事件处理器: {} -> {}", eventType, handler.getName());
    }

    /**
     * 注销事件处理器
     *
     * @param eventType 事件类型
     * @param handler   事件处理器
     */
    public void unregisterHandler(String eventType, EventHandler handler) {
        List<EventSubscriber> subscriberList = subscribers.get(eventType);
        if (subscriberList != null) {
            subscriberList.removeIf(subscriber -> {
                if (subscriber.handler.equals(handler)) {
                    subscriber.shutdown();
                    try {
                        handler.destroy();
                    } catch (Exception e) {
                        log.error("销毁事件处理器失败: {}", handler.getName(), e);
                    }
                    log.info("注销事件处理器: {} -> {}", eventType, handler.getName());
                    return true;
                }
                return false;
            });
        }
    }

    /**
     * 事件分发主循环 - 从主队列分发到各订阅者队列
     */
    private void dispatchLoop() {
        log.info("事件分发线程启动");

        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                Event event = mainEventQueue.poll(100, TimeUnit.MILLISECONDS);
                if (event != null) {
                    dispatchEvent(event);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("事件分发异常", e);
            }
        }

        log.info("事件分发线程停止");
    }

    /**
     * 分发单个事件到所有相关订阅者 - 非阻塞分发
     *
     * @param event 事件对象
     */
    private void dispatchEvent(Event event) {
        String eventType = event.getType().name();
        List<EventSubscriber> subscriberList = subscribers.get(eventType);

        if (subscriberList == null || subscriberList.isEmpty()) {
            log.debug("没有找到事件订阅者: {}", eventType);
            return;
        }

        log.debug("分发事件: {} 给 {} 个订阅者", event, subscriberList.size());

        // 并行分发到所有订阅者的独立队列
        int successCount = 0;
        for (EventSubscriber subscriber : subscriberList) {
            if (subscriber.offerEvent(event)) {
                successCount++;
            }
        }

        dispatchedEvents.incrementAndGet();
        
        if (successCount < subscriberList.size()) {
            log.warn("事件 {} 只成功分发给 {}/{} 个订阅者", 
                    event.getEventId(), successCount, subscriberList.size());
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
                long currentDispatched = dispatchedEvents.get();

                if (timeElapsed > 0) {
                    eventsPerSecond = (double) currentDispatched * 1000 / timeElapsed;
                }

                log.debug("事件引擎性能: 分发速度:{:.2f}事件/秒 主队列大小:{} 总事件:{} 已分发:{} 丢弃:{}",
                        eventsPerSecond, mainEventQueue.size(), totalEvents.get(), 
                        dispatchedEvents.get(), droppedEvents.get());

                lastStatsTime = currentTime;

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("性能监控异常", e);
            }
        }
    }

    /**
     * 打印统计信息
     */
    private void printStatistics() {
        log.info("事件引擎统计信息:");
        log.info("  总事件数: {}", totalEvents.get());
        log.info("  已分发: {}", dispatchedEvents.get());
        log.info("  丢弃: {}", droppedEvents.get());
        log.info("  平均分发速度: {:.2f} 事件/秒", eventsPerSecond);
        log.info("  主队列剩余: {}", mainEventQueue.size());

        // 打印各订阅者统计信息
        subscribers.forEach((eventType, subscriberList) -> {
            log.info("  事件类型 {} 订阅者数量: {}", eventType, subscriberList.size());
            subscriberList.forEach(subscriber -> {
                Map<String, Object> stats = subscriber.getStatistics();
                log.info("    订阅者: {} 队列:{} 已处理:{} 失败:{}",
                        stats.get("name"), stats.get("queueSize"),
                        stats.get("processedCount"), stats.get("failedCount"));
            });
        });
    }

    /**
     * 获取运行状态
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * 获取主队列大小
     */
    public int getMainQueueSize() {
        return mainEventQueue != null ? mainEventQueue.size() : 0;
    }

    /**
     * 获取统计信息
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new ConcurrentHashMap<>();
        stats.put("running", running.get());
        stats.put("mainQueueSize", getMainQueueSize());
        stats.put("totalEvents", totalEvents.get());
        stats.put("dispatchedEvents", dispatchedEvents.get());
        stats.put("droppedEvents", droppedEvents.get());
        stats.put("eventsPerSecond", eventsPerSecond);

        // 添加订阅者统计信息
        Map<String, Object> subscriberStats = new ConcurrentHashMap<>();
        subscribers.forEach((eventType, subscriberList) -> {
            subscriberStats.put(eventType, subscriberList.stream()
                    .map(EventSubscriber::getStatistics)
                    .toList());
        });
        stats.put("subscribers", subscriberStats);

        return stats;
    }
}