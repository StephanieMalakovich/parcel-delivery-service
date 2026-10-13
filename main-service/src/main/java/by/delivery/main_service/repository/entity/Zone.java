package by.delivery.main_service.repository.entity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "zones")
@Getter
@Setter
@ToString
@NoArgsConstructor
public class Zone {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "name", nullable = false, unique = true)
    private String name;

    @ToString.Exclude
    @OneToMany(mappedBy = "zone", fetch = FetchType.LAZY)
    private Set<CourierZone> courierLinks = new HashSet<>();

    @ToString.Exclude
    @OneToMany(mappedBy = "zone", fetch = FetchType.LAZY)
    private List<Order> orders = new ArrayList<>();

}
