package ru.vkr.materials;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MaterialsApplication {
    public static void main(String[] args) {
        var context = SpringApplication.run(MaterialsApplication.class, args);
        if (java.util.Arrays.asList(context.getEnvironment().getActiveProfiles()).contains("bootstrap-admin")) {
            context.close();
        }
    }
}
