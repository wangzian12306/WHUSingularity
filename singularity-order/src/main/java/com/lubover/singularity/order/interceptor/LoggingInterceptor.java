package com.lubover.singularity.order.interceptor;

import com.lubover.singularity.api.Context;
import com.lubover.singularity.api.Interceptor;
import com.lubover.singularity.api.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 日志拦截器：记录每次抢占的 actor、slot、耗时及结果
 */
@Component
public class LoggingInterceptor implements Interceptor {

    private static final Logger log = LoggerFactory.getLogger(LoggingInterceptor.class);

    @Override
    public void handle(Context context) {
        String actorId = context.getCurrActor().getId();
        String slotId = context.getCurrSlot().getId();
        log.info("snag start: actor={} slot={}", actorId, slotId);

        long start = System.currentTimeMillis();
        context.next();

        Result result = context.getResult();
        long cost = System.currentTimeMillis() - start;
        if (result != null && result.isSuccess()) {
            log.info("snag success: actor={} slot={} orderId={} cost={}ms",
                    actorId, slotId, result.getMessage(), cost);
        } else {
            log.warn("snag failed: actor={} slot={} reason={} cost={}ms",
                    actorId, slotId, result != null ? result.getMessage() : "no result", cost);
        }
    }
}
