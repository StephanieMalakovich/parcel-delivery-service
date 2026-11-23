package by.delivery.stats_service.service.model;

import java.time.LocalDateTime;


public record Period(LocalDateTime from, LocalDateTime to) {

    private static final LocalDateTime MIN = LocalDateTime.of(1, 1, 1, 0, 0);
    private static final LocalDateTime MAX = LocalDateTime.of(9999, 12, 31, 23, 59, 59);

    public Period {
        if (from != null && to != null && !from.isBefore(to)) {
            throw new IllegalArgumentException("Начало периода должно быть раньше конца: from=" + from + ", to=" + to);
        }
    }

    public static Period unbounded() {
        return new Period(null, null);
    }

    public LocalDateTime fromOrMin() {
        return from == null ? MIN : from;
    }

    public LocalDateTime toOrMax() {
        return to == null ? MAX : to;
    }
}
