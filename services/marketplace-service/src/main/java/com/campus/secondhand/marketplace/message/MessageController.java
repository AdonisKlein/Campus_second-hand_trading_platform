package com.campus.secondhand.marketplace.message;

import com.campus.secondhand.marketplace.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/messages")
public class MessageController {
    private final PublicQuestions questions; private final CurrentActorService actors; private final AccountPublicPort accounts;
    public MessageController(PublicQuestions questions,CurrentActorService actors,AccountPublicPort accounts){this.questions=questions;this.actors=actors;this.accounts=accounts;}
    @GetMapping("/item/{itemId}") public ApiResponse<List<MessageView>> list(@PathVariable Long itemId){return ApiResponse.ok(questions.list(itemId).stream().map(this::view).toList());}
    @PostMapping public ApiResponse<MessageView> send(Authentication auth,@Valid @RequestBody SendRequest request){long actor=actors.require(auth).userId();return ApiResponse.created(view(questions.ask(actor,request.itemId(),null,request.content())));}
    @PutMapping("/{id}") public ApiResponse<MessageView> edit(Authentication auth,@PathVariable Long id,@Valid @RequestBody EditRequest request){return ApiResponse.ok(view(questions.edit(actors.require(auth).userId(),id,request.content())));}
    @DeleteMapping("/{id}") public ApiResponse<String> delete(Authentication auth,@PathVariable Long id){questions.delete(actors.require(auth).userId(),id,false);return ApiResponse.ok("删除成功");}
    private MessageView view(Message value){String name=accounts.findPublic(value.getSenderId()).map(a->a.nickname()==null||a.nickname().isBlank()?a.username():a.nickname()).orElse("用户 "+value.getSenderId());return new MessageView(value.getId(),value.getItemId(),value.getSenderId(),name,value.getReceiverId(),value.getContent(),value.getCreatedAt());}
    public record SendRequest(@NotNull Long itemId,@NotBlank @Size(max=500) String content){}
    public record EditRequest(@NotBlank @Size(max=500) String content){}
    public record MessageView(Long id,Long itemId,Long senderId,String senderNickname,Long receiverId,String content,LocalDateTime createdAt){}
}
