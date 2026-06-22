
package org.springframework.flex.hibernate5.domain;

import java.util.Date;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class AddressNP {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    public Integer id;

    public String street;

    public String city;

    public String state;

    public String zipcode;

    public Integer rooms;

    public Date moveInDate;
}
