package com.ridelink.driver;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
        "MONGODB_URI=mongodb://localhost:27017/ridelink_driver_test",
        "JWT_SECRET=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970",
        "ACCOUNT_SERVICE_URL=http://localhost:8081"
})
class DriverServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
