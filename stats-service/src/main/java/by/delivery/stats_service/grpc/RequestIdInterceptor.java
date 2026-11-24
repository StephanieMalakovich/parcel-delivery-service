package by.delivery.stats_service.grpc;

import org.springframework.grpc.server.GlobalServerInterceptor;
import org.springframework.stereotype.Component;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;

@Component
@GlobalServerInterceptor
class RequestIdInterceptor implements ServerInterceptor {

    private static final Metadata.Key<String> HEADER = Metadata.Key.of("x-request-id",
            Metadata.ASCII_STRING_MARSHALLER);
    static final Context.Key<String> REQUEST_ID = Context.key("request-id");

    @Override
    public <Q, R> ServerCall.Listener<Q> interceptCall(ServerCall<Q, R> call, Metadata headers,
            ServerCallHandler<Q, R> next) {
        String id = headers.get(HEADER);
        if (id == null || !id.matches("[a-zA-Z0-9-]{1,64}")) {
            return next.startCall(call, headers);
        }
        return Contexts.interceptCall(Context.current().withValue(REQUEST_ID, id), call, headers, next);
    }
}
