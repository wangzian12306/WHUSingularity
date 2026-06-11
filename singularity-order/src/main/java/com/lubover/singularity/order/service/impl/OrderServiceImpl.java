package com.lubover.singularity.order.service.impl;

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

import org.apache.rocketmq.client.impl.producer.DefaultMQProducerImpl;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.LocalTransactionState;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageAccessor;
import org.apache.rocketmq.common.message.MessageConst;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.lubover.singularity.api.Actor;
import com.lubover.singularity.api.Allocator;
import com.lubover.singularity.api.Interceptor;
import com.lubover.singularity.api.Registry;
import com.lubover.singularity.api.Result;
import com.lubover.singularity.api.ShardPolicy;
import com.lubover.singularity.api.Slot;
import com.lubover.singularity.api.impl.DefaultAllocator;
import com.lubover.singularity.order.dto.OrderMessage;
import com.lubover.singularity.order.entity.Order;
import com.lubover.singularity.order.feign.MerchantClient;
import com.lubover.singularity.order.feign.UserClient;
import com.lubover.singularity.order.mapper.OrderMapper;
import com.lubover.singularity.order.registry.SlotRegistry;
import com.lubover.singularity.order.service.OrderService;
import com.lubover.singularity.order.slot.StockSlot;
import com.lubover.singularity.order.tx.OrderLocalTransaction;

@Service
public class OrderServiceImpl implements OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);
    private static final BigDecimal PRODUCT_PRICE = BigDecimal.valueOf(99);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private final Allocator allocator;
    private final OrderMapper orderMapper;
    private final UserClient userClient;
    private final MerchantClient merchantClient;
    private final SlotRegistry slotRegistry;
    private final StringRedisTemplate redisTemplate;
    private final DefaultMQProducerImpl producerImpl;
    private final DefaultMQProducer producer;
    private final ExecutorService executor = new ThreadPoolExecutor(
            20, 50, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(10000),
            new ThreadPoolExecutor.CallerRunsPolicy());

    @Autowired
    public OrderServiceImpl(
            Registry registry,
            ShardPolicy shardPolicy,
            List<Interceptor> interceptors,
            RocketMQTemplate rocketMQTemplate,
            StringRedisTemplate redisTemplate,
            SlotRegistry slotRegistry,
            OrderMapper orderMapper,
            UserClient userClient,
            MerchantClient merchantClient) {
        DefaultMQProducer producer = (DefaultMQProducer) rocketMQTemplate.getProducer();
        this.producer = producer;
        this.producerImpl = producer.getDefaultMQProducerImpl();

        this.allocator = new DefaultAllocator(
                registry,
                shardPolicy,
                interceptors != null ? interceptors : Collections.emptyList(),
                handler());
        this.orderMapper = orderMapper;
        this.userClient = userClient;
        this.merchantClient = merchantClient;
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
        return payOrder(orderId, userId, null);
    }

    @Override
    public Result payOrder(String orderId, String userId, String userType) {
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

        boolean isMerchant = "merchant".equalsIgnoreCase(userType);

        if (isMerchant) {
            // 商户扣款
            try {
                Long merchantId = Long.parseLong(userId);
                Map<String, Object> deductBody = new HashMap<>();
                deductBody.put("merchantId", merchantId);
                deductBody.put("amount", PRODUCT_PRICE);
                Map<String, Object> deductResult = merchantClient.deductBalance(deductBody);
                if (!Boolean.TRUE.equals(deductResult.get("success"))) {
                    String msg = deductResult.get("message") != null
                            ? String.valueOf(deductResult.get("message"))
                            : "余额不足";
                    return new Result(false, msg);
                }
            } catch (NumberFormatException e) {
                return new Result(false, "merchantId 格式非法");
            } catch (Exception e) {
                return new Result(false, "扣款失败: " + e.getMessage());
            }
        } else {
            // 用户扣款
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
        }

        orderMapper.updateStatus(orderId, "PAID");

        // 支付成功后，给对应商户增加余额
        try {
            String productId = order.getProductId();
            if (productId != null && !productId.isBlank()) {
                Map<String, Object> addBalanceBody = new HashMap<>();
                addBalanceBody.put("productId", productId);
                addBalanceBody.put("amount", PRODUCT_PRICE);
                Map<String, Object> merchantResult = merchantClient.addBalanceByProduct(addBalanceBody);
                if (!Boolean.TRUE.equals(merchantResult.get("success"))) {
                    log.warn("商户余额增加失败: orderId={}, productId={}, result={}", orderId, productId, merchantResult);
                } else {
                    log.info("商户余额增加成功: orderId={}, productId={}, amount={}", orderId, productId, PRODUCT_PRICE);
                }
            }
        } catch (Exception e) {
            log.error("调用商户余额增加接口异常: orderId={}", orderId, e);
        }

        return new Result(true, orderId);
    }

    // ---- transaction ----

    /**
     * 三步显式事务（运行在 worker 线程上）：
     * <ol>
     * <li>发送半消息（prepared，保留 broker 返回的 offsetMsgId）</li>
     * <li>执行 Redis Lua（原子减库存 + 写订单）</li>
     * <li>endTransaction commit 或 rollback</li>
     * </ol>
     * <p>
     * 不用 {@code sendMessageInTransaction}：其返回的 {@link org.apache.rocketmq.client.producer.TransactionSendResult}
     * 会丢失 {@code offsetMsgId}，5.x client 对 4.x broker 做 endTransaction 时会误解析 msgId。
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

        // Step 1: 发送半消息（SendResult 含 broker offsetMsgId，供 endTransaction 解码）
        MessageAccessor.putProperty(nativeMsg, MessageConst.PROPERTY_TRANSACTION_PREPARED, "true");
        MessageAccessor.putProperty(nativeMsg, MessageConst.PROPERTY_PRODUCER_GROUP, producer.getProducerGroup());
        SendResult sendResult;
        try {
            sendResult = producerImpl.send(nativeMsg);
        } catch (Exception e) {
            log.error("发送半消息失败: orderId={}", orderId, e);
            return new Result(false, "发送半消息失败");
        }
        if (sendResult.getSendStatus() != SendStatus.SEND_OK) {
            log.error("发送半消息未成功: orderId={} status={}", orderId, sendResult.getSendStatus());
            return new Result(false, "发送半消息失败");
        }
        if (sendResult.getTransactionId() != null) {
            nativeMsg.putUserProperty("__transactionId__", sendResult.getTransactionId());
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
