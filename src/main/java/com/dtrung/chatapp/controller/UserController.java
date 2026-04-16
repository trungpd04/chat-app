package com.dtrung.chatapp.controller;

import com.dtrung.chatapp.exception.BusinessException;
import com.dtrung.chatapp.model.FriendShip;
import com.dtrung.chatapp.model.FriendshipStatus;
import com.dtrung.chatapp.model.User;
import com.dtrung.chatapp.request.LoginRequest;
import com.dtrung.chatapp.request.SignUpRequest;
import com.dtrung.chatapp.response.LoginResponse;
import com.dtrung.chatapp.response.OnlineConversation;
import com.dtrung.chatapp.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping(value = "/register", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<User> register(
            @Valid SignUpRequest signUpRequest
            ) throws BusinessException {
        User user = userService.register(signUpRequest);
        return ResponseEntity.ok(user);
    }
    @PostMapping(value = "/login")
    public ResponseEntity<LoginResponse> login(

            @RequestBody @Valid LoginRequest loginRequest
    ) {
        return ResponseEntity.ok(userService.login(loginRequest));
    }

    @PostMapping("/sendAddFriendRequest")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<FriendShip> sendAddFriendRequest(
            @RequestParam(name = "userId") String userId
    ) throws BusinessException {
        return ResponseEntity.ok(userService.sendAddFriendRequest(UUID.fromString(userId)));
    }
    @PostMapping("/replyFriendRequest")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> replyFriendRequest(
            @RequestParam("id") UUID id,
            @RequestParam("reply") FriendshipStatus reply
            ) throws BusinessException {
        FriendShip friendShip = userService.acceptOrDeclineAddFriendRequest(id, reply);
        if (friendShip == null) {
            return ResponseEntity.ok("Remove request");
        }
        return ResponseEntity.ok(userService.acceptOrDeclineAddFriendRequest(id, reply));
    }

    @GetMapping("")
    public ResponseEntity<List<User>> getUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @GetMapping("/friends")
    public ResponseEntity<List<OnlineConversation>> getFriends() {
        return ResponseEntity.ok(userService.getOnlineConversations());
    }
}
