package com.dtrung.chatapp.service;

import com.dtrung.chatapp.exception.BusinessException;
import com.dtrung.chatapp.model.FriendShip;
import com.dtrung.chatapp.model.FriendshipStatus;
import com.dtrung.chatapp.model.User;
import com.dtrung.chatapp.request.LoginRequest;
import com.dtrung.chatapp.request.SignUpRequest;
import com.dtrung.chatapp.response.FriendRequestResponse;
import com.dtrung.chatapp.response.FriendshipResponse;
import com.dtrung.chatapp.response.LoginResponse;
import com.dtrung.chatapp.response.MyFriendResponse;
import com.dtrung.chatapp.response.UserSearchResponse;

import java.util.List;
import java.util.UUID;

public interface UserService {
    LoginResponse login(LoginRequest loginRequest);
    User register(SignUpRequest signUpRequest) throws BusinessException;
    FriendshipResponse sendAddFriendRequest(UUID friendId) throws BusinessException;
    List<UserSearchResponse> searchUsers(String search);
    FriendshipResponse acceptOrDeclineAddFriendRequest(
            UUID id,
            FriendshipStatus status)
            throws BusinessException;
    FriendShip unfriend(UUID friendId);
    List<FriendShip> getFriends(UUID userId);
    List<FriendRequestResponse> getPendingFriendRequests();
    List<MyFriendResponse> getMyFriends();
}
