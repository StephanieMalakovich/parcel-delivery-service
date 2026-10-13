package by.delivery.main_service.repository.entity;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "courier_zones")
@Getter
@Setter
@ToString
@NoArgsConstructor
public class CourierZone {
    @EmbeddedId
    private CourierZoneId id = new CourierZoneId();

    @ToString.Exclude
    @MapsId("courierId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "courier_id", nullable = false)
    private Courier courier;

    @ToString.Exclude
    @MapsId("zoneId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "zone_id", nullable = false)
    private Zone zone;

    public CourierZone(Courier courier, Zone zone) {
        this.courier = courier;
        this.zone = zone;

        this.id = new CourierZoneId(
                courier == null ? null : courier.getId(),
                zone == null ? null : zone.getId());
    }

}
