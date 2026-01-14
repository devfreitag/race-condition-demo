package com.example.banking;

import com.example.banking.entity.Account;
import com.example.banking.repository.AccountRepository;
import com.example.banking.service.TransferService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class RaceConditionTest {
    
    @Autowired
    private TransferService transferService;
    
    @Autowired
    private AccountRepository accountRepository;
    
    @BeforeEach
    void setup() {
        accountRepository.deleteAll();
        accountRepository.save(new Account("A", new BigDecimal("100")));
        accountRepository.save(new Account("B", new BigDecimal("0")));
        accountRepository.save(new Account("C", new BigDecimal("0")));
    }
    
    /**
     * TESTE 1 - DEMONSTRA O PROBLEMA (RACE CONDITION)
     * 
     * Cenário:
     * - Conta A tem R$100
     * - Thread 1 tenta transferir R$80 para B
     * - Thread 2 tenta transferir R$80 para C
     * 
     * Resultado esperado: Uma transferência deveria falhar (saldo insuficiente)
     * Resultado real: Ambas podem passar, deixando saldo negativo!
     */
    @Test
    void test_RaceCondition_Unsafe_ProblemaVisivel() throws InterruptedException {
        System.out.println("\n=== TESTE: UNSAFE (COM RACE CONDITION) ===");
        
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(2);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        
        // Thread 1
        executor.submit(() -> {
            try {
                transferService.transferUnsafe("A", "B", new BigDecimal("80"));
                successCount.incrementAndGet();
                System.out.println("Thread 1: transferência OK");
            } catch (Exception e) {
                failureCount.incrementAndGet();
                System.out.println("Thread 1: transferência FALHOU - " + e.getMessage());
            } finally {
                latch.countDown();
            }
        });
        
        // Thread 2
        executor.submit(() -> {
            try {
                transferService.transferUnsafe("A", "C", new BigDecimal("80"));
                successCount.incrementAndGet();
                System.out.println("Thread 2: transferência OK");
            } catch (Exception e) {
                failureCount.incrementAndGet();
                System.out.println("Thread 2: transferência FALHOU - " + e.getMessage());
            } finally {
                latch.countDown();
            }
        });
        
        latch.await();
        executor.shutdown();
        
        Account accountA = accountRepository.findById("A").get();
        Account accountB = accountRepository.findById("B").get();
        Account accountC = accountRepository.findById("C").get();
        
        System.out.println("Resultado:");
        System.out.println("- Conta A: " + accountA.getBalance() + " (esperado: 20 ou mais)");
        System.out.println("- Conta B: " + accountB.getBalance());
        System.out.println("- Conta C: " + accountC.getBalance());
        System.out.println("- Transferências bem-sucedidas: " + successCount.get());
        System.out.println("- Transferências que falharam: " + failureCount.get());
        
        // ATENÇÃO: Este teste pode falhar de forma intermitente
        // Isso é proposital - demonstra o race condition!
        // Às vezes ambas as transferências passam e o saldo fica negativo
        
        if (accountA.getBalance().compareTo(BigDecimal.ZERO) < 0) {
            System.out.println("⚠️  RACE CONDITION DETECTADO! Saldo ficou negativo!");
        }
    }
    
    /**
     * TESTE 2 - OPTIMISTIC LOCKING
     * 
     * Com retry, garante que apenas uma transferência passa.
     * Se houver conflito, tenta novamente.
     */
    @Test
    void test_OptimisticLocking_ComRetry() throws InterruptedException {
        System.out.println("\n=== TESTE: OPTIMISTIC LOCKING (COM RETRY) ===");
        
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(2);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        
        executor.submit(() -> {
            try {
                transferService.transferOptimisticWithRetry("A", "B", new BigDecimal("80"));
                successCount.incrementAndGet();
                System.out.println("Thread 1: transferência OK");
            } catch (Exception e) {
                failureCount.incrementAndGet();
                System.out.println("Thread 1: transferência FALHOU - " + e.getMessage());
            } finally {
                latch.countDown();
            }
        });
        
        executor.submit(() -> {
            try {
                transferService.transferOptimisticWithRetry("A", "C", new BigDecimal("80"));
                successCount.incrementAndGet();
                System.out.println("Thread 2: transferência OK");
            } catch (Exception e) {
                failureCount.incrementAndGet();
                System.out.println("Thread 2: transferência FALHOU - " + e.getMessage());
            } finally {
                latch.countDown();
            }
        });
        
        latch.await();
        executor.shutdown();
        
        Account accountA = accountRepository.findById("A").get();
        Account accountB = accountRepository.findById("B").get();
        Account accountC = accountRepository.findById("C").get();
        
        System.out.println("Resultado:");
        System.out.println("- Conta A: " + accountA.getBalance());
        System.out.println("- Conta B: " + accountB.getBalance());
        System.out.println("- Conta C: " + accountC.getBalance());
        System.out.println("- Transferências bem-sucedidas: " + successCount.get());
        System.out.println("- Transferências que falharam: " + failureCount.get());
        
        // Com optimistic + retry, garante consistência
        assertEquals(0, accountA.getBalance().compareTo(new BigDecimal("20")));
        assertEquals(1, successCount.get()); // apenas uma passou
        assertEquals(1, failureCount.get()); // outra falhou
        System.out.println("✅ Consistência garantida!");
    }
    
    /**
     * TESTE 3 - PESSIMISTIC LOCKING
     * 
     * Trava as contas, garantindo que não há race condition.
     * Não precisa de retry.
     */
    @Test
    void test_PessimisticLocking_SemRaceCondition() throws InterruptedException {
        System.out.println("\n=== TESTE: PESSIMISTIC LOCKING ===");
        
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(2);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        
        executor.submit(() -> {
            try {
                transferService.transferPessimistic("A", "B", new BigDecimal("80"));
                successCount.incrementAndGet();
                System.out.println("Thread 1: transferência OK");
            } catch (Exception e) {
                failureCount.incrementAndGet();
                System.out.println("Thread 1: transferência FALHOU - " + e.getMessage());
            } finally {
                latch.countDown();
            }
        });
        
        executor.submit(() -> {
            try {
                transferService.transferPessimistic("A", "C", new BigDecimal("80"));
                successCount.incrementAndGet();
                System.out.println("Thread 2: transferência OK");
            } catch (Exception e) {
                failureCount.incrementAndGet();
                System.out.println("Thread 2: transferência FALHOU - " + e.getMessage());
            } finally {
                latch.countDown();
            }
        });
        
        latch.await();
        executor.shutdown();
        
        Account accountA = accountRepository.findById("A").get();
        Account accountB = accountRepository.findById("B").get();
        Account accountC = accountRepository.findById("C").get();
        
        System.out.println("Resultado:");
        System.out.println("- Conta A: " + accountA.getBalance());
        System.out.println("- Conta B: " + accountB.getBalance());
        System.out.println("- Conta C: " + accountC.getBalance());
        System.out.println("- Transferências bem-sucedidas: " + successCount.get());
        System.out.println("- Transferências que falharam: " + failureCount.get());
        
        // Com pessimistic, garante consistência sem retry
        assertEquals(0, accountA.getBalance().compareTo(new BigDecimal("20")));
        assertEquals(1, successCount.get()); // apenas uma passou
        assertEquals(1, failureCount.get()); // outra falhou por saldo insuficiente
        System.out.println("✅ Consistência garantida com pessimistic lock!");
    }
}
