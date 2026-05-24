package com.lubover.singularity.order.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.lubover.singularity.api.*;
import com.lubover.singularity.api.impl.DefaultAllocator;
import com.lubover.singularity.order.dto.OrderMessage;
import com.lubover.singularity.order.entity.Order;
import com.lubover.singularity.order.feign.UserClient;
import com.lubover.singularity.order.mapper.OrderMapper;
import com.lubover.singularity.order.registry.SlotRegistry;
import com.lubover.singularity.order.service.OrderService;
import com.lubover.singularity.order.slot.StockSlot;
import com.lubover.singularity.order.tx.OrderLocalTransaction;
import org.apache.rocketmq.client.impl.producer.DefaultMQProducerImpl;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.LocalTransactionState;
import org.apache.rocketmq.client.producer.TransactionListener;
import org.apache.rocketmq.client.producer.TransactionSendResult;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Service
public class OrderServiceImpl implements OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);
    private static final BigDecimal PRODUCT_PRICE = BigDecimal.valueOf(99);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private final Allocator allocator;
    private final OrderMapper orderMapper;
    private final UserClient userClient;
    private final SlotRegistry slotRegistry;
    private final StringRedisTemplate redisTemplate;
    private final DefaultMQProducerImpl producerImpl;
    private final ExecutorService executor = new ThreadPoolExecutor(
            20, 50, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(10000),
            new ThreadPoolExecutor.CallerRunsPolicy());

    /**
     * 原生 RocketMQ TransactionListener。
     * {@code executeLocalTransaction} 永远返回 UNKNOW —— 主线程显式 commit/rollback。
     * {@code checkLocalTransaction} 查 Redis —— 进程崩溃时 broker 回查兜底。
     */
    private final TransactionListener transactionListener = new TransactionListener() {
        @Override
        public LocalTransactionState executeLocalTransaction(Message msg, Object arg) {
            return LocalTransactionState.UNKNOW;
        }

        @Override
        public LocalTransactionState checkLocalTransaction(MessageExt msg) {
            String orderId = msg.getProperty("orderId");
            if (orderId == null) {
                return LocalTransactionState.ROLLBACK_MESSAGE;
            }
            Boolean exists = redisTemplate.hasKey("order:" + orderId);
            LocalTransactionState state = Boolean.TRUE.equals(exists)
                    ? LocalTransactionState.COMMIT_MESSAGE
                    : LocalTransactionState.ROLLBACK_MESSAGE;
            log.info("checkLocalTransaction: orderId={} -> {}", orderId, state);
            return state;
        }
    };

    @Autowired
    public OrderServiceImpl(
            Registry registry,
            ShardPolicy shardPolicy,
            List<Interceptor> interceptors,
            RocketMQTemplate rocketMQTemplate,
            StringRedisTemplate redisTemplate,
            SlotRegistry slotRegistry,
            OrderMapper orderMapper,
            UserClient userClient) {
        DefaultMQProducer producer = (DefaultMQProducer) rocketMQTemplate.getProducer();
        this.producerImpl = producer.getDefaultMQProducerImpl();

        this.allocator = new DefaultAllocator(
                registry,
                shardPolicy,
                interceptors != null ? interceptors : Collections.emptyList(),
                handler());
        this.orderMapper = orderMapper;
        this.userClient = userClient;
        this.slotRegistry = slotRegistry;
        this.redisTemplate = redisTemplate;
    }

    // ---- async API ----

    @Override
    public CompletableFuture<Result> snagOrder(Actor actor) {
        return CompletableFuture.supplyAsync(() -> allocator.allocate(actor), executor);
    }

    @Override
    public CompletableFuture<Result> snagOrderByProduct(Actor actor, String productId) {
        return CompletableFuture.supplyAsync(() -> {
            StockSlot targetSlot = null;
            for (StockSlot slot : slotRegistry.getAllSlots()) {
                if (productId.equals(slot.getProductId())) {
                    if (!Boolean.TRUE.equals(slotRegistry.getEmptyStatus(slot.getId()))) {
                        targetSlot = slot;
                        break;
                    }
                }
            }
            if (targetSlot == null) {
                return new Result(false, "商品库存不足或不存在");
            }

            String orderId = UUID.randomUUID().toString();
            String redisStockKey = targetSlot.getRedisStockKey();
            LocalDateTime createTime = LocalDateTime.now();

            OrderLocalTransaction localTx = new OrderLocalTransaction(
                    orderId, actor.getId(), targetSlot.getId(),
                    productId, redisStockKey, redisTemplate, slotRegistry, createTime);

            OrderMessage orderMessage = new OrderMessage();
            orderMessage.setOrderId(orderId);
            orderMessage.setProductId(productId);
            orderMessage.setUserId(actor.getId());
            orderMessage.setSlotId(targetSlot.getId());
            orderMessage.setCreateTime(createTime);

            return executeTransaction(orderId, orderMessage, localTx);
        }, executor);
    }

    // ---- sync API ----

    @Override
    public Result payOrder(String orderId, String userId) {
        Order order = orderMapper.selectByOrderId(orderId);
        if (order == null) {
            return new Result(false, "订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            return new Result(false, "无权操作此订单");
        }
        if (!"CREATED".equals(order.getStatus())) {
            return new Result(false, "订单状态不支持支付");
        }

        try {
            Long uid = Long.parseLong(userId);
            Map<String, BigDecimal> body = new HashMap<>();
            body.put("amount", PRODUCT_PRICE);
            Map<String, Object> deductResult = userClient.deductBalance(uid, body);
            if (!Boolean.TRUE.equals(deductResult.get("success"))) {
                String msg = deductResult.get("message") != null
                        ? String.valueOf(deductResult.get("message"))
                        : "余额不足";
                return new Result(false, msg);
            }
        } catch (NumberFormatException e) {
            return new Result(false, "userId 格式非法");
        } catch (Exception e) {
            return new Result(false, "扣款失败: " + e.getMessage());
        }

        orderMapper.updateStatus(orderId, "PAID");
        return new Result(true, orderId);
    }

    // ---- transaction ----

    /**
     * 三步显式事务（运行在 worker 线程上）：
     * <ol>
     * <li>发送半消息（listener 返回 UNKNOW）</li>
     * <li>执行 Redis Lua（原子减库存 + 写订单）</li>
     * <li>endTransaction commit 或 rollback</li>
     * </ol>
     */
    private Result executeTransaction(String orderId, OrderMessage orderMessage,
                                      OrderLocalTransaction localTx) {
        Message nativeMsg;
        try {
            byte[] body = OBJECT_MAPPER.writeValueAsBytes(orderMessage);
            nativeMsg = new Message("order-topic", body);
            nativeMsg.putUserProperty("orderId", orderId);
        } catch (Exception e) {
            log.error("序列化 OrderMessage 失败: orderId={}", orderId, e);
            return new Result(false, "序列化失败");
        }

        // Step 1: 发送半消息
        TransactionSendResult sendResult;
        try {
            sendResult = producerImpl.sendMessageInTransaction(
                    nativeMsg, transactionListener, null);
        } catch (Exception e) {
            log.error("发送半消息失败: orderId={}", orderId, e);
            return new Result(false, "发送半消息失败");
        }

        // Step 2: 执行本地事务（Redis Lua）
        boolean ok = localTx.execute();

        // Step 3: commit 或 rollback
        LocalTransactionState state = ok
                ? LocalTransactionState.COMMIT_MESSAGE
                : LocalTransactionState.ROLLBACK_MESSAGE;
        try {
            producerImpl.endTransaction(nativeMsg, sendResult, state, null);
        } catch (Exception e) {
            log.error("endTransaction 失败: orderId={} state={}", orderId, state, e);
        }

        return ok
                ? new Result(true, orderId)
                : new Result(false, "库存不足，抢单失败");
    }

    /**
     * Allocator 的 handler 拦截器。在 worker 线程上被调用。
     */
    private Interceptor handler() {
        return context -> {
            Actor actor = context.getCurrActor();
            Slot slot = context.getCurrSlot();
            String orderId = UUID.randomUUID().toString();
            String redisStockKey = (String) slot.getMetadata().get("redisStockKey");
            String productId = (String) slot.getMetadata().get("productId");
            if (productId == null || productId.isBlank()) {
                context.setResult(new Result(false, "slot productId missing for slot: " + slot.getId()));
                return;
            }
            LocalDateTime createTime = LocalDateTime.now();

            OrderLocalTransaction localTx = new OrderLocalTransaction(
                    orderId, actor.getId(), slot.getId(),
                    productId, redisStockKey, redisTemplate, slotRegistry, createTime);

            OrderMessage orderMessage = new OrderMessage();
            orderMessage.setOrderId(orderId);
            orderMessage.setProductId(productId);
            orderMessage.setUserId(actor.getId());
            orderMessage.setSlotId(slot.getId());
            orderMessage.setCreateTime(createTime);

            Object riskObj = context.getValue("fraud.riskScore");
            if (riskObj instanceof Double risk) {
                orderMessage.setRiskScore(risk);
            }

            Result result = executeTransaction(orderId, orderMessage, localTx);
            context.setResult(result);
        };
    }
}
