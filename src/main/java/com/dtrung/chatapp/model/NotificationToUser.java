package com.dtrung.chatapp.model;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.util.List;
import java.util.UUID;

@Builder
@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class NotificationToUser {
    UUID friendId;
    String message;
    MessageDeliveryStatus deliveryStatus;
    FriendStatus friendStatus;
    String friendUsername;
    boolean unread;
    List<UUID> unreadFriends;
}
