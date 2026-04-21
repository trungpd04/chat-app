package com.dtrung.chatapp.repository;

import com.dtrung.chatapp.model.FriendShip;
import com.dtrung.chatapp.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface FriendShipRepository extends JpaRepository<FriendShip, UUID> {

    @Query("SELECT f from FriendShip f where (f.sender.id = :userId " +
            "or f.receiver.id = :userId) and f.status = 'ACCEPTED'")
    List<FriendShip> findAllByUserId(@Param("userId") UUID userId);

    @Query("SELECT f from FriendShip f where f.receiver.id = :receiverId and f.status = 'PENDING' " +
            "order by f.createdAt desc")
    List<FriendShip> findPendingRequestsByReceiverId(@Param("receiverId") UUID receiverId);

    boolean existsBySenderAndReceiver(User sender, User receiver);

    @Query("SELECT f from FriendShip f where f.id = :id and f.receiver.id = :receiverId ")
    FriendShip findByIdAndReceiverId(UUID id, UUID receiverId);
}
