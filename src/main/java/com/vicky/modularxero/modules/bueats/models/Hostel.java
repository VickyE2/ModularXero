package com.vicky.modularxero.modules.bueats.models;

import jakarta.persistence.*;

@Entity
@Table(name = "hostels")
public class Hostel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String hostelId;

    @Column(name = "hostelName")
    private String hostelName;

    public Hostel(String hostelName) {
        this.hostelName = hostelName;
    }

    public String getHostelId() {
        return hostelId;
    }

    public String getHostelName() {
        return hostelName;
    }

    public void setHostelName(String hostelName) {
        this.hostelName = hostelName;
    }
}
