package com.hideandseek.config;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Central provider for the admin password. Order of precedence:
 * 1) Env var ADMIN_PASSWORD
 * 2) .env file ADMIN_PASSWORD (loaded at startup)
 * 3) Spring property admin.password from application.properties
 */
@Component
public class AdminPasswordProvider {

    private final String applicationPropertyPassword;
    private final Dotenv dotenv;

    public AdminPasswordProvider(@Value("${admin.password:}") String applicationPropertyPassword) {
        this.applicationPropertyPassword = applicationPropertyPassword;
        // Load .env if present; don't throw if missing
        Dotenv loaded;
        try {
            loaded = Dotenv.configure()
                    .ignoreIfMissing()
                    .load();
        } catch (Exception e) {
            loaded = null;
        }
        this.dotenv = loaded;
    }

    public String getAdminPassword() {
        // 1) Environment variable
        String fromEnv = System.getenv("ADMIN_PASSWORD");
        if (fromEnv != null && !fromEnv.isBlank()) return fromEnv;

        // 2) .env file
        if (dotenv != null) {
            String fromDotenv = dotenv.get("ADMIN_PASSWORD");
            if (fromDotenv != null && !fromDotenv.isBlank()) return fromDotenv;
        }

        // 3) application.properties
        if (applicationPropertyPassword != null && !applicationPropertyPassword.isBlank()) {
            return applicationPropertyPassword;
        }
        return null;
    }
}
