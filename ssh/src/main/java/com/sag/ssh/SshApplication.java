package com.sag.ssh;

import org.springframework.boot.SpringApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.cumulocity.microservice.autoconfigure.MicroserviceApplication;
import com.cumulocity.microservice.security.annotation.EnableMicroserviceSecurity;

@MicroserviceApplication
@EnableScheduling
@EnableMicroserviceSecurity
public class SshApplication {

	public static void main(String[] args) {
		SpringApplication.run(SshApplication.class, args);
	}
}
