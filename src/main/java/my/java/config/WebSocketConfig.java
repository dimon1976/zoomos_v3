// src/main/java/my/java/config/WebSocketConfig.java
package my.java.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Конфигурация WebSocket для отслеживания прогресса импорта в реальном времени
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic"); // Префикс для исходящих сообщений
        config.setApplicationDestinationPrefixes("/app"); // Префикс для входящих сообщений
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Вариант 1: Использовать allowedOriginPatterns вместо allowedOrigins
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")  // Вместо setAllowedOrigins("*")
                .withSockJS();

    }
}