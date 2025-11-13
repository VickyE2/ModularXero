package com.vicky.modularxero.modules.bueats.models;

import jakarta.persistence.*;

@Entity
@Table(name = "cafeterias")
public class Cafeteria {
    @Id
    private String cafNumber;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String cafeteriaName;

    @Column(nullable = false)
    private Double earnings;

    public Cafeteria(String cafNumber, String password, String cafeteriaName) {
        this.cafeteriaName = cafeteriaName;
        this.password = password;
        this.cafNumber = cafNumber;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setCafeteriaName(String cafeteriaName) {
        this.cafeteriaName = cafeteriaName;
    }

    public void setCafNumber(String cafNumber) {
        this.cafNumber = cafNumber;
    }

    public String getPassword() {
        return password;
    }

    public String getCafeteriaName() {
        return cafeteriaName;
    }

    public String getCafNumber() {
        return cafNumber;
    }

    public Double getEarnings() {
        return earnings;
    }
}
