package com.openclassrooms.payMyBuddy;

import com.openclassrooms.payMyBuddy.controller.LoginController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@SpringBootTest
class PayMyBuddyApplicationTests {

    @Autowired
    private LoginController loginController;

	@Test
	void contextLoads() {
        assertThat(loginController).isNotNull();
	}

}
