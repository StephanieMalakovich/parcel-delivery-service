package by.delivery.main_service.repository.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Index;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "orders", indexes = {
        @Index(name = "idx_orders_client", columnList = "client_id"),
        @Index(name = "idx_orders_courier", columnList = "courier_id"),
        @Index(name = "idx_orders_zone_status", columnList = "zone_id, status")
})
@Getter
@Setter
@NoArgsConstructor
@ToString
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "courier_id")
    private Courier courier;

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "zone_id", nullable = false)
    private Zone zone;

    @Column(name = "pickup_address", nullable = false, length = 255)
    private String pickupAddress;

    @Column(name = "delivery_address", nullable = false, length = 255)
    private String deliveryAddress;

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private OrderStatus status = OrderStatus.SEARCHING_COURIER;

    @Column(name = "cancelled_by")
    @Enumerated(EnumType.STRING)
    private CancelledBy cancelledBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @ToString.Exclude
    @OneToOne(mappedBy = "order", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private Parcel parcel;

    @ToString.Exclude
    @OneToOne(mappedBy = "order", fetch = FetchType.LAZY)
    private Review review;

    @ToString.Exclude
    @OneToMany(mappedBy = "order", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("changedAt ASC, id ASC")
    private List<OrderStatusHistory> statusHistory = new ArrayList<>();

    // Меняет статус заказа и фиксирует момент перехода в истории статусов
    public void changeStatus(OrderStatus newStatus, LocalDateTime changedAt) {
        this.status = newStatus;
        addStatusHistory(newStatus, changedAt);
    }

    // Добавляет запись в историю, не меняя текущий статус
    public void addStatusHistory(OrderStatus historyStatus, LocalDateTime changedAt) {
        statusHistory.add(new OrderStatusHistory(this, historyStatus, changedAt));
    }

    // Время последнего перехода в указанный статус или null, если заказ в нём не был
    public LocalDateTime findStatusTime(OrderStatus searchedStatus) {
        LocalDateTime result = null;
        for (OrderStatusHistory entry : statusHistory) {
            if (entry.getStatus() == searchedStatus
                    && (result == null || !entry.getChangedAt().isBefore(result))) {
                result = entry.getChangedAt();
            }
        }
        return result;
    }
}
