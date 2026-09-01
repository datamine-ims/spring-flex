package org.springframework.flex.hibernate7.domain;

import jakarta.persistence.Embeddable;

@Embeddable
public class EmbeddedFloorNP {

    public Integer units;

    public EmbeddedFloorAttributesNP embeddedFloorAttributes;
}
