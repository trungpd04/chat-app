package com.dtrung.chatapp.model;

import java.util.Locale;

public enum FriendshipStatus {
    PENDING, ACCEPTED, BLOCKED, DECLINE;

    public static FriendshipStatus fromRequestValue(String value) {
        String normalizedValue = value.trim().toUpperCase(Locale.ROOT);
        if ("ACCEPT".equals(normalizedValue)) {
            return ACCEPTED;
        }
        return FriendshipStatus.valueOf(normalizedValue);
    }
}
