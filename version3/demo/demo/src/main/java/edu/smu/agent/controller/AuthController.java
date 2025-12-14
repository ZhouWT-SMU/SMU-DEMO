package edu.smu.agent.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import edu.smu.agent.service.AuthService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");

        AuthService.Session session = authService.login(username, password);
        if (session == null) {
            return ResponseEntity.status(401).body(Map.of(
                    "success", false,
                    "message", "用户名或密码错误"
            ));
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "token", session.token(),
                "username", session.username(),
                "role", session.role(),
                "displayName", session.displayName()
        ));
    }

    @GetMapping("/validate")
    public ResponseEntity<Map<String, Object>> validate(@RequestParam(value = "token", required = false) String token) {
        AuthService.Session session = authService.validate(token);
        if (session == null) {
            return ResponseEntity.status(401).body(Map.of(
                    "success", false,
                    "message", "登录状态已失效，请重新登录"
            ));
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "username", session.username(),
                "role", session.role(),
                "displayName", session.displayName(),
                "token", session.token()
        ));
    }
}
