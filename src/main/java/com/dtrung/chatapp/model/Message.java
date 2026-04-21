package com.dtrung.chatapp.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Message {

    @Id
            @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @Column(name = "from_user")
    UUID fromUser;

    @Column(name = "to_user")
    UUID toUser;

    @Enumerated(EnumType.STRING)
    MessageType messageType;

    @Enumerated(EnumType.STRING)
    MessageDeliveryStatus deliveryStatus;

    @Column(columnDefinition = "TEXT")
    String content;

    @ManyToOne(fetch = FetchType.EAGER)
            @JsonIgnore
            Conversation conversation;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    LocalDateTime sendTime;
}
