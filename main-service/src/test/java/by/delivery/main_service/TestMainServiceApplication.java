package by.delivery.main_service;

import org.springframework.boot.SpringApplication;

public class TestMainServiceApplication {

	public static void main(String[] args) {
		SpringApplication.from(MainServiceApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
