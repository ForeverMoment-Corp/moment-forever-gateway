package com.forvmom.MomentForeverAPIGateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class MomentForeverApiGatewayApplication {

	public static void main(String[] args) {
		SpringApplication.run(MomentForeverApiGatewayApplication.class, args);
	}

}
