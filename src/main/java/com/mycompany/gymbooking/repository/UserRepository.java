package com.mycompany.gymbooking.repository;

import com.mycompany.gymbooking.model.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * The "storage room door" for users.
 *
 * This is only an INTERFACE. We never write the class that implements it:
 * Spring Data reads the method names and writes the SQL for us at startup.
 *
 *   findByEmailIgnoreCase("a@b.com")  →  SELECT * FROM users WHERE LOWER(email) = LOWER('a@b.com')
 *
 * JpaRepository<User, Long> also gives us save(), findById(), findAll(), deleteById(), count()...
 */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);
}
