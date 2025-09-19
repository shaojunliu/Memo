package org.Memo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "org.Memo")
public class MemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(MemoApplication.class, args);
    }
}
