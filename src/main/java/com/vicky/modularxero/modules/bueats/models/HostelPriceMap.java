package com.vicky.modularxero.modules.bueats.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "hostelDeliveryPrice")
public class HostelPriceMap {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String hostelMapUUID;

    @OneToOne
    @JoinColumn(name = "hostel_name", referencedColumnName = "hostelName")
    private Hostel linkedHostel;

    @Column(name = "price")
    private Integer amountInNaira;
    public HostelPriceMap(Hostel hostel, Integer amountInNaira) {
        this.linkedHostel = hostel;
        this.amountInNaira = amountInNaira;
    }

    public Hostel getLinkedHostel() {
        return linkedHostel;
    }

    public Integer getAmountInNaira() {
        return amountInNaira;
    }

    public void setAmountInNaira(Integer amountInNaira) {
        this.amountInNaira = amountInNaira;
    }
}
