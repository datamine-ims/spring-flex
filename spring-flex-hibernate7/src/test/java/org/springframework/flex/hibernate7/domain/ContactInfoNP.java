package org.springframework.flex.hibernate7.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Version;

@Entity
public class ContactInfoNP {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    public int id;
    
    @Version
    @Column(name = "version")
    public int version;
    
    public String phone;
    
    public String email;

}
