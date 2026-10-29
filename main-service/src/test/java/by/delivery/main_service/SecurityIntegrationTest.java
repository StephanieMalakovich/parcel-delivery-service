package by.delivery.main_service;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.jayway.jsonpath.JsonPath;

@SpringBootTest(properties = "spring.liquibase.contexts=test")
@Import(TestcontainersConfiguration.class)
@RecordApplicationEvents
class SecurityIntegrationTest {

    // Корректное тело заказа: @Valid проверяется до @PreAuthorize, и с невалидным телом пришёл бы 400, а не 403
    private static final String VALID_ORDER = """
            {"zoneId": 1, "pickupAddress": "A", "deliveryAddress": "B",
             "parcel": {"weightKg": 1, "size": "SMALL"}}
            """;

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
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void givenNoToken_whenAnyEndpointIsCalled_thenUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/zones")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/orders")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/notifications")).andExpect(status().isUnauthorized());
    }

    @Test
    void givenCustomer_whenCallsNonCustomerEndpoints_thenForbidden() throws Exception {
        RequestPostProcessor customer = TestJwt.customer("1001");

        mockMvc.perform(get("/api/v1/orders").with(customer)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/orders/assigned").with(customer)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/orders/available").with(customer)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/orders/1/accept").with(customer)).andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/orders/1/status").with(customer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"PICKED_UP\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/orders/1").with(customer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"action\": \"CANCEL\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/zones").with(customer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"Зона\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/clients").with(customer)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/couriers").with(customer)).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/couriers/1").with(customer)).andExpect(status().isForbidden());
    }

    @Test
    void givenCourier_whenCallsNonCourierEndpoints_thenForbidden() throws Exception {
        RequestPostProcessor courier = TestJwt.courier("2001");

        mockMvc.perform(post("/api/v1/orders").with(courier)
                .contentType(MediaType.APPLICATION_JSON).content(VALID_ORDER))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/orders").with(courier)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/orders/my").with(courier)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/orders/1/cancel").with(courier)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/orders/1/review").with(courier)
                .contentType(MediaType.APPLICATION_JSON).content("{\"rating\": 5}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/clients/me").with(courier)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/couriers").with(courier)).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/zones/1").with(courier)).andExpect(status().isForbidden());
    }

    @Test
    void givenAdmin_whenCallsCustomerOrCourierEndpoints_thenForbidden() throws Exception {
        RequestPostProcessor admin = TestJwt.admin();

        mockMvc.perform(post("/api/v1/orders").with(admin)
                .contentType(MediaType.APPLICATION_JSON).content(VALID_ORDER))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/orders/my").with(admin)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/orders/1/accept").with(admin)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/orders/1/cancel").with(admin)).andExpect(status().isForbidden());
    }

    @Test
    void givenAnyRole_whenReadsZonesAndNotifications_thenOk() throws Exception {
        for (RequestPostProcessor user : new RequestPostProcessor[] {
                TestJwt.customer("1001"), TestJwt.courier("2001"), TestJwt.admin() }) {
            mockMvc.perform(get("/api/v1/zones").with(user)).andExpect(status().isOk());
            mockMvc.perform(get("/api/v1/notifications").with(user)).andExpect(status().isOk());
            mockMvc.perform(get("/api/v1/notifications/unread-count").with(user)).andExpect(status().isOk());
        }
    }

    @Test
    void givenCustomer_whenRegistersProfile_thenOnlyForHimself() throws Exception {
        RequestPostProcessor customer = TestJwt.customer("1001");

        mockMvc.perform(post("/api/v1/clients").with(customer)
                .contentType(MediaType.APPLICATION_JSON).characterEncoding(StandardCharsets.UTF_8)
                .content("{\"userId\": \"1002\", \"fullName\": \"Чужой\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/clients").with(customer)
                .contentType(MediaType.APPLICATION_JSON).characterEncoding(StandardCharsets.UTF_8)
                .content("{\"userId\": \"1001\", \"fullName\": \"Свой\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/v1/clients/me").with(customer)).andExpect(status().isOk());
    }

    @Test
    void givenOrder_whenReadByDifferentUsers_thenOnlyParticipantsAndAdminSeeIt() throws Exception {

        // Given: зона, два заказчика, два курьера; заказ принят первым курьером
        int zoneId = id(mockMvc.perform(post("/api/v1/zones").with(TestJwt.admin())
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"Центр\"}")).andReturn());
        createClient("1001");
        createClient("1002");
        createCourier("2001", zoneId);
        createCourier("2002", zoneId);
        int orderId = id(mockMvc.perform(post("/api/v1/orders").with(TestJwt.customer("1001"))
                .contentType(MediaType.APPLICATION_JSON).characterEncoding(StandardCharsets.UTF_8)
                .content("""
                        {"zoneId": %d, "pickupAddress": "A", "deliveryAddress": "B",
                         "parcel": {"weightKg": 1, "size": "SMALL"}}
                        """.formatted(zoneId)))
                .andExpect(status().isCreated()).andReturn());
        mockMvc.perform(post("/api/v1/orders/" + orderId + "/accept").with(TestJwt.courier("2001")))
                .andExpect(status().isOk());

        // Then: участники и администратор видят заказ
        mockMvc.perform(get("/api/v1/orders/" + orderId).with(TestJwt.customer("1001"))).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/orders/" + orderId).with(TestJwt.courier("2001"))).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/orders/" + orderId).with(TestJwt.admin())).andExpect(status().isOk());

        // Then: посторонние — нет
        mockMvc.perform(get("/api/v1/orders/" + orderId).with(TestJwt.customer("1002")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/orders/" + orderId).with(TestJwt.courier("2002")))
                .andExpect(status().isForbidden());
    }

    @Test
    void givenCustomerWithoutProfile_whenReadsOwnOrders_thenNotFound() throws Exception {
        // токен валидный и роль подходит, но профиль клиента в БД ещё не создан
        mockMvc.perform(get("/api/v1/orders/my").with(TestJwt.customer("unknown-sub")))
                .andExpect(status().isNotFound());
    }

    private void createClient(String userId) throws Exception {
        mockMvc.perform(post("/api/v1/clients").with(TestJwt.admin())
                .contentType(MediaType.APPLICATION_JSON).characterEncoding(StandardCharsets.UTF_8)
                .content("{\"userId\": \"" + userId + "\", \"fullName\": \"Клиент " + userId + "\"}"))
                .andExpect(status().isCreated());
    }

    private void createCourier(String userId, int zoneId) throws Exception {
        MockHttpServletRequestBuilder request = post("/api/v1/couriers").with(TestJwt.admin())
                .contentType(MediaType.APPLICATION_JSON).characterEncoding(StandardCharsets.UTF_8)
                .content("{\"userId\": \"" + userId + "\", \"fullName\": \"Курьер " + userId
                        + "\", \"zoneIds\": [" + zoneId + "]}");
        mockMvc.perform(request).andExpect(status().isCreated());
    }

    private int id(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id");
    }
}
