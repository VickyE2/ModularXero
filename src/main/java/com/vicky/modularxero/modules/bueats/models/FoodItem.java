package com.vicky.modularxero.modules.bueats.models;

import jakarta.persistence.*;

@Entity
@Table(name = "foodItems")
public class FoodItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String foodUUID;

    @Column(nullable = false)
    private String foodName;

    public FoodItem(String foodName) {
        this.foodName = foodName;
    }

    public String getFoodName() {
        return foodName;
    }

    public String getFoodUUID() {
        return foodUUID;
    }

    public void setFoodName(String foodName) {
        this.foodName = foodName;
    }

    public void setFoodUUID(String foodUUID) {
        this.foodUUID = foodUUID;
    }
}
