package io.github.Syedowais381.CodePulz.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.beans.factory.annotation.Autowired;
import io.github.Syedowais381.CodePulz.websocket.ExecutionWebSocketHandler;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final ExecutionWebSocketHandler handler;

    @Autowired
    public WebSocketConfig(ExecutionWebSocketHandler handler) {
        this.handler = handler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
         // Attach handler at /ws/execute/{sessionId}
         registry.addHandler(handler, "/ws/execute/*")
             .setAllowedOrigins(CorsConfig.ALLOWED_ORIGINS)
             .setAllowedOriginPatterns("https://*.duckdns.org");  // Allow duckdns subdomains
    }
}
