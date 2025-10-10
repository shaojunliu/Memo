package org.Memo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@EnableScheduling
@SpringBootApplication(scanBasePackages = "org.Memo")
public class MemoApplication {
    public static void main(String[] args) {
        // 设置 JVM 全局时区为东八区
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
        SpringApplication.run(MemoApplication.class, args);
    }
}
