package com.attendance.course.realtime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class RealtimeWebSocketConfig implements WebSocketConfigurer {
    private final RealtimeWebSocketHandler handler;
    private final String[] origins;

    public RealtimeWebSocketConfig(RealtimeWebSocketHandler handler,
            @Value("${authentication.allowed-origins}") String origins) {
        this.handler = handler;
        this.origins = origins.split(",");
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/realtime").setAllowedOrigins(origins);
    }
}
