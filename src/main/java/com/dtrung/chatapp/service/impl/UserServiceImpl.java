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
import com.dtrung.chatapp.response.FriendshipResponse;
import com.dtrung.chatapp.response.LoginResponse;
import com.dtrung.chatapp.response.MyFriendResponse;
import com.dtrung.chatapp.response.RelationshipStatus;
import com.dtrung.chatapp.response.UserSearchResponse;
import com.dtrung.chatapp.service.MinioService;
import com.dtrung.chatapp.service.OnlineOfflineService;
import com.dtrung.chatapp.service.UserService;
import com.dtrung.chatapp.utils.JwtUtils;
import com.dtrung.chatapp.utils.SecurityUtils;
import com.dtrung.chatapp.utils.UUIDUtils;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
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
    public FriendshipResponse sendAddFriendRequest(UUID friendId) throws BusinessException {
        User currentLoggedInUser = securityUtils.getCurrentUser();
        if (currentLoggedInUser.getId().equals(friendId)) {
            throw new BusinessException("You cannot send a friend request to yourself");
        }

        User sendToUser = userRepository.findById(friendId)
                .orElseThrow(() -> new BusinessException("User not found"));

        Optional<FriendShip> existingFriendship =
                friendShipRepository.findBetweenUsers(currentLoggedInUser.getId(), sendToUser.getId());
        if (existingFriendship.isPresent()) {
            FriendShip friendship = existingFriendship.get();
            if (friendship.getStatus() == FriendshipStatus.ACCEPTED) {
                throw new BusinessException("Users are already friends");
            }
            if (friendship.getStatus() == FriendshipStatus.PENDING) {
                if (friendship.getSender().getId().equals(currentLoggedInUser.getId())) {
                    throw new BusinessException("Friend request already sent");
                }
                throw new BusinessException("This user has already sent you a friend request");
            }
            throw new BusinessException("Friend request cannot be created");
        }

        FriendShip friendship = FriendShip.builder()
                .sender(currentLoggedInUser)
                .receiver(sendToUser)
                .createdAt(LocalDateTime.now())
                .status(FriendshipStatus.PENDING)
                .build();
        return mapToFriendshipResponse(friendShipRepository.save(friendship));
    }

    @Override
    public List<UserSearchResponse> searchUsers(String search) {
        User currentLoggedInUser = securityUtils.getCurrentUser();
        return userRepository.searchUsers(currentLoggedInUser.getId(), search)
                .stream()
                .map(user -> UserSearchResponse.builder()
                        .userId(user.getId())
                        .username(user.getUsername())
                        .avatar(user.getAvatar())
                        .relationshipStatus(getRelationshipStatus(currentLoggedInUser.getId(), user.getId()))
                        .build())
                .toList();
    }

    @Override
    @Transactional
    public FriendshipResponse acceptOrDeclineAddFriendRequest(UUID id, FriendshipStatus status)
            throws BusinessException {
        if (status != FriendshipStatus.ACCEPTED && status != FriendshipStatus.DECLINE) {
            throw new BusinessException("Only ACCEPT or DECLINE replies are supported");
        }
        User currentLoggedInUser = securityUtils.getCurrentUser();
        FriendShip friendship = friendShipRepository
                .findByIdAndReceiverId(id, currentLoggedInUser.getId());
        if(!Objects.isNull(friendship)) {
            if(friendship.getStatus().equals(FriendshipStatus.PENDING)) {
                if(status.equals(FriendshipStatus.ACCEPTED)) {
                    friendship.setStatus(FriendshipStatus.ACCEPTED);
                    Conversation conversation =
                            Conversation.builder()
                                    .convId(
                                            uuidUtils.getConversationId(
                                                    friendship.getSender().getId(),
                                                    friendship.getReceiver().getId()))
                                    .build();
                    conversationRepository.save(conversation);
                }
                if(status.equals(FriendshipStatus.DECLINE)) {
                    friendship.setStatus(FriendshipStatus.DECLINE);
                    friendShipRepository.delete(friendship);
                    return mapToFriendshipResponse(friendship);
                }
                return mapToFriendshipResponse(friendShipRepository.save(friendship));
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
        return friendShipRepository.findAllByUserId(userId);
    }

    @Override
    public List<FriendRequestResponse> getPendingFriendRequests() {
        User currentLoggedInUser = securityUtils.getCurrentUser();
        return friendShipRepository.findPendingRequestsByReceiverId(currentLoggedInUser.getId())
                .stream()
                .map(friendShip -> FriendRequestResponse.builder()
                        .id(friendShip.getId())
                        .senderId(friendShip.getSender().getId())
                        .senderUsername(friendShip.getSender().getUsername())
                        .senderAvatar(friendShip.getSender().getAvatar())
                        .status(friendShip.getStatus())
                        .createdAt(friendShip.getCreatedAt())
                        .build())
                .toList();
    }

    @Override
    public List<MyFriendResponse> getMyFriends() {
        User currentLoggedInUser = securityUtils.getCurrentUser();
        return friendShipRepository.findAllByUserId(currentLoggedInUser.getId())
                .stream()
                .map(friendShip -> {
                    User friend = friendShip.getSender().getId().equals(currentLoggedInUser.getId())
                            ? friendShip.getReceiver()
                            : friendShip.getSender();
                    return MyFriendResponse.builder()
                            .userId(friend.getId())
                            .username(friend.getUsername())
                            .avatar(friend.getAvatar())
                            .isOnline(onlineOfflineService.isOnlineUser(friend.getId()))
                            .convId(uuidUtils.getConversationId(currentLoggedInUser.getId(), friend.getId()))
                            .build();
                })
                .toList();
    }

    private RelationshipStatus getRelationshipStatus(UUID currentUserId, UUID otherUserId) {
        return friendShipRepository.findBetweenUsers(currentUserId, otherUserId)
                .map(friendShip -> {
                    if (friendShip.getStatus() == FriendshipStatus.ACCEPTED) {
                        return RelationshipStatus.FRIEND;
                    }
                    if (friendShip.getStatus() == FriendshipStatus.PENDING) {
                        return friendShip.getSender().getId().equals(currentUserId)
                                ? RelationshipStatus.PENDING_SENT
                                : RelationshipStatus.PENDING_RECEIVED;
                    }
                    return RelationshipStatus.NONE;
                })
                .orElse(RelationshipStatus.NONE);
    }

    private FriendshipResponse mapToFriendshipResponse(FriendShip friendShip) {
        return FriendshipResponse.builder()
                .id(friendShip.getId())
                .senderId(friendShip.getSender().getId())
                .receiverId(friendShip.getReceiver().getId())
                .status(friendShip.getStatus())
                .createdAt(friendShip.getCreatedAt())
                .build();
    }
}
