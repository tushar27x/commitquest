package com.tushar27x.insightshub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class InsightshubApplication {

	public static void main(String[] args) {
		SpringApplication.run(InsightshubApplication.class, args);
	}

}
