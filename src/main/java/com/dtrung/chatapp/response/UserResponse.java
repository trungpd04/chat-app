package com.dtrung.chatapp.response;

import com.dtrung.chatapp.model.User;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Builder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserResponse {
    String userId;
    String username;
    String userEmail;
    String avatar;
    boolean isFriend;

    static UserResponse fromUser(User user) {
        return UserResponse.builder()
                .userId(user.getId().toString())
                .username(user.getUsername())
                .avatar(user.getAvatar())
                .userEmail(user.getEmail())
                .build();
    }
}
