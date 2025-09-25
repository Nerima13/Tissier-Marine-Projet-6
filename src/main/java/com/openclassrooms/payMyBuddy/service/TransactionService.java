package com.openclassrooms.payMyBuddy.service;

import com.openclassrooms.payMyBuddy.model.Transaction;
import com.openclassrooms.payMyBuddy.repository.TransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class TransactionService {

    @Autowired
    TransactionRepository transactionRepository;

    public Iterable<Transaction> getTransactions() {
        return transactionRepository.findAll();
    }

    public Optional<Transaction> getTransactionById(Integer id) {
        if (id == null) {
            throw new IllegalArgumentException("Id must not be null.");
        }
        return transactionRepository.findById(id);
    }

    public Transaction save(Transaction tx) {
        return transactionRepository.save(tx);
    }

    public List<Transaction> getSentByUser(Integer userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User id must not be null.");
        }
        return transactionRepository.findBySenderIdOrderByCreatedAtDesc(userId);
    }

    public List<Transaction> getReceivedByUser(Integer userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User id must not be null.");
        }
        return transactionRepository.findByReceiverIdOrderByCreatedAtDesc(userId);
    }

    public List<Transaction> getFeedForUser(Integer userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User id must not be null.");
        }
        return transactionRepository.findBySenderIdOrReceiverIdOrderByCreatedAtDesc(userId, userId);
    }
}
