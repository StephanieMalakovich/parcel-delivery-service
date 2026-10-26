package by.delivery.main_service.repository.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "order_status_history", indexes = {
        @Index(name = "idx_order_status_history_order_status", columnList = "order_id, status")
})
@Getter
@Setter
@NoArgsConstructor
@ToString
public class OrderStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "status", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    public OrderStatusHistory(Order order, OrderStatus status, LocalDateTime changedAt) {
        this.order = order;
        this.status = status;
        this.changedAt = changedAt;
    }
}
