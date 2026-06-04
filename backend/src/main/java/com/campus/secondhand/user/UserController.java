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
            // if not expired and previously sent, return message indicating already sent
            if (v.getExpiresAt().isAfter(java.time.LocalDateTime.now()) && v.getCreatedAt().isBefore(java.time.LocalDateTime.now().minusSeconds(1))) {
                return ApiResponse.ok("验证码已发送");
            }
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
                user.setNickname(request.nickname());
                user.setPhone(request.phone());
                user.setEmail(request.email());
                return ApiResponse.ok(UserView.from(userRepository.save(user)));
            })
            .orElseGet(() -> ApiResponse.fail("用户不存在"));
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

    public record UpdateProfileRequest(String nickname, String phone, String email) {
    }

    public record UserView(Long id, String username, String nickname, String phone, String email) {

        static UserView from(User user) {
            return new UserView(user.getId(), user.getUsername(), user.getNickname(), user.getPhone(), user.getEmail());
        }
    }
}
