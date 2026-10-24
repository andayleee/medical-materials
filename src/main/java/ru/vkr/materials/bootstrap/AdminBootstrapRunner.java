package ru.vkr.materials.bootstrap;

import java.io.Console;
import java.util.Arrays;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("bootstrap-admin")
public class AdminBootstrapRunner implements ApplicationRunner {
    private final AdminCreationService admins;

    public AdminBootstrapRunner(AdminCreationService admins) {
        this.admins = admins;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        Console console = System.console();
        if (console == null) {
            throw new IllegalStateException(
                    "Запустите scripts/create-admin.sh в обычном терминале со скрытым вводом пароля");
        }
        String login = console.readLine("Логин администратора: ");
        String fullName = console.readLine("Имя / ФИО: ");
        if (login == null || fullName == null) {
            throw new IllegalStateException("Создание отменено");
        }
        char[] password = console.readPassword("Пароль (от 10 символов): ");
        char[] confirmation = null;
        try {
            confirmation = console.readPassword("Повторите пароль: ");
            if (password == null || confirmation == null || !Arrays.equals(password, confirmation)) {
                throw new IllegalArgumentException("Пароли не совпадают или ввод отменён");
            }
            admins.createFirstAdmin(login, fullName, password);
            console.printf("Администратор создан. Теперь запустите ./scripts/run.sh%n");
        } finally {
            if (password != null) {
                Arrays.fill(password, '\0');
            }
            if (confirmation != null) {
                Arrays.fill(confirmation, '\0');
            }
        }
    }
}
