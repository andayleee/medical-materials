package ru.vkr.materials;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import ru.vkr.materials.bootstrap.AdminCreationService;
import ru.vkr.materials.model.User;
import ru.vkr.materials.model.UserRole;
import ru.vkr.materials.repository.UserRepository;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class AuthenticationIT {
    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired AdminCreationService admins;
    MockMvc mvc;
    private static final String PASSWORD = "test-password-only-2026";

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        User user = new User(); user.setLogin("auth-test"); user.setFullName("Тестовый сотрудник");
        user.setPasswordHash(passwords.encode(PASSWORD)); user.setRole(UserRole.STOREKEEPER);
        users.saveAndFlush(user);
    }

    @Test
    void loginFormIsPublicAndIncludesCsrfButDashboardRequiresLogin() throws Exception {
        mvc.perform(get("/login")).andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"_csrf\"")));
        mvc.perform(get("/css/main.css")).andExpect(status().isOk());
        mvc.perform(get("/dashboard")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void authenticatesAgainstDatabaseAndRendersNameThenLogsOut() throws Exception {
        var result = mvc.perform(formLogin().user("  AUTH-TEST  ").password(PASSWORD))
                .andExpect(authenticated().withUsername("auth-test"))
                .andExpect(redirectedUrl("/dashboard")).andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        mvc.perform(get("/dashboard").session(session)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Тестовый сотрудник")))
                .andExpect(content().string(containsString("name=\"_csrf\"")));
        mvc.perform(post("/logout").session(session).with(csrf()))
                .andExpect(unauthenticated()).andExpect(redirectedUrl("/login?logout"));
        assertTrue(session.isInvalid());
        mvc.perform(get("/dashboard")).andExpect(status().is3xxRedirection());
    }

    @Test
    void wrongPasswordAndInactiveAccountCannotAuthenticate() throws Exception {
        mvc.perform(formLogin().user("auth-test").password("wrong"))
                .andExpect(unauthenticated()).andExpect(redirectedUrl("/login?error"));
        User user = users.findByLoginIgnoreCase("auth-test").orElseThrow();
        user.setActive(false); users.saveAndFlush(user);
        mvc.perform(formLogin().user("auth-test").password(PASSWORD))
                .andExpect(unauthenticated()).andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void csrfIsRequiredForLoginAndLogout() throws Exception {
        mvc.perform(post("/login").param("username", "auth-test").param("password", PASSWORD))
                .andExpect(status().isForbidden());
        var login = mvc.perform(formLogin().user("auth-test").password(PASSWORD)).andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        mvc.perform(post("/logout").session(session)).andExpect(status().isForbidden());
        mvc.perform(get("/dashboard").session(session)).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "RECIPIENT")
    void recipientCannotAccessWarehouseOrAdministration() throws Exception {
        mvc.perform(get("/inventory/auth-check")).andExpect(status().isForbidden());
        mvc.perform(get("/admin/auth-check")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "STOREKEEPER")
    void storekeeperCannotAccessAdministration() throws Exception {
        mvc.perform(get("/admin/auth-check")).andExpect(status().isForbidden());
        // No module exists yet: reaching MVC's 404 confirms that security allowed this role.
        mvc.perform(get("/inventory/auth-check")).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanReachBothProtectedModules() throws Exception {
        mvc.perform(get("/admin/auth-check")).andExpect(status().isNotFound());
        mvc.perform(get("/inventory/auth-check")).andExpect(status().isNotFound());
    }

    @Test
    void bootstrapStoresHashAndDoesNotOverwriteOrCreateAnotherAdmin() {
        char[] password = PASSWORD.toCharArray();
        admins.createFirstAdmin("FIRST-ADMIN", "Администратор", password);
        User admin = users.findByLoginIgnoreCase("first-admin").orElseThrow();
        assertEquals(UserRole.ADMIN, admin.getRole());
        assertNotEquals(PASSWORD, admin.getPasswordHash());
        assertTrue(passwords.matches(PASSWORD, admin.getPasswordHash()));
        assertThrows(IllegalStateException.class,
                () -> admins.createFirstAdmin("second-admin", "Другой", password));
    }

    @Test
    void databaseRejectsCaseInsensitiveDuplicateLogin() {
        User duplicate = new User(); duplicate.setLogin("AUTH-TEST"); duplicate.setFullName("Дубликат");
        duplicate.setPasswordHash("test-only"); duplicate.setRole(UserRole.RECIPIENT);
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> users.saveAndFlush(duplicate));
    }
}
