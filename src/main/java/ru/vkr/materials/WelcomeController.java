package ru.vkr.materials;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WelcomeController {
    @GetMapping("/api/info")
    public Map<String, String> info() {
        return Map.of("name", "Система автоматизированного учета материалов в медицинском учреждении", "version", "0.0.1");
    }
}
