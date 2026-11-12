package com.example.java_mind_agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class JavaMindAgentApplication {

	public static void main(String[] args) {
		SpringApplication.run(JavaMindAgentApplication.class, args);
	}

}
