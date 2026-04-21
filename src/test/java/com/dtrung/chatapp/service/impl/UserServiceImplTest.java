package com.dtrung.chatapp.service.impl;

import com.dtrung.chatapp.exception.BusinessException;
import com.dtrung.chatapp.model.Conversation;
import com.dtrung.chatapp.model.ERole;
import com.dtrung.chatapp.model.FriendShip;
import com.dtrung.chatapp.model.FriendshipStatus;
import com.dtrung.chatapp.model.Role;
import com.dtrung.chatapp.model.User;
import com.dtrung.chatapp.repository.ConversationRepository;
import com.dtrung.chatapp.repository.FriendShipRepository;
import com.dtrung.chatapp.repository.RoleRepository;
import com.dtrung.chatapp.repository.UserRepository;
import com.dtrung.chatapp.response.FriendshipResponse;
import com.dtrung.chatapp.response.MyFriendResponse;
import com.dtrung.chatapp.response.RelationshipStatus;
import com.dtrung.chatapp.response.UserSearchResponse;
import com.dtrung.chatapp.service.MinioService;
import com.dtrung.chatapp.service.OnlineOfflineService;
import com.dtrung.chatapp.utils.JwtUtils;
import com.dtrung.chatapp.utils.SecurityUtils;
import com.dtrung.chatapp.utils.UUIDUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private FriendShipRepository friendShipRepository;
    @Mock
    private ConversationRepository conversationRepository;
    @Mock
    private JwtUtils jwtUtils;
    @Mock
    private SecurityUtils securityUtils;
    @Mock
    private UUIDUtils uuidUtils;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private MinioService minioService;
    @Mock
    private OnlineOfflineService onlineOfflineService;

    @InjectMocks
    private UserServiceImpl userService;

    private User currentUser;

    @BeforeEach
    void setUp() {
        currentUser = buildUser(UUID.randomUUID(), "current-user");
    }

    @Test
    void sendAddFriendRequest_rejectsSelfRequest() {
        when(securityUtils.getCurrentUser()).thenReturn(currentUser);

        assertThatThrownBy(() -> userService.sendAddFriendRequest(currentUser.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("You cannot send a friend request to yourself");
    }

    @Test
    void sendAddFriendRequest_createsPendingRequest() throws BusinessException {
        UUID friendId = UUID.randomUUID();
        User otherUser = buildUser(friendId, "alice");
        FriendShip savedFriendship = FriendShip.builder()
                .id(UUID.randomUUID())
                .sender(currentUser)
                .receiver(otherUser)
                .status(FriendshipStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        when(securityUtils.getCurrentUser()).thenReturn(currentUser);
        when(userRepository.findById(friendId)).thenReturn(Optional.of(otherUser));
        when(friendShipRepository.findBetweenUsers(currentUser.getId(), friendId)).thenReturn(Optional.empty());
        when(friendShipRepository.save(any(FriendShip.class))).thenReturn(savedFriendship);

        FriendshipResponse response = userService.sendAddFriendRequest(friendId);

        assertThat(response.getSenderId()).isEqualTo(currentUser.getId());
        assertThat(response.getReceiverId()).isEqualTo(friendId);
        assertThat(response.getStatus()).isEqualTo(FriendshipStatus.PENDING);
    }

    @Test
    void searchUsers_mapsRelationshipStatuses() {
        UUID friendId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        User friendUser = buildUser(friendId, "friend-user");
        User requesterUser = buildUser(requesterId, "requester-user");

        when(securityUtils.getCurrentUser()).thenReturn(currentUser);
        when(userRepository.searchUsers(currentUser.getId(), "user")).thenReturn(List.of(friendUser, requesterUser));
        when(friendShipRepository.findBetweenUsers(currentUser.getId(), friendId))
                .thenReturn(Optional.of(FriendShip.builder()
                        .sender(currentUser)
                        .receiver(friendUser)
                        .status(FriendshipStatus.ACCEPTED)
                        .createdAt(LocalDateTime.now())
                        .build()));
        when(friendShipRepository.findBetweenUsers(currentUser.getId(), requesterId))
                .thenReturn(Optional.of(FriendShip.builder()
                        .sender(requesterUser)
                        .receiver(currentUser)
                        .status(FriendshipStatus.PENDING)
                        .createdAt(LocalDateTime.now())
                        .build()));

        List<UserSearchResponse> responses = userService.searchUsers("user");

        assertThat(responses)
                .extracting(UserSearchResponse::getRelationshipStatus)
                .containsExactly(RelationshipStatus.FRIEND, RelationshipStatus.PENDING_RECEIVED);
    }

    @Test
    void acceptOrDeclineAddFriendRequest_acceptsAndCreatesConversation() throws BusinessException {
        UUID requestId = UUID.randomUUID();
        User sender = buildUser(UUID.randomUUID(), "sender");
        FriendShip pendingFriendship = FriendShip.builder()
                .id(requestId)
                .sender(sender)
                .receiver(currentUser)
                .status(FriendshipStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        when(securityUtils.getCurrentUser()).thenReturn(currentUser);
        when(friendShipRepository.findByIdAndReceiverId(requestId, currentUser.getId())).thenReturn(pendingFriendship);
        when(uuidUtils.getConversationId(sender.getId(), currentUser.getId())).thenReturn("conv-1");
        when(friendShipRepository.save(any(FriendShip.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FriendshipResponse response = userService.acceptOrDeclineAddFriendRequest(requestId, FriendshipStatus.ACCEPTED);

        assertThat(response.getStatus()).isEqualTo(FriendshipStatus.ACCEPTED);

        ArgumentCaptor<Conversation> conversationCaptor = ArgumentCaptor.forClass(Conversation.class);
        verify(conversationRepository).save(conversationCaptor.capture());
        assertThat(conversationCaptor.getValue().getConvId()).isEqualTo("conv-1");
    }

    @Test
    void getMyFriends_returnsMappedFriendsOnly() {
        User friend = buildUser(UUID.randomUUID(), "friend");
        FriendShip acceptedFriendship = FriendShip.builder()
                .id(UUID.randomUUID())
                .sender(friend)
                .receiver(currentUser)
                .status(FriendshipStatus.ACCEPTED)
                .createdAt(LocalDateTime.now())
                .build();

        when(securityUtils.getCurrentUser()).thenReturn(currentUser);
        when(friendShipRepository.findAllByUserId(currentUser.getId())).thenReturn(List.of(acceptedFriendship));
        when(onlineOfflineService.isOnlineUser(friend.getId())).thenReturn(true);
        when(uuidUtils.getConversationId(currentUser.getId(), friend.getId())).thenReturn("conv-friend");

        List<MyFriendResponse> responses = userService.getMyFriends();

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().getUserId()).isEqualTo(friend.getId());
        assertThat(responses.getFirst().isOnline()).isTrue();
        assertThat(responses.getFirst().getConvId()).isEqualTo("conv-friend");
    }

    @Test
    void acceptOrDeclineAddFriendRequest_rejectsUnsupportedReply() {
        assertThatThrownBy(() -> userService.acceptOrDeclineAddFriendRequest(UUID.randomUUID(), FriendshipStatus.BLOCKED))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Only ACCEPT or DECLINE replies are supported");

        verify(friendShipRepository, never()).findByIdAndReceiverId(any(UUID.class), any(UUID.class));
    }

    private User buildUser(UUID id, String username) {
        return User.builder()
                .id(id)
                .username(username)
                .email(username + "@example.com")
                .avatar("avatar-" + username)
                .roles(Set.of(Role.builder().name(ERole.USER).build()))
                .build();
    }
}
