package com.dtrung.chatapp.service;

import com.dtrung.chatapp.model.Message;
import com.dtrung.chatapp.model.NotificationToUser;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;

import java.util.List;

public interface ChatService {
    Message sendMessage(String conversationId,
                        Message message,
                        SimpMessageHeaderAccessor headerAccessor
    );
    List<Message> getMessages(String conversationId);
    NotificationToUser sendNotificationToUser(
            String userId,
            String conversationId,
            NotificationToUser notificationToUser,
            SimpMessageHeaderAccessor headerAccessor
    );
    void sendMessageSeenStatusToSenderUser(String senderUserId, String conversationId);
}
