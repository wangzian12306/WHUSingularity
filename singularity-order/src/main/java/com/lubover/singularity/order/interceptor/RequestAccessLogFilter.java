package com.lubover.singularity.order.interceptor;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class RequestAccessLogFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestAccessLogFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long start = System.currentTimeMillis();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long costMs = System.currentTimeMillis() - start;
            log.info("http access: method={} uri={} status={} instancePort={} remote={} cost={}ms",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    request.getLocalPort(),
                    request.getRemoteAddr(),
                    costMs);
        }
    }
}
