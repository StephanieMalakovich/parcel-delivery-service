package by.delivery.stats_service.repository.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "review_facts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class ReviewFact {

    @Id
    @Column(name = "review_id")
    private Integer reviewId;

    @Column(name = "order_id", nullable = false)
    private Integer orderId;

    @Column(name = "courier_id", nullable = false)
    private Integer courierId;

    @Column(name = "rating", nullable = false)
    private Short rating;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
