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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @PostMapping("/register")
    public ApiResponse<UserView> register(@Valid @RequestBody RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            return ApiResponse.fail("用户名已存在");
        }

        User user = new User();
        user.setUsername(request.username());
        user.setPasswordHash(Integer.toHexString(request.password().hashCode()));
        user.setNickname(request.nickname());
        user.setPhone(request.phone());
        userRepository.save(user);
        return ApiResponse.created(UserView.from(user));
    }

    @PostMapping("/login")
    public ApiResponse<UserView> login(@Valid @RequestBody LoginRequest request) {
        String passwordHash = Integer.toHexString(request.password().hashCode());
        return userRepository.findByUsername(request.username())
            .filter(user -> user.getPasswordHash().equals(passwordHash))
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

    public record RegisterRequest(
        @NotBlank String username,
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d]{6,12}$", message = "密码需为6-12位字母和数字组合") String password,
        String nickname,
        String phone
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

