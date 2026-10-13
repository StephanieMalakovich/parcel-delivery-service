package by.delivery.main_service.repository.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
public class CourierZoneId implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "courier_id", nullable = false)
    private Integer courierId;

    @Column(name = "zone_id", nullable = false)
    private Integer zoneId;
}
