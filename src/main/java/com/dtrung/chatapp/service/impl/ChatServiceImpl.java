package com.dtrung.chatapp.service.impl;

import com.dtrung.chatapp.model.*;
import com.dtrung.chatapp.repository.ConversationRepository;
import com.dtrung.chatapp.repository.MessageRepository;
import com.dtrung.chatapp.repository.UserRepository;
import com.dtrung.chatapp.service.ChatService;
import com.dtrung.chatapp.service.OnlineOfflineService;
import com.dtrung.chatapp.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {
    private final SimpMessageSendingOperations messagingTemplate;
    private final UserRepository userRepository;
    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final OnlineOfflineService onlineOfflineService;
    private final SecurityUtils securityUtils;
    @Override
    public Message sendMessage(
            String conversationId,
            Message message,
            SimpMessageHeaderAccessor headerAccessor
    ) {
        Conversation conversation = conversationRepository
                .findByConvId(conversationId);
        UUID receiverId = message.getToUser();
        User sender = userRepository.findByUsername(Objects.requireNonNull(headerAccessor.getUser()).getName());
        message.setFromUser(sender.getId());
        message.setSendTime(LocalDateTime.now());

        boolean isUserOnline = onlineOfflineService.isOnlineUser(receiverId);
        boolean isUserSubscribed = onlineOfflineService.isUserSubscribed(
                receiverId,
                "/topic/" + conversationId
        );

        if (!isUserOnline) {
            // Người nhận không online
            message.setDeliveryStatus(MessageDeliveryStatus.NOT_DELIVERED);
        } else if (!isUserSubscribed) {
            // Người nhận online nhưng không subscribe vào kênh chat
            message.setDeliveryStatus(MessageDeliveryStatus.DELIVERED);
        } else {
            // Người nhận online và đang subscribe vào kênh chat
            message.setDeliveryStatus(MessageDeliveryStatus.SEEN);
        }

        // Lưu tin nhắn vào database
        message.setConversation(conversation);
        conversation.getMessages().add(message);
        messageRepository.save(message);
        conversationRepository.save(conversation);

        // Gửi tin nhắn đến kênh chat
        messagingTemplate.convertAndSend("/topic/" + conversationId, message);

        return message;
    }

    @Override
    public List<Message> getMessages(String conversationId) {
        return messageRepository.findByConversationId(conversationId);
    }

    public void sendMessageSeenStatusToSenderUser(String senderUserId, String conversationId) {

        List<Message> messages = messageRepository.findBySenderIdAndConversationId(
                conversationId, UUID.fromString(senderUserId)
        );
        if (!messages.isEmpty()) {
            for(Message message : messages) {
                message.setDeliveryStatus(MessageDeliveryStatus.SEEN);
            }

            messageRepository.saveAll(messages);
            User loggedInUser = securityUtils.getCurrentUser();
            NotificationToUser notificationToUser =
                    NotificationToUser.builder()
                            .friendId(UUID.fromString(loggedInUser.getId().toString()))
                            .friendUsername(loggedInUser.getUsername())
                            .friendStatus(FriendStatus.ONLINE)
                            .deliveryStatus(MessageDeliveryStatus.SEEN)
                            .build();
            messagingTemplate.convertAndSend("/topic/" + conversationId, notificationToUser);
        }
    }

    public Map<UUID, Boolean> getFriendsUnreadStatus(UUID currentUserId, List<UUID> friends) {
        if (friends == null || friends.isEmpty()) {
            return Collections.emptyMap();
        }

        // 1. Lấy danh sách ID các người bạn CÓ tin nhắn chưa đọc
        // Giả sử trạng thái chưa đọc của bạn là MessageDeliveryStatus.DELIVERED
        List<UUID> unreadFriendIds = messageRepository.findFriendsWithUnreadMessages(
                currentUserId,
                friends
        );

        // 2. Map lại danh sách ban đầu để ra kết quả true/false
        return friends.stream()
                .collect(Collectors.toMap(
                        friendId -> friendId,
                        unreadFriendIds::contains // Trả về true nếu có trong list, ngược lại false
                ));
    }

}
