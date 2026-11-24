package by.delivery.main_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import by.delivery.main_service.dto.CreateCourierRequest;
import by.delivery.main_service.dto.CourierResponse;
import by.delivery.main_service.service.CourierProfileService;
import by.delivery.main_service.statistics.AnalyticsOutboxDispatcher;
import by.delivery.stats.grpc.AdminOverviewRequest;
import by.delivery.stats.grpc.AdminOverviewResponse;
import by.delivery.stats.grpc.CourierStatsRequest;
import by.delivery.stats.grpc.CourierStatsResponse;
import by.delivery.stats.grpc.RecordAnalyticsEventRequest;
import by.delivery.stats.grpc.RecordAnalyticsEventResponse;
import by.delivery.stats.grpc.StatisticsServiceGrpc;
import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;

@SpringBootTest(properties = "app.statistics.outbox.poll-delay=1h")
@Import(TestcontainersConfiguration.class)
class StatisticsApiIntegrationTest {

    private static final String STATS = "/api/v1/stats";
    private static final FakeStatistics STATISTICS = new FakeStatistics();
    private static final Context.Key<String> REQUEST_ID = Context.key("request-id");
    private static Server server;

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private CourierProfileService couriers;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private TransactionTemplate transaction;
    @Autowired
    private AnalyticsOutboxDispatcher dispatcher;

    private MockMvc mvc;

    @DynamicPropertySource
    static void grpcTarget(DynamicPropertyRegistry properties) throws IOException {
        server = ServerBuilder.forPort(0).addService(STATISTICS).intercept(new RequestIdCapture()).build().start();
        properties.add("spring.grpc.client.channel.statistics.target", () -> "dns:///localhost:" + server.getPort());
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.shutdownNow();
        }
    }

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        jdbc.execute("TRUNCATE TABLE statistics_outbox, couriers RESTART IDENTITY CASCADE");
        STATISTICS.attempts.clear();
        STATISTICS.unavailable = false;
        STATISTICS.courierRequest = null;
        STATISTICS.requestId = null;
    }

    @Test
    void courierGetsOwnStatsAndPassesThroughPeriodAndRequestId() throws Exception {
        int id = createCourier().id();
        var result = mvc.perform(get(STATS).with(TestJwt.courier("courier-sub"))
                .param("courierId", "999")
                .param("from", "2026-11-01T00:00:00Z")
                .param("to", "2026-12-01T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-ID"))
                .andExpect(jsonPath("$.courierId").value(id))
                .andExpect(jsonPath("$.deliveredCount").value(3))
                .andExpect(jsonPath("$.averageRating").value((Object) null))
                .andReturn();

        assertThat(STATISTICS.courierRequest.getCourierId()).isEqualTo(id);
        assertThat(STATISTICS.courierRequest.getPeriod().getFrom().getSeconds())
                .isEqualTo(Instant.parse("2026-11-01T00:00:00Z").getEpochSecond());
        assertThat(STATISTICS.requestId).isEqualTo(result.getResponse().getHeader("X-Request-ID"));
    }

    @Test
    void statisticsAreProtectedAndGrpcFailureIsNotReportedAsEmptyStatistics() throws Exception {
        mvc.perform(get(STATS)).andExpect(status().isUnauthorized());
        mvc.perform(get(STATS).with(TestJwt.customer("customer-sub"))).andExpect(status().isForbidden());
        mvc.perform(get(STATS).with(TestJwt.courier("no-profile"))).andExpect(status().isNotFound());
        mvc.perform(get(STATS).with(TestJwt.admin()).param("from", "bad-date")).andExpect(status().isBadRequest());
        mvc.perform(get(STATS).with(TestJwt.admin())
                .param("from", "2026-12-01T00:00:00Z").param("to", "2026-11-01T00:00:00Z"))
                .andExpect(status().isBadRequest());
        mvc.perform(get(STATS).with(TestJwt.admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelledCount").value(8));
        STATISTICS.unavailable = true;
        mvc.perform(get(STATS).with(TestJwt.admin())).andExpect(status().isServiceUnavailable());
    }

    @Test
    void committedOutboxEntryRetriesWithSameIdAfterGrpcRecovers() {
        STATISTICS.unavailable = true;
        int id = createCourier().id();
        assertThat(STATISTICS.attempts).isEmpty();
        dispatcher.dispatchAvailable();
        assertThat(jdbc.queryForObject("SELECT attempts FROM statistics_outbox", Integer.class)).isEqualTo(1);

        STATISTICS.unavailable = false;
        jdbc.update("UPDATE statistics_outbox SET next_attempt_at = now()");
        dispatcher.dispatchAvailable();

        assertThat(STATISTICS.attempts).hasSize(2);
        var event = STATISTICS.attempts.getFirst();
        assertThat(STATISTICS.attempts.getLast()).isEqualTo(event);
        assertThat(UUID.fromString(event.getEventId())).isNotNull();
        assertThat(event.getCourierUpdated().getCourierId()).isEqualTo(id);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM statistics_outbox", Integer.class)).isZero();
    }

    @Test
    void rollingBackDomainChangeAlsoRollsBackOutboxEntry() {
        transaction.executeWithoutResult(status -> {
            createCourier();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM statistics_outbox", Integer.class)).isEqualTo(1);
            status.setRollbackOnly();
        });

        dispatcher.dispatchAvailable();
        assertThat(STATISTICS.attempts).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM couriers", Integer.class)).isZero();
    }

    private CourierResponse createCourier() {
        return couriers.create(new CreateCourierRequest("courier-sub", "Тестовый курьер", null, Set.of()));
    }

    private static class FakeStatistics extends StatisticsServiceGrpc.StatisticsServiceImplBase {
        private final CopyOnWriteArrayList<RecordAnalyticsEventRequest> attempts = new CopyOnWriteArrayList<>();
        private volatile CourierStatsRequest courierRequest;
        private volatile String requestId;
        private volatile boolean unavailable;

        @Override
        public void getCourierStats(CourierStatsRequest request, StreamObserver<CourierStatsResponse> observer) {
            courierRequest = request;
            requestId = REQUEST_ID.get();
            respond(observer, CourierStatsResponse.newBuilder()
                    .setCourierId(request.getCourierId()).setDeliveredCount(3).build());
        }

        @Override
        public void getAdminOverview(AdminOverviewRequest request, StreamObserver<AdminOverviewResponse> observer) {
            respond(observer, AdminOverviewResponse.newBuilder().setCancelledCount(8).build());
        }

        @Override
        public void recordAnalyticsEvent(RecordAnalyticsEventRequest request,
                StreamObserver<RecordAnalyticsEventResponse> observer) {
            attempts.add(request);
            respond(observer, RecordAnalyticsEventResponse.getDefaultInstance());
        }

        private <T> void respond(StreamObserver<T> observer, T value) {
            if (unavailable) {
                observer.onError(Status.UNAVAILABLE.asRuntimeException());
            } else {
                observer.onNext(value);
                observer.onCompleted();
            }
        }
    }

    private static class RequestIdCapture implements ServerInterceptor {
        @Override
        public <Q, R> ServerCall.Listener<Q> interceptCall(ServerCall<Q, R> call, Metadata headers,
                ServerCallHandler<Q, R> next) {
            String id = headers.get(Metadata.Key.of("x-request-id", Metadata.ASCII_STRING_MARSHALLER));
            return Contexts.interceptCall(Context.current().withValue(REQUEST_ID, id), call, headers, next);
        }
    }
}
