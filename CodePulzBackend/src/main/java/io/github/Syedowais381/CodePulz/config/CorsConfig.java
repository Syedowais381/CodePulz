package io.github.Syedowais381.CodePulz.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@Configuration
public class CorsConfig {

    public static final String[] ALLOWED_ORIGINS = {
        "https://codepulz.netlify.app",
        "http://localhost:5173",  // Vite dev server
        "http://127.0.0.1:5173", // Vite dev server alternative
        "http://localhost:3000",  // React dev server alternative
        "https://*.duckdns.org"   // Your duckdns domain
    };

    @Bean
    public CorsFilter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();
        
        // Allow the specified origins
        for (String origin : ALLOWED_ORIGINS) {
            config.addAllowedOrigin(origin);
        }
        
        // Allow common HTTP methods
        config.addAllowedMethod("GET");
        config.addAllowedMethod("POST");
        config.addAllowedMethod("PUT");
        config.addAllowedMethod("DELETE");
        config.addAllowedMethod("OPTIONS");
        
        // Allow all headers
        config.addAllowedHeader("*");
        
        // Allow credentials (important for cookies/auth if you add them later)
        config.setAllowCredentials(true);
        
        // Apply to all paths
        source.registerCorsConfiguration("/**", config);
        
        return new CorsFilter(source);
    }
}