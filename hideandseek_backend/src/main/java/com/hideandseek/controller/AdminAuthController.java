package com.hideandseek.controller;

import com.hideandseek.config.AdminPasswordProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/auth")
@CrossOrigin(origins = "*")
public class AdminAuthController {

	private final AdminPasswordProvider passwordProvider;

	public AdminAuthController(AdminPasswordProvider passwordProvider) {
		this.passwordProvider = passwordProvider;
	}

	@PostMapping("/login")
	public ResponseEntity<?> login(@RequestBody Map<String, String> body, HttpServletRequest request) {
		String provided = body.get("password");
		String expected = passwordProvider.getAdminPassword();

		if (expected == null || expected.isBlank()) {
			return ResponseEntity.status(503).body(Map.of("error", "Admin password not configured"));
		}

		if (provided != null && provided.equals(expected)) {
			HttpSession session = request.getSession(true);
			session.setAttribute("ADMIN_AUTH", true);
			// 4h session window
			session.setMaxInactiveInterval(4 * 60 * 60);
			return ResponseEntity.ok(Map.of("success", true));
		}
		return ResponseEntity.status(401).body(Map.of("success", false, "error", "Invalid password"));
	}

	@PostMapping("/logout")
	public ResponseEntity<?> logout(HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		if (session != null) session.invalidate();
		return ResponseEntity.ok(Map.of("success", true));
	}

	@GetMapping("/status")
	public ResponseEntity<?> status(HttpServletRequest request) {
		HttpSession session = request.getSession(false);
		boolean authed = session != null && Boolean.TRUE.equals(session.getAttribute("ADMIN_AUTH"));
		return ResponseEntity.ok(Map.of("authenticated", authed));
	}
}
