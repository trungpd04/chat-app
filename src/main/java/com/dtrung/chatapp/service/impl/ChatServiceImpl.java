package com.dtrung.chatapp.service.impl;

import com.dtrung.chatapp.model.*;
import com.dtrung.chatapp.repository.ConversationRepository;
import com.dtrung.chatapp.repository.MessageRepository;
import com.dtrung.chatapp.repository.UserRepository;
import com.dtrung.chatapp.service.ChatService;
import com.dtrung.chatapp.service.OnlineOfflineService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {
    private final SimpMessageSendingOperations messagingTemplate;
    private final UserRepository userRepository;
    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final OnlineOfflineService onlineOfflineService;
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

    @Override
    public NotificationToUser sendNotificationToUser(
            String userId,
            String subscription,
            NotificationToUser notificationToUser,
            SimpMessageHeaderAccessor headerAccessor
    ) {
        boolean isUserSubscribed =
                onlineOfflineService.isUserSubscribed(UUID.fromString(userId), "/topic/" + subscription);
        List<Message> messages = messageRepository.findBySenderIdAndReceiverId(
                subscription,
                UUID.fromString(userId),
                notificationToUser.getFriendId()
        );
        if(isUserSubscribed){
            for(Message message : messages){
                message.setDeliveryStatus(notificationToUser.getDeliveryStatus());
            }
            messageRepository.saveAll(messages);
            messagingTemplate.convertAndSend("/topic/notification/" + subscription, notificationToUser);
        }
        return notificationToUser;
    }
}
