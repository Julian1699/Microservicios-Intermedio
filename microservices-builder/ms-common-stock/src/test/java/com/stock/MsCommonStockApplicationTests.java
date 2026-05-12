package com.stock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "eureka.client.enabled=false")
class MsCommonStockApplicationTests {

	@Test
	void contextLoads() {
	}

}
