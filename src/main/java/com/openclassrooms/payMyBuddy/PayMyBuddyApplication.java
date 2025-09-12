package com.openclassrooms.payMyBuddy;

import com.openclassrooms.payMyBuddy.model.User;
import com.openclassrooms.payMyBuddy.service.TransactionService;
import com.openclassrooms.payMyBuddy.service.UserService;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Optional;

@SpringBootApplication
public class PayMyBuddyApplication implements CommandLineRunner {

    @Autowired
    private UserService userService;

    @Autowired
    private TransactionService transactionService;

	public static void main(String[] args) {
		SpringApplication.run(PayMyBuddyApplication.class, args);
	}

    @Override
    @Transactional
    public void run(String... args) throws Exception {

        Optional<User> optUser = userService.getUserById(1);
        User userId1 = optUser.get();
        System.out.println(userId1.getUsername());


    }
}
