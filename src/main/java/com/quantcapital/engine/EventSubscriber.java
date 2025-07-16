/**
 * @(#)EventSubscriber.java, 7月 16, 2025.
 * <p>
 * Copyright 2025 yuanfudao.com. All rights reserved.
 * FENBI.COM PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */
package com.quantcapital.engine;

import com.quantcapital.entities.event.Event;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 事件订阅者 - 每个订阅者拥有独立的队列和处理线程
 * @author lijiechengbj
 */
@Slf4j
public class EventSubscriber {
    private final String name;
    @Getter
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