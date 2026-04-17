package com.dtrung.chatapp.service.impl;

import com.dtrung.chatapp.model.*;
import com.dtrung.chatapp.repository.MessageRepository;
import com.dtrung.chatapp.repository.UserRepository;
import com.dtrung.chatapp.service.OnlineOfflineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

@Slf4j
@Service
@RequiredArgsConstructor
public class OnlineOfflineServiceImpl implements OnlineOfflineService {

    private final Set<UUID> onlineUsers = new ConcurrentSkipListSet<>();
    private final Map<UUID, Set<String>> userSubscribed = new ConcurrentHashMap<>();
    private final UserRepository userRepository;
    private final SimpMessageSendingOperations messagingTemplate;
    private final MessageRepository messageRepository;


    @Override
    public void addOnlineUser(Principal user) {
        if(user == null) { return; }
        UsernamePasswordAuthenticationToken authentication = (UsernamePasswordAuthenticationToken) user;
        User onlineUser = (User) authentication.getPrincipal();
        log.info("{} is online", onlineUser.getUsername());
        for(UUID id : onlineUsers) {
            messagingTemplate.convertAndSend(
                    "/topic/" + id,
                    NotificationToUser.builder()
                            .friendStatus(FriendStatus.ONLINE)
                            .friendId(onlineUser.getId())
                            .friendUsername(onlineUser.getUsername())
                            .build()
            );
        }
        onlineUsers.add(onlineUser.getId());
    }

    @Override
    public void removeOnlineUser(Principal user) {
        UsernamePasswordAuthenticationToken authentication = (UsernamePasswordAuthenticationToken) user;
        User onlineUser = (User) authentication.getPrincipal();
        userSubscribed.remove(onlineUser.getId());
        onlineUsers.remove(onlineUser.getId());
        for(UUID id : onlineUsers) {
            messagingTemplate.convertAndSend(
              "/topic/" + id,
              NotificationToUser.builder()
                      .friendStatus(FriendStatus.OFFLINE)
                      .friendId(onlineUser.getId())
                      .friendUsername(onlineUser.getUsername())
                      .build()
            );
        }
    }

    @Override
    public boolean isOnlineUser(UUID userId) {
        return onlineUsers.contains(userId);
    }

    @Override
    public boolean isUserSubscribed(UUID userId, String subscription) {
        Set<String> subscribed = userSubscribed.getOrDefault(userId, Collections.emptySet());
        return subscribed.contains(subscription);
    }

    @Override
    public void addUserSubscribed(Principal user, String subscribedChannel) {
        User user1 = userRepository.findByUsername(user.getName());
        log.info("{} subscribed to {}", user1.getUsername(), subscribedChannel);
        Set<String> subscriptions = userSubscribed.getOrDefault(user1.getId(), new HashSet<>());
        subscriptions.add(subscribedChannel);
        userSubscribed.put(user1.getId(), subscriptions);
    }

    @Override
    public void removeUserSubscribed(Principal user, String subscribedChannel) {
        User user1 = userRepository.findByUsername(user.getName());
        Set<String> subscribed = userSubscribed.get(user1.getId());
        log.info("after remove channel: {} \n user: {} \n present user subscribed {}", subscribedChannel, user1.getUsername(), subscribed.toString());
        Set<String> subscriptions = userSubscribed.getOrDefault(user1.getId(), new HashSet<>());
        log.info("unsubscription! {} unsubscribed {}", user1.getUsername(), subscribedChannel);
        subscriptions.remove(subscribedChannel);

        userSubscribed.put(user1.getId(), subscriptions);
    }

    @Override
    public Map<String, Set<String>> getUserSubscribed() {
        return Map.of();
    }

    @Override
    public Set<UUID> getOnlineUsers() {
        return this.onlineUsers;
    }

    @Override
    public void notifySender(
            UUID senderId,
            String conversationId,
            List<Conversation> conversations,
            MessageDeliveryStatus messageStatus
    ) {
        List<NotificationToUser> notifications = new ArrayList<>();
        List<Message> messages = messageRepository.findByConversationId(conversationId);
        for(Message message : messages) {
            if(message.getDeliveryStatus() == MessageDeliveryStatus.DELIVERED
                || message.getDeliveryStatus() == MessageDeliveryStatus.NOT_DELIVERED
            ) {
                NotificationToUser notificationToUser =
                        NotificationToUser.builder()
                                .unread(true)
                                .friendId(senderId)
                                .build();
                notifications.add(notificationToUser);
            }
        }
        messagingTemplate.convertAndSend("/topic/" + senderId, notifications);
    }
}
