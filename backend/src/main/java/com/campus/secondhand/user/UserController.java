package com.campus.secondhand.user;

import com.campus.secondhand.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserRepository userRepository;
    private final VerificationService verificationService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UserController(UserRepository userRepository, VerificationService verificationService) {
        this.userRepository = userRepository;
        this.verificationService = verificationService;
    }

    @PostMapping("/send-verification")
    public ApiResponse<String> sendVerification(@RequestBody SendVerificationRequest request) {
        try {
            EmailVerification v = verificationService.sendCode(request.email());
            return ApiResponse.ok("验证码已发送");
        } catch (Exception e) {
            return ApiResponse.fail("发送失败");
        }
    }

    @PostMapping("/register")
    public ApiResponse<UserView> register(@Valid @RequestBody RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            return ApiResponse.fail("用户名已存在");
        }
        // verify code
        boolean ok = verificationService.verifyCode(request.email(), request.code());
        if (!ok) {
            return ApiResponse.fail("验证码错误或已过期");
        }

        User user = new User();
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setNickname(request.nickname());
        user.setPhone(null); // phone to be provided in profile
        user.setEmail(request.email());
        userRepository.save(user);
        return ApiResponse.created(UserView.from(user));
    }

    @PostMapping("/login")
    public ApiResponse<UserView> login(@Valid @RequestBody LoginRequest request) {
        return userRepository.findByUsername(request.username())
            .filter(user -> passwordEncoder.matches(request.password(), user.getPasswordHash()))
            .map(user -> ApiResponse.ok(UserView.from(user)))
            .orElseGet(() -> ApiResponse.fail("用户名或密码错误"));
    }

    @GetMapping("/{id}")
    public ApiResponse<UserView> detail(@PathVariable Long id) {
        return userRepository.findById(id)
            .map(user -> ApiResponse.ok(UserView.from(user)))
            .orElseGet(() -> ApiResponse.fail("用户不存在"));
    }

    @PutMapping("/{id}")
    public ApiResponse<UserView> update(@PathVariable Long id, @RequestBody UpdateProfileRequest request) {
        return userRepository.findById(id)
            .map(user -> {
                // if email changed, require verification code
                if (request.email() != null && !request.email().equals(user.getEmail())) {
                    if (request.emailCode() == null || !verificationService.verifyCode(request.email(), request.emailCode())) {
                        return ApiResponse.fail("邮箱验证码错误或已过期");
                    }
                    user.setEmail(request.email());
                }
                user.setNickname(request.nickname());
                user.setPhone(request.phone());
                return ApiResponse.ok(UserView.from(userRepository.save(user)));
            })
            .orElseGet(() -> ApiResponse.fail("用户不存在"));
    }

    @GetMapping("/check")
    public ApiResponse<Object> check(@RequestParam(required = false) String username,
                                     @RequestParam(required = false) String email) {
        boolean usernameExists = false;
        boolean emailExists = false;
        if (username != null && !username.isBlank()) usernameExists = userRepository.existsByUsername(username);
        if (email != null && !email.isBlank()) emailExists = userRepository.existsByEmail(email);
        var map = java.util.Map.of("usernameExists", usernameExists, "emailExists", emailExists);
        return ApiResponse.ok(map);
    }

    public record SendVerificationRequest(@NotBlank String email) {}

    public record RegisterRequest(
        @NotBlank String username,
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d]{6,12}$", message = "密码需为6-12位字母和数字组合") String password,
        String nickname,
        @NotBlank String email,
        @NotBlank String code
    ) {
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record UpdateProfileRequest(String nickname, String phone, String email, String emailCode) {
    }

    public record UserView(Long id, String username, String nickname, String phone, String email) {

        static UserView from(User user) {
            return new UserView(user.getId(), user.getUsername(), user.getNickname(), user.getPhone(), user.getEmail());
        }
    }
}
