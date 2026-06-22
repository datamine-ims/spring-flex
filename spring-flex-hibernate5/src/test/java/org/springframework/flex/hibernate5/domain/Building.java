package org.springframework.flex.hibernate5.domain;

import java.util.Set;

import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Building {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Integer id;

    @Embedded
    private EmbeddedAddress address;

    @ElementCollection
    private Set<EmbeddedFloor> floors;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public EmbeddedAddress getAddress() {
        return address;
    }

    public void setAddress(EmbeddedAddress address) {
        this.address = address;
    }

    public void setFloors(Set<EmbeddedFloor> floors) {
        this.floors = floors;
    }

    public Set<EmbeddedFloor> getFloors() {
        return floors;
    }

}
