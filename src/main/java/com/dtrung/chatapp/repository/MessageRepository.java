package com.dtrung.chatapp.repository;

import com.dtrung.chatapp.model.Message;
import com.dtrung.chatapp.model.MessageDeliveryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    @Query("SELECT m from Message m where m.conversation.convId = :conversationId " +
            "order by m.sendTime asc ")
    List<Message> findByConversationId(@Param("conversationId") String conversationId);

    @Query("SELECT m from Message m where m.conversation.convId = :conversationId " +
            "and (m.fromUser = :fromUser or m.toUser = :toUser) " +
            "order by m.sendTime asc ")
    List<Message> findBySenderIdAndReceiverId(
            @Param("conversationId") String conversationId,
            @Param("fromUser") UUID fromUser,
            @Param("toUser") UUID toUser
    );

    @Query("SELECT m from Message m where m.conversation.convId = :conversationId " +
            "and (m.fromUser = :fromUser) " +
            "order by m.sendTime asc ")
    List<Message> findBySenderIdAndConversationId(
            @Param("conversationId") String conversationId,
            @Param("fromUser") UUID fromUser
    );

    @Query("""
        SELECT DISTINCT m.fromUser
        FROM Message m
        WHERE m.toUser = :currentUserId
          AND m.fromUser IN :friends
          AND ( m.deliveryStatus = "DELIVERED" or m.deliveryStatus = "NOT_DELIVERED")
    """)
    List<UUID> findFriendsWithUnreadMessages(
            @Param("currentUserId") UUID currentUserId,
            @Param("friends") List<UUID> friends
    );
}
