package com.dtrung.chatapp.repository;

import com.dtrung.chatapp.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);
    Optional<User> findByPhoneNumber(String phone);
    User findByUsername(String username);
    boolean existsByEmail(String email);
    boolean existsByPhoneNumber(String phone);
    boolean existsByUsername(String username);
    @Query("""
        SELECT u
        FROM User u
        WHERE u.id <> :currentUserId
          AND (
            :search IS NULL
            OR trim(:search) = ''
            OR lower(u.username) LIKE lower(concat('%', :search, '%'))
          )
        ORDER BY u.username ASC
    """)
    List<User> searchUsers(@Param("currentUserId") UUID currentUserId, @Param("search") String search);
}
