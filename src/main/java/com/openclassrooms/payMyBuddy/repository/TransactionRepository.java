package com.openclassrooms.payMyBuddy.repository;

import com.openclassrooms.payMyBuddy.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Integer> {

    List<Transaction> findBySenderIdOrderByCreatedAtDesc(Integer senderId);

    List<Transaction> findByReceiverIdOrderByCreatedAtDesc(Integer receiverId);

    List<Transaction> findBySenderIdOrReceiverIdOrderByCreatedAtDesc(Integer senderId, Integer receiverId);

    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);
}
