package com.rescue.mesh.controller;

import com.rescue.mesh.db.DatabaseManager;
import com.rescue.mesh.db.UserDao;
import com.rescue.mesh.model.UserEntity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthApiController {

    private final DatabaseManager databaseManager;
    private final PasswordEncoder passwordEncoder;

    public AuthApiController(DatabaseManager databaseManager, PasswordEncoder passwordEncoder) {
        this.databaseManager = databaseManager;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String username = body.get("username");
        String password = body.get("password");

        if (username == null || password == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Vui lòng nhập tên đăng nhập và mật khẩu"));
        }

        UserDao dao = databaseManager.getUserDao();
        UserEntity user = dao.findByUsername(username.trim());

        if (user == null || !user.isActive()) {
            return ResponseEntity.status(401).body(Map.of("error", "Tài khoản không tồn tại hoặc đã bị vô hiệu hóa"));
        }

        if (user.isLocked()) {
            return ResponseEntity.status(403).body(Map.of("error", "Tài khoản bị tạm khóa do nhập sai nhiều lần. Vui lòng thử lại sau."));
        }

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            dao.recordFailedLogin(username, 5, 5 * 60 * 1000L); // Khóa 5 phút sau 5 lần sai
            return ResponseEntity.status(401).body(Map.of("error", "Sai mật khẩu"));
        }

        // Đăng nhập thành công
        dao.recordSuccessLogin(username);
        HttpSession session = request.getSession(true);
        session.setAttribute("user", user);

        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "username", user.getUsername(),
                "displayName", user.getDisplayName(),
                "role", user.getRole()
        ));
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute("user") != null) {
            UserEntity user = (UserEntity) session.getAttribute("user");
            return ResponseEntity.ok(Map.of(
                    "authenticated", true,
                    "username", user.getUsername(),
                    "displayName", user.getDisplayName(),
                    "role", user.getRole()
            ));
        }
        // Mặc định khách truy cập là OBSERVER nếu chưa đăng nhập
        return ResponseEntity.ok(Map.of(
                "authenticated", false,
                "username", "guest",
                "displayName", "Khách Quan Sát",
                "role", "OBSERVER"
        ));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ResponseEntity.ok(Map.of("status", "LOGGED_OUT"));
    }

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@RequestBody Map<String, String> body, HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Chưa đăng nhập"));
        }

        UserEntity currentUser = (UserEntity) session.getAttribute("user");
        String oldPassword = body.get("oldPassword");
        String newPassword = body.get("newPassword");

        if (oldPassword == null || newPassword == null || newPassword.length() < 6) {
            return ResponseEntity.badRequest().body(Map.of("error", "Mật khẩu mới phải từ 6 ký tự trở lên"));
        }

        UserDao dao = databaseManager.getUserDao();
        UserEntity freshUser = dao.findByUsername(currentUser.getUsername());
        if (freshUser == null || !passwordEncoder.matches(oldPassword, freshUser.getPasswordHash())) {
            return ResponseEntity.status(400).body(Map.of("error", "Mật khẩu cũ không chính xác"));
        }

        dao.updatePassword(currentUser.getUsername(), passwordEncoder.encode(newPassword));
        return ResponseEntity.ok(Map.of("status", "PASSWORD_CHANGED"));
    }
}
