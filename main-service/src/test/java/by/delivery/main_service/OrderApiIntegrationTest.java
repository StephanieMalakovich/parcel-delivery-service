package by.delivery.main_service;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.jayway.jsonpath.JsonPath;

@SpringBootTest(properties = "spring.liquibase.contexts=test")
@Import(TestcontainersConfiguration.class)
@RecordApplicationEvents
class OrderApiIntegrationTest {

    private static final String USER_ID_HEADER = "X-User-Id";

    private static final int CLIENT_USER_ID = 1001;
    private static final int COURIER_USER_ID = 2001;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void cleanDatabase() {
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
    }

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void givenClientAndCourier_whenOrderGoesThroughAllStages_thenEachStepIsAvailableOverHttp() throws Exception {

        // Given: зона, заказчик и курьер, созданные через API
        int zoneId = createZone("Центральный");
        createClient(CLIENT_USER_ID);
        createCourier(COURIER_USER_ID, zoneId);

        // When: заказчик создаёт заказ
        MvcResult created = send(post("/orders"), CLIENT_USER_ID, orderJson(zoneId, "1.50"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("SEARCHING_COURIER")))
                .andExpect(jsonPath("$.courierId").doesNotExist())
                .andExpect(jsonPath("$.parcel.size", is("SMALL")))
                .andExpect(jsonPath("$.statusHistory", hasSize(1)))
                .andReturn();
        int orderId = JsonPath.read(body(created), "$.id");

        // Then: курьер видит заявку среди доступных
        send(get("/orders/available"), COURIER_USER_ID, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.content[0].id", is(orderId)));

        // When / Then: курьер принимает заявку
        send(post("/orders/" + orderId + "/accept"), COURIER_USER_ID, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("COURIER_ASSIGNED")));

        // When / Then: курьер забрал посылку
        send(patch("/orders/" + orderId + "/status"), COURIER_USER_ID, "{\"status\": \"PICKED_UP\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("PICKED_UP")));

        // When / Then: курьер доставил посылку
        send(patch("/orders/" + orderId + "/status"), COURIER_USER_ID, "{\"status\": \"DELIVERED\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("DELIVERED")))
                .andExpect(jsonPath("$.statusHistory", hasSize(4)));

        // When / Then: заказчик оценивает курьера
        send(post("/orders/" + orderId + "/review"), CLIENT_USER_ID, "{\"rating\": 5, \"comment\": \"Быстро\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rating", is(5)))
                .andExpect(jsonPath("$.orderId", is(orderId)));

        // Then: история заказов заказчика и заказов курьера
        send(get("/orders/my"), CLIENT_USER_ID, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(1)));
        send(get("/orders/assigned?status=DELIVERED"), COURIER_USER_ID, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(1)));

        // Then: у заказчика есть непрочитанное уведомление, его можно прочитать
        MvcResult notifications = send(get("/notifications?unreadOnly=true"), CLIENT_USER_ID, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.content[0].type", is("ORDER_ASSIGNED")))
                .andReturn();
        int notificationId = JsonPath.read(body(notifications), "$.content[0].id");

        send(patch("/notifications/" + notificationId + "/read"), CLIENT_USER_ID, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read", is(true)));
        send(get("/notifications/unread-count"), CLIENT_USER_ID, null)
                .andExpect(jsonPath("$.count", is(0)));
    }

    @Test
    void givenSearchingOrder_whenClientCancels_thenOrderIsCancelled() throws Exception {

        // Given
        int zoneId = createZone("Центральный");
        createClient(CLIENT_USER_ID);
        int orderId = createOrder(zoneId);

        // When / Then
        send(post("/orders/" + orderId + "/cancel"), CLIENT_USER_ID, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CANCELLED")))
                .andExpect(jsonPath("$.cancelledBy", is("CLIENT")))
                .andExpect(jsonPath("$.statusHistory", hasSize(2)));
    }

    @Test
    void givenAssignedOrder_whenAdminReassignsAndCancels_thenStatusIsUpdated() throws Exception {

        // Given: заказ принят первым курьером, второй курьер обслуживает ту же зону
        int zoneId = createZone("Центральный");
        createClient(CLIENT_USER_ID);
        createCourier(COURIER_USER_ID, zoneId);
        int secondCourierId = createCourier(2002, zoneId);
        int orderId = createOrder(zoneId);
        send(post("/orders/" + orderId + "/accept"), COURIER_USER_ID, null).andExpect(status().isOk());

        // When / Then: администратор переназначает курьера
        send(patch("/orders/" + orderId), null,
                "{\"action\": \"REASSIGN\", \"courierId\": " + secondCourierId + "}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courierId", is(secondCourierId)))
                .andExpect(jsonPath("$.status", is("COURIER_ASSIGNED")));

        // When / Then: администратор отменяет заказ
        send(patch("/orders/" + orderId), null, "{\"action\": \"CANCEL\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CANCELLED")))
                .andExpect(jsonPath("$.cancelledBy", is("ADMIN")));

        // Then: администратор видит заказ в общей истории с фильтром по статусу
        send(get("/orders?status=CANCELLED"), null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(1)));
    }

    @Test
    void givenInvalidRequests_whenSent_thenErrorsAreReturnedWithProperStatus() throws Exception {

        // Given
        int zoneId = createZone("Центральный");
        createClient(CLIENT_USER_ID);
        createClient(1002);
        createCourier(COURIER_USER_ID, zoneId);
        createCourier(2002, zoneId);
        int orderId = createOrder(zoneId);

        // 400: отрицательный вес посылки
        send(post("/orders"), CLIENT_USER_ID, orderJson(zoneId, "-1"))
                .andExpect(status().isBadRequest());
        // 400: пустой адрес
        send(post("/orders"), CLIENT_USER_ID,
                "{\"zoneId\": " + zoneId + ", \"pickupAddress\": \" \", \"deliveryAddress\": \"B\","
                        + " \"parcel\": {\"weightKg\": 1, \"size\": \"SMALL\"}}")
                .andExpect(status().isBadRequest());
        // 400: неизвестный размер посылки
        send(post("/orders"), CLIENT_USER_ID,
                "{\"zoneId\": " + zoneId + ", \"pickupAddress\": \"A\", \"deliveryAddress\": \"B\","
                        + " \"parcel\": {\"weightKg\": 1, \"size\": \"HUGE\"}}")
                .andExpect(status().isBadRequest());
        // 400: нет заголовка с идентификатором пользователя
        mockMvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content(orderJson(zoneId, "1")))
                .andExpect(status().isBadRequest());
        // 400: оценка вне диапазона
        send(post("/orders/" + orderId + "/review"), CLIENT_USER_ID, "{\"rating\": 6}")
                .andExpect(status().isBadRequest());
        // 400: переназначение без курьера
        send(patch("/orders/" + orderId), null, "{\"action\": \"REASSIGN\"}")
                .andExpect(status().isBadRequest());
        // 404: нет такого заказа
        send(get("/orders/999999"), null, null)
                .andExpect(status().isNotFound());
        // 403: чужой заказ нельзя отменить
        send(post("/orders/" + orderId + "/cancel"), 1002, null)
                .andExpect(status().isForbidden());
        // 409: вторая попытка принять уже принятую заявку
        send(post("/orders/" + orderId + "/accept"), COURIER_USER_ID, null).andExpect(status().isOk());
        send(post("/orders/" + orderId + "/accept"), 2002, null)
                .andExpect(status().isConflict());
        // 409: оценить недоставленный заказ
        send(post("/orders/" + orderId + "/review"), CLIENT_USER_ID, "{\"rating\": 5}")
                .andExpect(status().isConflict());
    }

    private int createZone(String name) throws Exception {
        MvcResult result = send(post("/zones"), null, "{\"name\": \"" + name + "\"}")
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(body(result), "$.id");
    }

    private void createClient(int userId) throws Exception {
        send(post("/clients"), null,
                "{\"userId\": " + userId + ", \"fullName\": \"Клиент " + userId + "\", \"phone\": \"79001112233\"}")
                .andExpect(status().isCreated());
    }

    private int createCourier(int userId, int zoneId) throws Exception {
        MvcResult result = send(post("/couriers"), null,
                "{\"userId\": " + userId + ", \"fullName\": \"Курьер " + userId + "\", \"zoneIds\": [" + zoneId + "]}")
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(body(result), "$.id");
    }

    private int createOrder(int zoneId) throws Exception {
        MvcResult result = send(post("/orders"), CLIENT_USER_ID, orderJson(zoneId, "1.50"))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(body(result), "$.id");
    }

    private String orderJson(int zoneId, String weight) {
        return """
                {
                  "zoneId": %d,
                  "pickupAddress": "ул. Ленина 1",
                  "deliveryAddress": "ул. Мира 10",
                  "parcel": {"weightKg": %s, "size": "SMALL", "description": "Документы"}
                }
                """.formatted(zoneId, weight);
    }

    private ResultActions send(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
            Integer userId,
            String json) throws Exception {
        request.characterEncoding(StandardCharsets.UTF_8);
        if (userId != null) {
            request.header(USER_ID_HEADER, userId);
        }
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mockMvc.perform(request);
    }

    private String body(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
