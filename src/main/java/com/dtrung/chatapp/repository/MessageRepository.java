package com.dtrung.chatapp.repository;

import com.dtrung.chatapp.model.Message;
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
}
