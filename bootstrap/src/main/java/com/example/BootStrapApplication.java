package com.example;

import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
@EntityScan(basePackages = "com.example")
@EnableJpaRepositories(basePackages = "com.example")
@SpringBootApplication(scanBasePackages = "com.example")
@EnableSpringDataWebSupport
public class BootStrapApplication {

	public static void main(String[] args) {
		SpringApplication.run(BootStrapApplication.class, args);
	}
}
