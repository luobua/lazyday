package com.fan.lazyday;

import com.fan.lazyday.infrastructure.scheduler.PartitionScheduler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class LazydayApplication {

	public static void main(String[] args) {
		SpringApplication.run(LazydayApplication.class, args);
	}

	@Bean
	@ConditionalOnProperty(name = "lazyday.partition.bootstrap.enabled", havingValue = "true", matchIfMissing = true)
	ApplicationRunner partitionBootstrapRunner(PartitionScheduler partitionScheduler) {
		return args -> partitionScheduler.ensureNextTwoMonthsPartitions();
	}

}
