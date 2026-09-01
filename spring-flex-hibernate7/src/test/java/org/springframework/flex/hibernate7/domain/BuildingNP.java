package org.springframework.flex.hibernate7.domain;

import java.util.Set;

import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class BuildingNP {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    public Integer id;

    @Embedded
    public EmbeddedAddressNP address;

    @ElementCollection
    public Set<EmbeddedFloorNP> floors;
}
