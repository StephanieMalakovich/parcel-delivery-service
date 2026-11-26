package by.delivery.main_service.dto;

import java.util.List;

import by.delivery.stats.grpc.AdminOverviewResponse;
import by.delivery.stats.grpc.CourierRating;
import by.delivery.stats.grpc.CourierStatsResponse;

public sealed interface StatisticsResponse permits StatisticsResponse.Courier, StatisticsResponse.Admin {

    record Courier(int courierId, String fullName, long deliveredCount, Double averageRating,
            long ratedReviewsCount, long pendingReviewsCount, Double averageDeliveryMinutes)
            implements StatisticsResponse {
        public static Courier from(CourierStatsResponse stats) {
            return new Courier(stats.getCourierId(), stats.getFullName(), stats.getDeliveredCount(),
                    stats.hasAverageRating() ? stats.getAverageRating() : null, stats.getRatedReviewsCount(),
                    stats.getPendingReviewsCount(),
                    stats.hasAverageDeliveryMinutes() ? stats.getAverageDeliveryMinutes() : null);
        }
    }

    record Admin(long deliveredCount, long cancelledCount, long cancelledByClientCount, long cancelledByAdminCount,
            double cancelledPercent, Double averageAssignmentMinutes, Double averageDeliveryMinutes,
            List<Rating> courierRatings) implements StatisticsResponse {
        public static Admin from(AdminOverviewResponse stats) {
            return new Admin(stats.getDeliveredCount(), stats.getCancelledCount(), stats.getCancelledByClientCount(),
                    stats.getCancelledByAdminCount(), stats.getCancelledPercent(),
                    stats.hasAverageAssignmentMinutes() ? stats.getAverageAssignmentMinutes() : null,
                    stats.hasAverageDeliveryMinutes() ? stats.getAverageDeliveryMinutes() : null,
                    stats.getCourierRatingsList().stream().map(Rating::from).toList());
        }
    }

    record Rating(int position, int courierId, String fullName, Double averageRating,
            long ratedReviewsCount, long deliveredCount) {
        private static Rating from(CourierRating rating) {
            return new Rating(rating.getPosition(), rating.getCourierId(), rating.getFullName(),
                    rating.hasAverageRating() ? rating.getAverageRating() : null,
                    rating.getRatedReviewsCount(), rating.getDeliveredCount());
        }
    }
}
