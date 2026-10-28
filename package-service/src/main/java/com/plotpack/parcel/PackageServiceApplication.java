package com.plotpack.parcel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.plotpack.parcel", "com.plotpack.common.web", "com.plotpack.common.outbox"})
@EntityScan({"com.plotpack.parcel", "com.plotpack.common.outbox"})
@EnableJpaRepositories({"com.plotpack.parcel", "com.plotpack.common.outbox"})
@EnableScheduling
public class PackageServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(PackageServiceApplication.class, args);
    }
}
