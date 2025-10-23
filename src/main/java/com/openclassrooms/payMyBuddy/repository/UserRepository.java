package com.openclassrooms.payMyBuddy.repository;

import com.openclassrooms.payMyBuddy.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Integer> {

    Optional<User> findByEmail(String email);

    Optional<User> findFirstByIsBankTrue();

    List<User> findByIsBankFalseAndEmailContainingIgnoreCase(String keyword);

}
