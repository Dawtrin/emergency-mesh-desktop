package com.rescue.mesh.config;

import com.rescue.mesh.db.DatabaseManager;
import com.rescue.mesh.db.UserDao;
import com.rescue.mesh.model.UserEntity;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final DatabaseManager databaseManager;

    public SecurityConfig(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
        initDefaultAdminIfNeeded();
    }

    private void initDefaultAdminIfNeeded() {
        UserDao dao = databaseManager.getUserDao();
        if (dao.countUsers() == 0) {
            PasswordEncoder encoder = new BCryptPasswordEncoder();
            // Khởi tạo tài khoản Quản trị dã chiến mặc định
            UserEntity admin = new UserEntity(
                    "admin",
                    "Chỉ Huy Trưởng C2",
                    encoder.encode("admin123"),
                    "ADMIN"
            );
            dao.insert(admin);
            System.out.println("[SECURITY] Đã khởi tạo tài khoản ADMIN mặc định: admin / admin123");
        }
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            UserEntity user = databaseManager.getUserDao().findByUsername(username);
            if (user == null || !user.isActive()) {
                throw new UsernameNotFoundException("Không tìm thấy người dùng hoặc tài khoản bị khóa: " + username);
            }
            return User.builder()
                    .username(user.getUsername())
                    .password(user.getPasswordHash())
                    .roles(user.getRole())
                    .disabled(!user.isActive())
                    .accountLocked(user.isLocked())
                    .build();
        };
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable) // Tối ưu cho môi trường mạng LAN ngoại tuyến
            .authorizeHttpRequests(auth -> auth
                // Tài nguyên tĩnh, WebSocket handshake, và API kiểm tra sức khỏe mở tự do
                .requestMatchers("/", "/index.html", "/login.html", "/css/**", "/js/**", "/images/**", "/vendor/**", "/fonts/**", "/tiles/**", "/ws/**", "/api/health", "/api/auth/login", "/api/auth/me").permitAll()
                // Mọi API khác cho phép truy cập sau xác thực hoặc phiên làm việc
                .anyRequest().permitAll()
            )
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable);

        return http.build();
    }
}
