package by.delivery.stats_service.repository.entity;

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
@Table(name = "courier_facts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class CourierFact {

    @Id
    @Column(name = "courier_id")
    private Integer courierId;

    @Column(name = "full_name", nullable = false)
    private String fullName;
}
