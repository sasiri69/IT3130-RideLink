package com.ridelink.account;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "MONGODB_URI=mongodb://localhost:27017/ridelink_account_test",
    "JWT_SECRET=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970",
    "JWT_EXPIRATION=86400000",
    "JWT_REFRESH_EXPIRATION=604800000"
})
class AccountServiceApplicationTests {

	@Test
	void contextLoads() {
	}
}
