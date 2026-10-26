package by.delivery.main_service;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.jayway.jsonpath.JsonPath;

@SpringBootTest(properties = "spring.liquibase.contexts=test")
@Import(TestcontainersConfiguration.class)
@RecordApplicationEvents
class DirectoryApiIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void prepare() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE
                    reviews,
                    parcels,
                    courier_zones,
                    notifications,
                    orders,
                    clients,
                    couriers,
                    zones
                RESTART IDENTITY CASCADE
                """);
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void givenZone_whenCrudOverHttp_thenAllOperationsWork() throws Exception {

        // When: создание
        MvcResult created = send(post("/api/v1/zones"), "{\"name\": \"Север\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("Север")))
                .andReturn();
        int id = JsonPath.read(body(created), "$.id");

        // Then: чтение, список, обновление
        send(get("/api/v1/zones/" + id), null).andExpect(status().isOk()).andExpect(jsonPath("$.name", is("Север")));
        send(get("/api/v1/zones"), null).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
        send(put("/api/v1/zones/" + id), "{\"name\": \"Юг\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Юг")));

        // Then: дубликат названия — 409, пустое название — 400
        send(post("/api/v1/zones"), "{\"name\": \"Юг\"}").andExpect(status().isConflict());
        send(post("/api/v1/zones"), "{\"name\": \"\"}").andExpect(status().isBadRequest());

        // Then: удаление, повторное чтение — 404
        send(delete("/api/v1/zones/" + id), null).andExpect(status().isNoContent());
        send(get("/api/v1/zones/" + id), null).andExpect(status().isNotFound());
    }

    @Test
    void givenClient_whenCrudOverHttp_thenAllOperationsWork() throws Exception {

        MvcResult created = send(post("/api/v1/clients"),
                "{\"userId\": 1, \"fullName\": \"Анна\", \"phone\": \"79001112233\"}")
                .andExpect(status().isCreated())
                .andReturn();
        int id = JsonPath.read(body(created), "$.id");

        send(get("/api/v1/clients/" + id), null).andExpect(jsonPath("$.fullName", is("Анна")));
        send(get("/api/v1/clients/me").header("X-User-Id", 1), null).andExpect(jsonPath("$.id", is(id)));
        send(put("/api/v1/clients/" + id), "{\"fullName\": \"Анна Новая\", \"phone\": \"79000000000\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName", is("Анна Новая")));

        // Повторная регистрация того же пользователя и неверный телефон
        send(post("/api/v1/clients"), "{\"userId\": 1, \"fullName\": \"Дубль\"}").andExpect(status().isConflict());
        send(post("/api/v1/clients"), "{\"userId\": 2, \"fullName\": \"Б\", \"phone\": \"abc\"}")
                .andExpect(status().isBadRequest());

        send(delete("/api/v1/clients/" + id), null).andExpect(status().isNoContent());
        send(get("/api/v1/clients/" + id), null).andExpect(status().isNotFound());
    }

    @Test
    void givenCourier_whenCrudOverHttp_thenZonesAreReplaced() throws Exception {

        int zoneA = createZone("А");
        int zoneB = createZone("Б");

        MvcResult created = send(post("/api/v1/couriers"),
                "{\"userId\": 10, \"fullName\": \"Иван\", \"zoneIds\": [" + zoneA + "]}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.zoneIds", contains(zoneA)))
                .andReturn();
        int id = JsonPath.read(body(created), "$.id");

        // Обновление заменяет набор зон
        send(put("/api/v1/couriers/" + id), "{\"fullName\": \"Иван П.\", \"zoneIds\": [" + zoneB + "]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName", is("Иван П.")))
                .andExpect(jsonPath("$.zoneIds", contains(zoneB)));

        send(get("/api/v1/couriers/" + id + "/reviews"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));

        // Зона, не существующая в БД
        send(post("/api/v1/couriers"), "{\"userId\": 11, \"fullName\": \"Петр\", \"zoneIds\": [9999]}")
                .andExpect(status().isNotFound());

        send(delete("/api/v1/couriers/" + id), null).andExpect(status().isNoContent());
        send(get("/api/v1/couriers/" + id), null).andExpect(status().isNotFound());
    }

    private int createZone(String name) throws Exception {
        MvcResult result = send(post("/api/v1/zones"), "{\"name\": \"" + name + "\"}")
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(body(result), "$.id");
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String json) throws Exception {
        request.characterEncoding(StandardCharsets.UTF_8);
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mockMvc.perform(request);
    }

    private String body(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}