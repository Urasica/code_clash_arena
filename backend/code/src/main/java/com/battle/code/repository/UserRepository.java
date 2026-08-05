package com.battle.code.repository;

import com.battle.code.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);

    @Modifying
    @Query("""
            delete from User u
            where u.role = com.battle.code.domain.User.Role.GUEST
              and u.createdAt < :cutoff
              and not exists (select mp.id from MatchPlayer mp where mp.user = u)
            """)
    int deleteUnreferencedGuestsCreatedBefore(@Param("cutoff") LocalDateTime cutoff);
}
