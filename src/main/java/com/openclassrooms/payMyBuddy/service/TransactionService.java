package com.openclassrooms.payMyBuddy.service;

import com.openclassrooms.payMyBuddy.model.Transaction;
import com.openclassrooms.payMyBuddy.repository.TransactionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class TransactionService {

    @Autowired
    TransactionRepository transactionRepository;

    public Iterable<Transaction> getTransactions() {
        log.info("TransactionService.getTransactions - fetch all");
        return transactionRepository.findAll();
    }

    public Optional<Transaction> getTransactionById(Integer id) {
        if (id == null) {
            log.warn("TransactionService.getTransactionById - null id");
            throw new IllegalArgumentException("Id must not be null.");
        }
        log.info("TransactionService.getTransactionById - id={}", id);
        return transactionRepository.findById(id);
    }

    public Transaction save(Transaction tx) {
        log.info("TransactionService.save - saving transaction");
        Transaction saved = transactionRepository.save(tx);
        log.info("TransactionService.save - saved txId={}", saved.getId());
        return saved;
    }

    public List<Transaction> getSentByUser(Integer userId) {
        if (userId == null) {
            log.warn("TransactionService.getSentByUser - null userId");
            throw new IllegalArgumentException("User id must not be null.");
        }
        log.info("TransactionService.getSentByUser - userId={}", userId);
        List<Transaction> list = transactionRepository.findBySenderIdOrderByCreatedAtDesc(userId);
        log.info("TransactionService.getSentByUser - userId={} count={}", userId, list.size());
        return list;
    }

    public List<Transaction> getReceivedByUser(Integer userId) {
        if (userId == null) {
            log.warn("TransactionService.getReceivedByUser - null userId");
            throw new IllegalArgumentException("User id must not be null.");
        }
        log.info("TransactionService.getReceivedByUser - userId={}", userId);
        List<Transaction> list = transactionRepository.findByReceiverIdOrderByCreatedAtDesc(userId);
        log.info("TransactionService.getReceivedByUser - userId={} count={}", userId, list.size());
        return list;
    }

    public List<Transaction> getFeedForUser(Integer userId) {
        if (userId == null) {
            log.warn("TransactionService.getFeedForUser - null userId");
            throw new IllegalArgumentException("User id must not be null.");
        }
        log.info("TransactionService.getFeedForUser - userId={}", userId);
        List<Transaction> list = transactionRepository.findBySenderIdOrReceiverIdOrderByCreatedAtDesc(userId, userId);
        log.info("TransactionService.getFeedForUser - userId={} count={}", userId, list.size());
        return list;
    }
}