package com.hideandseek.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Simple session-based guard for admin assets and endpoints.
 * - Protects: /api/admin/** and /admin-dashboard.html
 * - Public: /admin-login.html, /api/admin/auth/login, /api/admin/auth/status, /api/admin/auth/logout
 * - Uses HTTP session attribute "ADMIN_AUTH" = true when authenticated.
 */
@Configuration
public class AdminSecurityConfig {

	@Component
	@Order(1)
	public static class AdminAuthFilter extends OncePerRequestFilter {
		private static final AntPathMatcher matcher = new AntPathMatcher();

		private static final String[] PUBLIC_PATHS = new String[]{
				"/admin-login.html",
				"/api/admin/auth/login",
				"/api/admin/auth/status",
				"/api/admin/auth/logout",
				"/",
				"/admin",
				"/admin.html" // we'll redirect to login page anyway
		};

		@Override
		protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
			String path = request.getRequestURI();

			// Allow static assets like css/js/images in same folder
			if (isPublic(path) || isStaticAsset(path)) {
				filterChain.doFilter(request, response);
				return;
			}

			boolean adminPath = path.startsWith("/api/admin/") || path.equals("/admin-dashboard.html") || path.equals("/admin-game.html");
			if (!adminPath) {
				filterChain.doFilter(request, response);
				return;
			}

			HttpSession session = request.getSession(false);
			boolean authed = session != null && Boolean.TRUE.equals(session.getAttribute("ADMIN_AUTH"));
			if (!authed) {
				// For API endpoints, return 401 JSON; for HTML, redirect
				if (path.startsWith("/api/")) {
					response.setStatus(HttpStatus.UNAUTHORIZED.value());
					response.setContentType("application/json");
					response.getWriter().write("{\"error\":\"Unauthorized\"}");
				} else {
					response.sendRedirect("/admin-login.html");
				}
				return;
			}

			filterChain.doFilter(request, response);
		}

		private boolean isPublic(String path) {
			for (String p : PUBLIC_PATHS) {
				if (matcher.match(p, path)) return true;
			}
			return false;
		}

		private boolean isStaticAsset(String path) {
			return path.startsWith("/static/") || path.startsWith("/assets/") ||
					path.endsWith(".css") || path.endsWith(".js") || path.endsWith(".png") ||
					path.endsWith(".jpg") || path.endsWith(".ico") || path.endsWith(".svg");
		}
	}
}
