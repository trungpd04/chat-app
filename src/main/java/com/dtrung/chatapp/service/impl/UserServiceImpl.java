package com.dtrung.chatapp.service.impl;

import com.dtrung.chatapp.exception.BusinessException;
import com.dtrung.chatapp.model.*;
import com.dtrung.chatapp.repository.ConversationRepository;
import com.dtrung.chatapp.repository.FriendShipRepository;
import com.dtrung.chatapp.repository.RoleRepository;
import com.dtrung.chatapp.repository.UserRepository;
import com.dtrung.chatapp.request.LoginRequest;
import com.dtrung.chatapp.request.SignUpRequest;
import com.dtrung.chatapp.response.FriendRequestResponse;
import com.dtrung.chatapp.response.LoginResponse;
import com.dtrung.chatapp.response.OnlineConversation;
import com.dtrung.chatapp.service.MinioService;
import com.dtrung.chatapp.service.OnlineOfflineService;
import com.dtrung.chatapp.service.UserService;
import com.dtrung.chatapp.utils.JwtUtils;
import com.dtrung.chatapp.utils.SecurityUtils;
import com.dtrung.chatapp.utils.UUIDUtils;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    // repository
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final FriendShipRepository friendShipRepository;
    private final ConversationRepository conversationRepository;

    // utils
    private final JwtUtils jwtUtils;
    private final SecurityUtils securityUtils;
    private final UUIDUtils uuidUtils;

    // security
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;

    // other services
    private final MinioService minioService;
    private final OnlineOfflineService onlineOfflineService;

    @Override
    public LoginResponse login(LoginRequest loginRequest) {
        UsernamePasswordAuthenticationToken authenticationToken =
                new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword());
        Authentication authentication = authenticationManager.authenticate(authenticationToken);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        User user = (User) authentication.getPrincipal();
        List<String> roles =
                user.getAuthorities()
                        .stream()
                        .map(GrantedAuthority::getAuthority)
                        .toList();
        String jwt = jwtUtils.generateToken(user);
        return LoginResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .avatar(user.getAvatar())
                .roles(roles)
                .tokenType("Bearer")
                .token(jwt)
                .build();
    }

    @Override
    public User register(SignUpRequest signUpRequest) throws BusinessException {
        if(!signUpRequest.getEmail().isBlank() && userRepository.existsByEmail(signUpRequest.getEmail())) {
            throw new BusinessException("Email already in use");
        }
        if(!signUpRequest.getPhoneNumber().isBlank() && userRepository.existsByPhoneNumber(signUpRequest.getPhoneNumber())) {
            throw new BusinessException("Phone number already in use");
        }
        if(!signUpRequest.getUsername().isBlank() && userRepository.existsByUsername(signUpRequest.getUsername())) {
            throw new BusinessException("Username already in use");
        }
        Optional<Role> role = roleRepository.findByName(ERole.USER);
        if(role.isEmpty()) {
            throw new BusinessException("Role not found");
        }
        Set<Role> roles = new HashSet<>();
        roles.add(role.get());
        User user = User.builder()
                .email(signUpRequest.getEmail())
                .phoneNumber(signUpRequest.getPhoneNumber())
                .username(signUpRequest.getUsername())
                .avatar(minioService.uploadAvatar(signUpRequest.getAvatar()))
                .passwordHash(passwordEncoder.encode(signUpRequest.getPassword()))
                .status(true)
                .roles(roles)
                .build();
        return userRepository.save(user);
    }

    @Override
    @Transactional
    public FriendShip sendAddFriendRequest(UUID friendId) throws BusinessException {
        User currentLoggedInUser = securityUtils.getCurrentUser();
        Optional<User> user = userRepository.findById(friendId);
        if(user.isPresent()) {
            FriendShip friendship = FriendShip.builder()
                    .senderId(currentLoggedInUser.getId())
                    .receiverId(user.get().getId())
                    .createdAt(LocalDateTime.now())
                    .status(FriendshipStatus.PENDING)
                    .build();
            if(!friendShipRepository
                    .existsBySenderIdAndReceiverId(currentLoggedInUser.getId(), user.get().getId())) {
                return friendShipRepository.save(friendship);
            }else{
                throw new BusinessException("Add friend request has already sent");
            }
        }else{
            throw new BusinessException("User not found");
        }
    }

    @Override
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @Override
    @Transactional
    public FriendShip acceptOrDeclineAddFriendRequest(UUID id, FriendshipStatus status)
            throws BusinessException {

        User currentLoggedInUser = securityUtils.getCurrentUser();
        FriendShip friendship = friendShipRepository
                .findByIdAndReceiverId(id, currentLoggedInUser.getId());
        if(!Objects.isNull(friendship)) {
            if(friendship.getStatus().equals(FriendshipStatus.PENDING)) {
                if(status.equals(FriendshipStatus.ACCEPTED)) {
                    friendship.setStatus(FriendshipStatus.ACCEPTED);
                    Conversation conversation =
                            Conversation.builder()
                                    .convId(uuidUtils.getConversationId(friendship.getSenderId(), friendship.getReceiverId()))
                                    .build();
                    conversationRepository.save(conversation);
                }
                if(status.equals(FriendshipStatus.DECLINE)) {
                    friendship.setStatus(FriendshipStatus.DECLINE);
                    friendShipRepository.delete(friendship);
                    return null;
                }
                return friendShipRepository.save(friendship);
            }else{
                throw new BusinessException("Add friend request has already declined or accepted");
            }
        }else{
            throw new BusinessException("Add friend request not found");
        }
    }

    @Override
    public FriendShip unfriend(UUID id) {
        return null;
    }

    @Override
    public List<FriendShip> getFriends(UUID userId) {

        return friendShipRepository.findByUserId(userId);
    }

    @Override
    public List<FriendRequestResponse> getPendingFriendRequests() {
        User currentLoggedInUser = securityUtils.getCurrentUser();
        return friendShipRepository.findPendingRequestsByReceiverId(currentLoggedInUser.getId())
                .stream()
                .map(friendShip -> userRepository.findById(friendShip.getSenderId())
                        .map(sender -> FriendRequestResponse.builder()
                                .id(friendShip.getId())
                                .senderId(sender.getId())
                                .senderUsername(sender.getUsername())
                                .senderAvatar(sender.getAvatar())
                                .status(friendShip.getStatus())
                                .createdAt(friendShip.getCreatedAt())
                                .build())
                        .orElse(null))
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    public List<OnlineConversation> getOnlineConversations() {
        User currentLoggedInUser = securityUtils.getCurrentUser();
        List<User> users = userRepository.findAll();
        return users.stream().filter(user -> !user.getUsername().equalsIgnoreCase(currentLoggedInUser.getUsername()))
                .map(user -> OnlineConversation.builder()
                        .otherSideUserName(user.getUsername())
                        .otherSideUserId(user.getId())
                        .otherSideUserAvatar(user.getAvatar())
                        .convId(uuidUtils.getConversationId(currentLoggedInUser.getId(), user.getId()))
                        .isOnline(onlineOfflineService.isOnlineUser(user.getId()))
                        .build()).toList();
    }
}
