package ru.vkr.materials;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import ru.vkr.materials.model.*;
import ru.vkr.materials.repository.*;
import java.time.LocalDate;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
@WithMockUser(roles = "ADMIN")
class CatalogIT {
    @Autowired WebApplicationContext context;
    @Autowired UnitRepository units;
    @Autowired CategoryRepository categories;
    @Autowired MaterialRepository materials;
    @Autowired MaterialBatchRepository batches;
    MockMvc mvc;
    Unit unit;
    Category category;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        unit = new Unit(); unit.setName("Миллилитр"); unit.setSymbol("мл"); units.saveAndFlush(unit);
        category = new Category(); category.setName("Реактивы"); categories.saveAndFlush(category);
    }
    private Material material(String code) {
        Material m = new Material(); m.setName("Раствор Ёмкость"); m.setCode(code);
        m.setUnit(unit); m.setCategory(category); m.setHasExpiry(true); return materials.saveAndFlush(m);
    }

    @Test
    void adminCanCreateCatalogEntriesAndMaterialWithBothForeignKeys() throws Exception {
        for (String path : new String[]{"units", "categories", "materials"}) {
            mvc.perform(get("/admin/catalog/" + path + "/new")).andExpect(status().isOk());
        }
        mvc.perform(post("/admin/catalog/units").with(csrf()).param("name", "Упаковка").param("symbol", "уп"))
                .andExpect(redirectedUrl("/catalog/units"));
        Unit createdUnit = units.findAll().stream().filter(u -> u.getSymbol().equals("уп")).findFirst().orElseThrow();
        mvc.perform(post("/admin/catalog/categories").with(csrf()).param("name", "Расходные материалы"))
                .andExpect(redirectedUrl("/catalog/categories"));
        Category createdCategory = categories.findAll().stream().filter(c -> c.getName().equals("Расходные материалы")).findFirst().orElseThrow();
        mvc.perform(post("/admin/catalog/materials").with(csrf()).param("code", "MAT-001").param("name", "Наконечники")
                .param("unitId", createdUnit.getId().toString()).param("categoryId", createdCategory.getId().toString())
                .param("hasExpiry", "true").param("active", "true"))
                .andExpect(redirectedUrl("/catalog/materials"));
        Material m = materials.findAll().stream().filter(v -> v.getCode().equals("MAT-001")).findFirst().orElseThrow();
        assertEquals(createdUnit.getId(), m.getUnit().getId()); assertEquals(createdCategory.getId(), m.getCategory().getId());
        assertTrue(m.isHasExpiry()); assertTrue(m.isActive());
        mvc.perform(get("/catalog/materials")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Наконечники")))
                .andExpect(content().string(containsString("Добавить материал")));
    }

    @Test
    @WithMockUser(roles = "STOREKEEPER")
    void storekeeperCanCreateEditAndArchiveMaterialsButCannotChangeReferenceLists() throws Exception {
        Material m = material("STORE-EDIT");
        mvc.perform(get("/catalog/materials")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Добавить материал")));
        mvc.perform(get("/admin/catalog/materials/new")).andExpect(status().isOk());
        mvc.perform(post("/admin/catalog/materials").with(csrf()).param("code", "STORE-NEW").param("name", "Материал склада")
                .param("unitId", unit.getId().toString()).param("categoryId", category.getId().toString())
                .param("active", "true")).andExpect(redirectedUrl("/catalog/materials"));
        assertTrue(materials.findAll().stream().anyMatch(row -> row.getCode().equals("STORE-NEW")));
        mvc.perform(get("/admin/catalog/materials/" + m.getId() + "/edit")).andExpect(status().isOk());
        mvc.perform(post("/admin/catalog/materials/" + m.getId()).with(csrf()).param("code", m.getCode())
                .param("name", "Изменён сотрудником склада").param("unitId", unit.getId().toString())
                .param("categoryId", category.getId().toString()).param("hasExpiry", "true")
                .param("active", "false").param("version", "0")).andExpect(redirectedUrl("/catalog/materials"));
        assertFalse(m.isActive()); assertEquals("Изменён сотрудником склада", m.getName());
        for (String path : new String[]{"units", "categories"}) {
            mvc.perform(get("/catalog/" + path)).andExpect(status().isOk())
                    .andExpect(content().string(not(containsString("/admin/catalog/"))));
            mvc.perform(get("/admin/catalog/" + path + "/new")).andExpect(status().isForbidden());
            mvc.perform(get("/admin/catalog/" + path + "/1/edit")).andExpect(status().isForbidden());
            mvc.perform(post("/admin/catalog/" + path).with(csrf())).andExpect(status().isForbidden());
            mvc.perform(post("/admin/catalog/" + path + "/1").with(csrf())).andExpect(status().isForbidden());
        }
    }

    @Test
    @WithMockUser(roles = "RECIPIENT")
    void recipientCanReadButCannotAddOrEditAnything() throws Exception { verifyReadOnly(); }

    private void verifyReadOnly() throws Exception {
        for (String path : new String[]{"units", "categories", "materials"}) {
            mvc.perform(get("/catalog/" + path)).andExpect(status().isOk())
                    .andExpect(content().string(not(containsString("/admin/catalog/"))));
            mvc.perform(get("/admin/catalog/" + path + "/new")).andExpect(status().isForbidden());
            mvc.perform(get("/admin/catalog/" + path + "/1/edit")).andExpect(status().isForbidden());
            mvc.perform(post("/admin/catalog/" + path).with(csrf())).andExpect(status().isForbidden());
            mvc.perform(post("/admin/catalog/" + path + "/1").with(csrf())).andExpect(status().isForbidden());
        }
    }

    @Test
    void russianSearchFindsNamesAndArchiveIsOptional() throws Exception {
        Material m = material("LAB-Ё001");
        mvc.perform(get("/catalog/materials").param("q", "раствор ёмкость"))
                .andExpect(status().isOk()).andExpect(content().string(containsString(m.getCode())));
        mvc.perform(get("/catalog/units").param("q", "МИЛЛИЛИТР"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Миллилитр")));
        mvc.perform(get("/catalog/categories").param("q", "РЕАКТИВЫ"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Реактивы")));
        m.setActive(false); materials.saveAndFlush(m);
        mvc.perform(get("/catalog/materials").param("q", "LAB-Ё001"))
                .andExpect(content().string(not(containsString("<strong>LAB-Ё001</strong>"))));
        mvc.perform(get("/catalog/materials").param("q", "LAB-Ё001").param("showArchived", "true"))
                .andExpect(content().string(containsString("В архиве")));
    }

    @Test
    void adminCanEditReferenceEntriesAndArchiveMaterialWithoutDeletingIt() throws Exception {
        Material m = material("ARCHIVE-ME");
        mvc.perform(get("/admin/catalog/units/" + unit.getId() + "/edit")).andExpect(status().isOk());
        mvc.perform(post("/admin/catalog/units/" + unit.getId()).with(csrf())
                .param("name", "Миллилитры").param("symbol", "мл").param("version", "0"))
                .andExpect(redirectedUrl("/catalog/units"));
        mvc.perform(get("/admin/catalog/categories/" + category.getId() + "/edit")).andExpect(status().isOk());
        mvc.perform(post("/admin/catalog/categories/" + category.getId()).with(csrf())
                .param("name", "Реактивы лабораторные").param("version", "0"))
                .andExpect(redirectedUrl("/catalog/categories"));
        mvc.perform(get("/admin/catalog/materials/" + m.getId() + "/edit")).andExpect(status().isOk());
        mvc.perform(post("/admin/catalog/materials/" + m.getId()).with(csrf()).param("code", m.getCode())
                .param("name", "Архивный раствор").param("unitId", unit.getId().toString())
                .param("categoryId", category.getId().toString()).param("hasExpiry", "true")
                .param("active", "false").param("version", "0"))
                .andExpect(redirectedUrl("/catalog/materials"));
        assertTrue(materials.existsById(m.getId())); assertFalse(m.isActive());
        assertEquals("Миллилитры", m.getUnit().getName()); assertEquals("Реактивы лабораторные", m.getCategory().getName());
    }

    @Test
    void invalidInputAndUnknownReferencesDoNotSaveRecords() throws Exception {
        long count = materials.count();
        mvc.perform(post("/admin/catalog/materials").with(csrf()).param("code", "BAD").param("name", ""))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Укажите название материала")));
        mvc.perform(post("/admin/catalog/materials").with(csrf()).param("code", "BAD").param("name", "Тест")
                .param("unitId", "999999999").param("categoryId", category.getId().toString()))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Единица измерения не найдена")));
        assertEquals(count, materials.count());
        mvc.perform(post("/admin/catalog/units").with(csrf()).param("name", "   ").param("symbol", " "))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Укажите сокращение")));
    }

    @Test
    void csrfAndStaleEditsAreRejected() throws Exception {
        mvc.perform(post("/admin/catalog/categories").param("name", "Без CSRF")).andExpect(status().isForbidden());
        mvc.perform(post("/admin/catalog/categories/" + category.getId()).with(csrf()).param("name", "Первое имя").param("version", "0"))
                .andExpect(redirectedUrl("/catalog/categories"));
        mvc.perform(post("/admin/catalog/categories/" + category.getId()).with(csrf()).param("name", "Устаревшее имя").param("version", "0"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Запись уже изменена")));
        assertEquals("Первое имя", category.getName());
    }

    @Test
    void unitAndExpiryPolicyCannotChangeAfterBatchesExist() throws Exception {
        Material m = material("WITH-BATCH"); MaterialBatch batch = new MaterialBatch(); batch.setMaterial(m);
        batch.setBatchNumber("B-1"); batch.setExpiresOn(LocalDate.of(2029, 1, 1)); batches.saveAndFlush(batch);
        Unit other = new Unit(); other.setName("Литр"); other.setSymbol("л"); units.saveAndFlush(other);
        mvc.perform(post("/admin/catalog/materials/" + m.getId()).with(csrf()).param("code", m.getCode()).param("name", m.getName())
                .param("unitId", other.getId().toString()).param("categoryId", category.getId().toString())
                .param("hasExpiry", "true").param("active", "true").param("version", "0"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("У материала уже есть партии")));
        assertEquals(unit.getId(), m.getUnit().getId());
        mvc.perform(post("/admin/catalog/units/" + unit.getId()).with(csrf()).param("name", "Миллилитр")
                .param("symbol", "л").param("version", "0"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Её сокращение менять нельзя")));
        assertEquals("мл", unit.getSymbol());
    }
}
