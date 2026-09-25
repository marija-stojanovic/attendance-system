package com.attendance.course.realtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class RealtimeWebSocketHandler extends TextWebSocketHandler {
    private final JwtDecoder decoder;
    private final ObjectMapper json;
    private final Map<String, WebSocketSession> employees = new ConcurrentHashMap<>();

    public RealtimeWebSocketHandler(JwtDecoder decoder, ObjectMapper json) {
        this.decoder = decoder;
        this.json = json;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        session.getAttributes().put("authenticated", false);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        if (Boolean.TRUE.equals(session.getAttributes().get("authenticated"))) return;
        try {
            JsonNode request = json.readTree(message.getPayload());
            var jwt = decoder.decode(request.path("token").asText());
            if (!"EMPLOYEE".equals(jwt.getClaimAsString("role"))) {
                session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Uloga nije dozvoljena."));
                return;
            }
            session.getAttributes().put("authenticated", true);
            WebSocketSession safe = new ConcurrentWebSocketSessionDecorator(session, 5000, 65536);
            employees.put(session.getId(), safe);
            safe.sendMessage(new TextMessage("{\"type\":\"CONNECTED\"}"));
        } catch (RuntimeException exception) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Prijava nije važeća."));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        employees.remove(session.getId());
    }

    @EventListener
    @Order(Ordered.LOWEST_PRECEDENCE)
    public void broadcast(RealtimeEvent event) {
        TextMessage message;
        try {
            message = new TextMessage(json.writeValueAsString(event));
        } catch (Exception exception) {
            return;
        }
        employees.forEach((id, session) -> {
            try {
                if (session.isOpen()) session.sendMessage(message);
                else employees.remove(id);
            } catch (Exception exception) {
                employees.remove(id);
                try {
                    session.close();
                } catch (Exception ignored) {
                }
            }
        });
    }
}
