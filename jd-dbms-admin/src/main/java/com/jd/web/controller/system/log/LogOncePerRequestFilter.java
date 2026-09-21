package com.jd.web.controller.system.log;

import com.jd.common.tools.common.util.LogUtils;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Intercept the log and put in the trace id
 *
 * @author Jiaju Zhuang
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@Component
@Slf4j
public class LogOncePerRequestFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws
            ServletException, IOException {
        try {
            MDC.put(LogUtils.TRACE_ID, LogUtils.generateTraceId());
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(LogUtils.TRACE_ID);
        }
    }
}
