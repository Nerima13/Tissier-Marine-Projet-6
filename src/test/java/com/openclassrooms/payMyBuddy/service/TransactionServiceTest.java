package com.openclassrooms.payMyBuddy.service;

import com.openclassrooms.payMyBuddy.model.Transaction;
import com.openclassrooms.payMyBuddy.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private TransactionService transactionService;

    @Test
    public void getTransactions_returnsAllFromRepository() {
        Transaction t1 = new Transaction(); t1.setId(1);
        Transaction t2 = new Transaction(); t2.setId(2);
        List<Transaction> expected = List.of(t1, t2);

        when(transactionRepository.findAll()).thenReturn(expected);

        Iterable<Transaction> result = transactionService.getTransactions();

        verify(transactionRepository).findAll();
        assertIterableEquals(expected, result);
    }

    @Test
    public void getTransactionById_validId_returnsTransaction() {
        Transaction tx = new Transaction();
        tx.setId(42);

        when(transactionRepository.findById(42)).thenReturn(Optional.of(tx));

        Optional<Transaction> result = transactionService.getTransactionById(42);

        verify(transactionRepository).findById(42);
        assertTrue(result.isPresent());
        assertEquals(42, result.get().getId());
    }

    @Test
    public void getTransactionById_nullId_throwsException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> transactionService.getTransactionById(null));

        assertEquals("Id must not be null.", ex.getMessage());
        verifyNoInteractions(transactionRepository);
    }

    @Test
    public void save_callsRepositoryAndReturnsSavedTransaction() {
        Transaction input = new Transaction();
        input.setId(1);
        Transaction saved = new Transaction();
        saved.setId(1);

        when(transactionRepository.save(input)).thenReturn(saved);

        Transaction result = transactionService.save(input);

        verify(transactionRepository).save(input); // verifies the call
        assertNotNull(result);
        assertEquals(1, result.getId()); // verifies the return
    }

    @Test
    public void getSentByUser_validId_returnsTransactionsFromRepository() {
        Transaction t1 = new Transaction(); t1.setId(1);
        Transaction t2 = new Transaction(); t2.setId(2);
        List<Transaction> expected = List.of(t1, t2);

        when(transactionRepository.findBySenderIdOrderByCreatedAtDesc(42)).thenReturn(expected);

        List<Transaction> result = transactionService.getSentByUser(42);

        verify(transactionRepository).findBySenderIdOrderByCreatedAtDesc(42);
        assertEquals(expected, result);
        assertEquals(2, result.size());
        assertEquals(1, result.get(0).getId());
        assertEquals(2, result.get(1).getId());
    }

    @Test
    public void getSentByUser_nullId_throwsException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> transactionService.getSentByUser(null));

        assertEquals("User id must not be null.", ex.getMessage());
        verifyNoInteractions(transactionRepository);
    }

    @Test
    public void getReceivedByUser_validId_returnsTransactionsFromRepository() {
        Transaction t1 = new Transaction();
        t1.setId(21);
        Transaction t2 = new Transaction();
        t2.setId(42);
        List<Transaction> expected = List.of(t1, t2);

        when(transactionRepository.findByReceiverIdOrderByCreatedAtDesc(7)).thenReturn(expected);

        List<Transaction> result = transactionService.getReceivedByUser(7);

        verify(transactionRepository).findByReceiverIdOrderByCreatedAtDesc(7);
        assertEquals(expected, result);
        assertEquals(2, result.size());
        assertEquals(21, result.get(0).getId());
        assertEquals(42, result.get(1).getId());
    }

    @Test
    public void getReceivedByUser_nullId_throwsException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> transactionService.getReceivedByUser(null));

        assertEquals("User id must not be null.", ex.getMessage());
        verifyNoInteractions(transactionRepository);
    }

    @Test
    public void getFeedForUser_validId_returnsTransactionsFromRepository() {
        Transaction t1 = new Transaction();
        t1.setId(1);
        Transaction t2 = new Transaction();
        t2.setId(2);
        List<Transaction> expected = List.of(t1, t2);

        when(transactionRepository.findBySenderIdOrReceiverIdOrderByCreatedAtDesc(7, 7)).thenReturn(expected);

        List<Transaction> result = transactionService.getFeedForUser(7);

        verify(transactionRepository).findBySenderIdOrReceiverIdOrderByCreatedAtDesc(7, 7);

        assertEquals(expected, result);
        assertEquals(2, result.size());
        assertEquals(1, result.get(0).getId());
        assertEquals(2, result.get(1).getId());
    }

    @Test
    public void getFeedForUser_nullId_throwsException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> transactionService.getFeedForUser(null));

        assertEquals("User id must not be null.", ex.getMessage());
        verifyNoInteractions(transactionRepository);
    }
}
