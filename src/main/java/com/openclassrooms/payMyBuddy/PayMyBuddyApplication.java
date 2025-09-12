package com.openclassrooms.payMyBuddy;

import com.openclassrooms.payMyBuddy.model.User;
import com.openclassrooms.payMyBuddy.service.TransactionService;
import com.openclassrooms.payMyBuddy.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

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
    public void run(String... args) throws Exception {

        Iterable<User> users = userService.getUsers();
        users.forEach(user -> System.out.println(user.getUsername()));
    }
}
