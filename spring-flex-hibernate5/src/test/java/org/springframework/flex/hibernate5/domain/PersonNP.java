
package org.springframework.flex.hibernate5.domain;

import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Version;

@Entity
public class PersonNP {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    public Integer id;

    @Version
    @Column(name = "version")
    public Integer version;

    public String name;

    @OneToOne(fetch = FetchType.LAZY)
    public PersonNP spouse;

    @OneToOne
    public AddressNP address;

    @OneToMany
    public Set<AddressNP> previousAddresses;

    @ManyToMany
    public Set<PersonNP> children;

}
