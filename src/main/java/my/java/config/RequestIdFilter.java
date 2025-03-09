package my.java.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * Фильтр для добавления уникального идентификатора к каждому HTTP-запросу
 * для улучшения отслеживания запросов в логах
 */
@Component
@Order(1)
public class RequestIdFilter implements Filter {

    private static final String REQUEST_ID_KEY = "requestId";
    private static final String REQUEST_ID_HEADER = "X-Request-ID";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try {
            // Получаем идентификатор запроса из заголовка или генерируем новый
            String requestId = getOrCreateRequestId((HttpServletRequest) request);

            // Добавляем идентификатор запроса в MDC для логирования
            MDC.put(REQUEST_ID_KEY, requestId);

            // Продолжаем цепочку фильтров
            chain.doFilter(request, response);
        } finally {
            // Очищаем MDC после завершения запроса
            MDC.remove(REQUEST_ID_KEY);
        }
    }

    /**
     * Получает идентификатор запроса из заголовка или генерирует новый
     *
     * @param request HTTP-запрос
     * @return идентификатор запроса
     */
    private String getOrCreateRequestId(HttpServletRequest request) {
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isEmpty()) {
            requestId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }
        return requestId;
    }
}