package com.campus.secondhand.user;

import com.campus.secondhand.common.ApiResponse;
import com.campus.secondhand.security.CurrentActorService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
public class UserController {
    private final UserRepository users;
    private final CurrentActorService actors;

    public UserController(UserRepository users, CurrentActorService actors) {
        this.users = users;
        this.actors = actors;
    }

    @GetMapping("/me")
    public ApiResponse<UserView> me() {
        return ApiResponse.ok(UserView.from(users.findById(actors.require().userId()).orElseThrow()));
    }

    @PutMapping("/me")
    public ApiResponse<UserView> update(@Valid @RequestBody UpdateProfileRequest request) {
        User user = users.findById(actors.require().userId()).orElseThrow();
        user.setNickname(request.nickname());
        user.setPhone(request.phone());
        return ApiResponse.ok(UserView.from(users.save(user)));
    }

    public record UpdateProfileRequest(String nickname, String phone) {}

    public record UserView(Long id, String username, String nickname, String phone, String email, String role, String status) {
        public static UserView from(User user) {
            return new UserView(user.getId(), user.getUsername(), user.getNickname(), user.getPhone(),
                user.getEmail(), user.getRole(), user.getStatus());
        }
    }
}
