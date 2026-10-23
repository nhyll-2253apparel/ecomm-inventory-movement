package com._apparel.ecomm_inventory_movement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class EcommInventoryMovementApplication {

	public static void main(String[] args) {
		SpringApplication.run(EcommInventoryMovementApplication.class, args);
	}

}
