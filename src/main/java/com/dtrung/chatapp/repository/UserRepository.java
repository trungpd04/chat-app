package com.dtrung.chatapp.repository;

import com.dtrung.chatapp.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

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
        SELECT u from User u where u.username like %?1%
    """)
    List<User> findAllByUsernameLike(String username);
}
