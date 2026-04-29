package com.fan.lazyday;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"spring.flyway.enabled=false",
		"lazyday.partition.bootstrap.enabled=false"
})
class LazydayApplicationTests {

	@Test
	void contextLoads() {
	}

}
