package com.dtrung.chatapp.controller;

import com.dtrung.chatapp.exception.BusinessException;
import com.dtrung.chatapp.model.FriendshipStatus;
import com.dtrung.chatapp.model.User;
import com.dtrung.chatapp.request.LoginRequest;
import com.dtrung.chatapp.request.SignUpRequest;
import com.dtrung.chatapp.response.FriendRequestResponse;
import com.dtrung.chatapp.response.FriendshipResponse;
import com.dtrung.chatapp.response.LoginResponse;
import com.dtrung.chatapp.response.MyFriendResponse;
import com.dtrung.chatapp.response.UserSearchResponse;
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
    @PreAuthorize("hasAuthority('ROLE_USER')")
    public ResponseEntity<FriendshipResponse> sendAddFriendRequest(
            @RequestParam(name = "userId") String userId
    ) throws BusinessException {
        return ResponseEntity.ok(userService.sendAddFriendRequest(UUID.fromString(userId)));
    }

    @PostMapping("/replyFriendRequest")
    @PreAuthorize("hasAuthority('ROLE_USER')")
    public ResponseEntity<FriendshipResponse> replyFriendRequest(
            @RequestParam("id") UUID id,
            @RequestParam("reply") String reply
            ) throws BusinessException {
        FriendshipResponse friendshipResponse = userService.acceptOrDeclineAddFriendRequest(
                id,
                FriendshipStatus.fromRequestValue(reply)
        );
        return ResponseEntity.ok(friendshipResponse);
    }

    @GetMapping("")
    @PreAuthorize("hasAuthority('ROLE_USER')")
    public ResponseEntity<List<UserSearchResponse>> getUsers(
            @RequestParam(value = "search", required = false) String search
    ) {
        return ResponseEntity.ok(userService.searchUsers(search));
    }

    @GetMapping("/friends")
    @PreAuthorize("hasAuthority('ROLE_USER')")
    public ResponseEntity<List<MyFriendResponse>> getFriends() {
        return ResponseEntity.ok(userService.getMyFriends());
    }

    @GetMapping("/friendRequests")
    @PreAuthorize("hasAuthority('ROLE_USER')")
    public ResponseEntity<List<FriendRequestResponse>> getFriendRequests() {
        return ResponseEntity.ok(userService.getPendingFriendRequests());
    }
}
