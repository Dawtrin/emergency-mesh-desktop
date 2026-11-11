package com.rescue.mesh.controller;

import com.rescue.mesh.db.DatabaseManager;
import com.rescue.mesh.db.UserDao;
import com.rescue.mesh.model.UserEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserApiController {

    private final DatabaseManager databaseManager;
    private final PasswordEncoder passwordEncoder;

    public AdminUserApiController(DatabaseManager databaseManager, PasswordEncoder passwordEncoder) {
        this.databaseManager = databaseManager;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    public List<UserEntity> listUsers() {
        return databaseManager.getUserDao().findAll();
    }

    @PostMapping
    public ResponseEntity<?> createUser(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String displayName = body.get("displayName");
        String password = body.get("password");
        String role = body.getOrDefault("role", "DISPATCHER");

        if (username == null || password == null || displayName == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Thiếu thông tin bắt buộc"));
        }

        UserDao dao = databaseManager.getUserDao();
        if (dao.findByUsername(username.trim()) != null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Tên đăng nhập đã tồn tại"));
        }

        UserEntity user = new UserEntity(
                username.trim(),
                displayName.trim(),
                passwordEncoder.encode(password),
                role
        );

        boolean success = dao.insert(user);
        if (success) {
            return ResponseEntity.ok(Map.of("status", "CREATED", "username", user.getUsername()));
        }
        return ResponseEntity.internalServerError().body(Map.of("error", "Không thể tạo tài khoản"));
    }

    @PostMapping("/{username}/reset-password")
    public ResponseEntity<?> resetPassword(@PathVariable String username, @RequestBody Map<String, String> body) {
        String newPassword = body.get("newPassword");
        if (newPassword == null || newPassword.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Mật khẩu mới không được để trống"));
        }
        databaseManager.getUserDao().updatePassword(username, passwordEncoder.encode(newPassword));
        return ResponseEntity.ok(Map.of("status", "PASSWORD_RESET", "username", username));
    }

    @PatchMapping("/{username}")
    public ResponseEntity<?> updateStatus(@PathVariable String username, @RequestBody Map<String, Object> body) {
        String role = (String) body.get("role");
        Boolean active = (Boolean) body.get("active");

        UserDao dao = databaseManager.getUserDao();
        UserEntity u = dao.findByUsername(username);
        if (u == null) {
            return ResponseEntity.notFound().build();
        }

        String finalRole = role != null ? role : u.getRole();
        boolean finalActive = active != null ? active : u.isActive();

        dao.updateRoleAndStatus(username, finalRole, finalActive);
        return ResponseEntity.ok(Map.of("status", "UPDATED", "username", username));
    }
}
