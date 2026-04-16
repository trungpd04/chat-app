package com.dtrung.chatapp.controller;

import com.dtrung.chatapp.model.Message;
import com.dtrung.chatapp.model.NotificationToUser;
import com.dtrung.chatapp.repository.MessageRepository;
import com.dtrung.chatapp.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
@RequiredArgsConstructor
@RequestMapping("/chats")
public class ChatController {

    private final ChatService chatService;

    @MessageMapping("/chat/sendMessage/{convId}")
    public ResponseEntity<Message> sendMessage(
            @Payload Message message,
            @DestinationVariable String convId,
            SimpMessageHeaderAccessor headerAccessor
    ) {
        return ResponseEntity.ok(chatService.sendMessage(convId, message, headerAccessor));
    }
    @MessageMapping("/chat/notification/{convId}")
    public ResponseEntity<NotificationToUser> sendNotification(
            @Payload NotificationToUser notification,
            @DestinationVariable String convId,
            SimpMessageHeaderAccessor headerAccessor
    ) {
        return ResponseEntity.ok(
                chatService.sendNotificationToUser(
                        notification.getFriendId().toString(),
                        convId,
                        notification,
                        headerAccessor
                ));
    }

    @GetMapping("")
    public ResponseEntity<List<Message>> getAllMessages(
            @RequestParam(name = "convId") String conversationId
    ) {
        return ResponseEntity.ok(chatService.getMessages(conversationId));
    }
}
