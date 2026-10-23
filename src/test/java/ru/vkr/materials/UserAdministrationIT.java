package ru.vkr.materials;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import ru.vkr.materials.model.*;
import ru.vkr.materials.repository.*;
import java.time.LocalDate;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class UserAdministrationIT {
    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired DepartmentRepository departments;
    @Autowired WarehouseRepository warehouses;
    @Autowired InventoryDocumentRepository documents;
    @Autowired PasswordEncoder passwords;
    MockMvc mvc;
    User admin;
    private static final String PASSWORD = "test-admin-password-2026";
    private static final String TEMP = "temporary-password-2026";
    private static final String OWN = "my-own-password-2026";

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        admin = account("users-test-admin", UserRole.ADMIN, false);
    }
    private User account(String login, UserRole role, boolean temporary) {
        User user = new User(); user.setLogin(login); user.setFullName("Тест " + login);
        user.setPasswordHash(passwords.encode(temporary ? TEMP : PASSWORD));
        user.setRole(role); user.setPasswordChangeRequired(temporary);
        return users.saveAndFlush(user);
    }
    private MockHttpSession login(String name, String password) throws Exception {
        return (MockHttpSession) mvc.perform(formLogin().user(name).password(password))
                .andExpect(authenticated()).andReturn().getRequest().getSession(false);
    }
    private Long departmentId() {
        return departments.findAll().stream().filter(d -> d.getName().equals("ВЭЖХ")).findFirst().orElseThrow().getId();
    }

    @Test
    void onlyAdminCanReadCreateEditAndDeleteUsers() throws Exception {
        User recipient = account("users-recipient", UserRole.RECIPIENT, false);
        MockHttpSession session = login(recipient.getLogin(), PASSWORD);
        for (String path : new String[]{"/admin/users", "/admin/users/new", "/admin/users/" + admin.getId() + "/edit", "/admin/users/" + admin.getId() + "/delete"}) {
            mvc.perform(get(path).session(session)).andExpect(status().isForbidden());
        }
        mvc.perform(post("/admin/users").session(session).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/admin/users/" + admin.getId()).session(session).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/admin/users/" + admin.getId() + "/delete").session(session).with(csrf()).param("version", "0"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanCreateSearchAndEditWithoutChangingPassword() throws Exception {
        MockHttpSession session = login(admin.getLogin(), PASSWORD);
        mvc.perform(get("/dashboard").session(session)).andExpect(content().string(containsString("Управление пользователями")));
        mvc.perform(get("/admin/users/new").session(session)).andExpect(status().isOk());
        mvc.perform(post("/admin/users").session(session).with(csrf())
                .param("login", "NEW-LAB-USER").param("fullName", "Ирина Лабораторная").param("role", "RECIPIENT")
                .param("departmentId", departmentId().toString()).param("active", "true")
                .param("temporaryPassword", TEMP).param("version", "0"))
                .andExpect(redirectedUrl("/admin/users"));
        User created = users.findByLoginIgnoreCase("new-lab-user").orElseThrow();
        assertTrue(created.isPasswordChangeRequired()); assertTrue(passwords.matches(TEMP, created.getPasswordHash()));
        String hash = created.getPasswordHash();
        mvc.perform(get("/admin/users").session(session).param("q", "Ирина"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("new-lab-user")))
                .andExpect(content().string(not(containsString(hash))));
        mvc.perform(get("/admin/users/" + created.getId() + "/edit").session(session))
                .andExpect(status().isOk()).andExpect(content().string(not(containsString(TEMP))));
        mvc.perform(post("/admin/users/" + created.getId()).session(session).with(csrf())
                .param("login", created.getLogin()).param("fullName", "Новое имя").param("role", "RECIPIENT")
                .param("departmentId", departmentId().toString()).param("active", "true")
                .param("temporaryPassword", "").param("version", Long.toString(created.getSecurityVersion())))
                .andExpect(redirectedUrl("/admin/users"));
        assertEquals(hash, users.findById(created.getId()).orElseThrow().getPasswordHash());
        assertEquals("Новое имя", created.getFullName());
    }

    @Test
    void duplicateLoginAndMissingDepartmentAreExplainedWithoutEchoingPassword() throws Exception {
        MockHttpSession session = login(admin.getLogin(), PASSWORD);
        mvc.perform(post("/admin/users").session(session).with(csrf())
                .param("login", admin.getLogin()).param("fullName", "Дубликат").param("role", "ADMIN")
                .param("temporaryPassword", TEMP).param("active", "true"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Этот логин уже занят")))
                .andExpect(content().string(not(containsString(TEMP))));
        mvc.perform(post("/admin/users").session(session).with(csrf())
                .param("login", "no-department").param("fullName", "Получатель").param("role", "RECIPIENT")
                .param("temporaryPassword", TEMP).param("active", "true"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("выберите подразделение")));
        assertTrue(users.findByLoginIgnoreCase("no-department").isEmpty());
    }

    @Test
    void temporaryPasswordBlocksEveryInternalPageUntilItIsReplaced() throws Exception {
        User user = account("temporary-admin", UserRole.ADMIN, true);
        MockHttpSession session = login(user.getLogin(), TEMP);
        for (String path : new String[]{"/dashboard", "/admin/users", "/api/info", "/"}) {
            mvc.perform(get(path).session(session)).andExpect(redirectedUrl("/change-password"));
        }
        mvc.perform(post("/admin/users").session(session).with(csrf()))
                .andExpect(redirectedUrl("/change-password"));
        mvc.perform(get("/change-password").session(session)).andExpect(status().isOk());
        mvc.perform(post("/change-password").session(session).with(csrf())
                .param("currentPassword", TEMP).param("newPassword", OWN).param("confirmation", OWN))
                .andExpect(redirectedUrl("/login?passwordChanged"));
        assertTrue(session.isInvalid()); assertFalse(user.isPasswordChangeRequired());
        assertTrue(passwords.matches(OWN, user.getPasswordHash()));
        mvc.perform(formLogin().user(user.getLogin()).password(TEMP)).andExpect(unauthenticated());
        MockHttpSession newSession = login(user.getLogin(), OWN);
        mvc.perform(get("/admin/users").session(newSession)).andExpect(status().isOk());
    }

    @Test
    void passwordChangeRejectsWrongCurrentSamePasswordAndMismatch() throws Exception {
        User user = account("temporary-recipient", UserRole.RECIPIENT, true);
        MockHttpSession session = login(user.getLogin(), TEMP);
        for (String[] input : new String[][]{{"wrong", OWN, OWN}, {TEMP, TEMP, TEMP}, {TEMP, OWN, "different"}, {TEMP, "short", "short"}}) {
            mvc.perform(post("/change-password").session(session).with(csrf())
                    .param("currentPassword", input[0]).param("newPassword", input[1]).param("confirmation", input[2]))
                    .andExpect(status().isOk()).andExpect(content().string(containsString("role=\"alert\"")))
                    .andExpect(content().string(not(containsString(TEMP))));
        }
        assertTrue(user.isPasswordChangeRequired()); assertTrue(passwords.matches(TEMP, user.getPasswordHash()));
        mvc.perform(post("/change-password").session(session).param("currentPassword", TEMP)
                .param("newPassword", OWN).param("confirmation", OWN)).andExpect(status().isForbidden());
    }

    @Test
    void adminPasswordResetRevokesOldSessionAndForcesAnotherChange() throws Exception {
        User user = account("reset-target", UserRole.STOREKEEPER, false);
        MockHttpSession previous = login(user.getLogin(), PASSWORD);
        MockHttpSession adminSession = login(admin.getLogin(), PASSWORD);
        mvc.perform(post("/admin/users/" + user.getId()).session(adminSession).with(csrf())
                .param("login", user.getLogin()).param("fullName", user.getFullName()).param("role", "STOREKEEPER")
                .param("active", "true").param("temporaryPassword", TEMP).param("version", "0"))
                .andExpect(redirectedUrl("/admin/users"));
        mvc.perform(get("/dashboard").session(previous)).andExpect(redirectedUrl("/login?accountChanged"));
        mvc.perform(formLogin().user(user.getLogin()).password(PASSWORD)).andExpect(unauthenticated());
        MockHttpSession temporary = login(user.getLogin(), TEMP);
        mvc.perform(get("/dashboard").session(temporary)).andExpect(redirectedUrl("/change-password"));
    }

    @Test
    void deleteRequiresConfirmationAndCsrfAndRevokesTheDeletedAccountSession() throws Exception {
        User user = account("delete-target", UserRole.RECIPIENT, false);
        MockHttpSession userSession = login(user.getLogin(), PASSWORD);
        MockHttpSession adminSession = login(admin.getLogin(), PASSWORD);
        String url = "/admin/users/" + user.getId() + "/delete";
        mvc.perform(get(url).session(adminSession)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Удалить пользователя?")));
        assertTrue(users.existsById(user.getId()));
        mvc.perform(post(url).session(adminSession).param("version", "0")).andExpect(status().isForbidden());
        mvc.perform(post(url).session(adminSession).with(csrf()).param("version", "0"))
                .andExpect(redirectedUrl("/admin/users"));
        assertFalse(users.existsById(user.getId()));
        mvc.perform(get("/dashboard").session(userSession)).andExpect(redirectedUrl("/login?accountChanged"));
    }

    @Test
    void selfDeletionAndSelfDeactivationAreRejected() throws Exception {
        MockHttpSession session = login(admin.getLogin(), PASSWORD);
        mvc.perform(post("/admin/users/" + admin.getId() + "/delete").session(session).with(csrf()).param("version", "0"))
                .andExpect(flash().attribute("error", "Нельзя удалить собственную учётную запись."));
        mvc.perform(post("/admin/users/" + admin.getId()).session(session).with(csrf())
                .param("login", admin.getLogin()).param("fullName", admin.getFullName()).param("role", "RECIPIENT")
                .param("active", "false").param("version", "0"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Нельзя отключить себя")));
        assertTrue(admin.isActive()); assertEquals(UserRole.ADMIN, admin.getRole());
    }

    @Test
    void usersReferencedByDocumentsCannotBeDeleted() throws Exception {
        User author = account("document-author", UserRole.STOREKEEPER, false);
        InventoryDocument document = new InventoryDocument(); document.setAuthor(author);
        document.setWarehouse(warehouses.findAll().getFirst()); document.setType(DocumentType.WRITE_OFF);
        document.setWriteOffReason("Тест"); document.setNumber("TEST"); document.setDocumentDate(LocalDate.now());
        documents.saveAndFlush(document);
        mvc.perform(post("/admin/users/" + author.getId() + "/delete").session(login(admin.getLogin(), PASSWORD))
                .with(csrf()).param("version", "0"))
                .andExpect(flash().attribute("error", "Пользователь связан с документами. Отключите его вместо удаления."));
        assertTrue(users.existsById(author.getId()));
    }

    @Test
    void roleChangeAndDeactivationRevokeExistingSessions() throws Exception {
        User user = account("role-change-target", UserRole.ADMIN, false);
        MockHttpSession previous = login(user.getLogin(), PASSWORD);
        MockHttpSession adminSession = login(admin.getLogin(), PASSWORD);
        String url = "/admin/users/" + user.getId();
        mvc.perform(post(url).session(adminSession).with(csrf()).param("login", user.getLogin())
                .param("fullName", user.getFullName()).param("role", "RECIPIENT")
                .param("departmentId", departmentId().toString()).param("active", "true").param("version", "0"))
                .andExpect(redirectedUrl("/admin/users"));
        mvc.perform(get("/admin/users").session(previous)).andExpect(redirectedUrl("/login?accountChanged"));
        MockHttpSession recipient = login(user.getLogin(), PASSWORD);
        mvc.perform(get("/admin/users").session(recipient)).andExpect(status().isForbidden());
        mvc.perform(post(url).session(adminSession).with(csrf()).param("login", user.getLogin())
                .param("fullName", user.getFullName()).param("role", "RECIPIENT")
                .param("departmentId", departmentId().toString()).param("active", "false")
                .param("version", Long.toString(user.getSecurityVersion())))
                .andExpect(redirectedUrl("/admin/users"));
        mvc.perform(get("/dashboard").session(recipient)).andExpect(redirectedUrl("/login?accountChanged"));
        mvc.perform(formLogin().user(user.getLogin()).password(PASSWORD)).andExpect(unauthenticated());
    }

    @Test
    void staleEditCannotOverwriteMoreRecentChanges() throws Exception {
        User user = account("stale-target", UserRole.STOREKEEPER, false);
        MockHttpSession adminSession = login(admin.getLogin(), PASSWORD);
        String url = "/admin/users/" + user.getId();
        mvc.perform(post(url).session(adminSession).with(csrf()).param("login", user.getLogin())
                .param("fullName", "Первое изменение").param("role", "STOREKEEPER").param("active", "true")
                .param("version", "0")).andExpect(redirectedUrl("/admin/users"));
        mvc.perform(post(url).session(adminSession).with(csrf()).param("login", user.getLogin())
                .param("fullName", "Устаревшее изменение").param("role", "STOREKEEPER").param("active", "true")
                .param("version", "0")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Запись уже изменена")));
        assertEquals("Первое изменение", user.getFullName());
    }
}
