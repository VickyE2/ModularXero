package com.vicky.modularxero.modules.bueats.models;

import com.vicky.modularxero.common.util.PossibleAccessionException;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.*;

@Entity
@Table(name = "CafFoodItemMap")
public class CafFoodItemPriceMap {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String cafToFoodMapId;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "map_id") // foreign key in FoodItem table
    private List<FoodItem> foodItems = new ArrayList<>();

    public PossibleAccessionException<FoodItem> addFoodItem(FoodItem possibleFood) {
        if (foodItems.stream().anyMatch(item -> item.getFoodName().equals(possibleFood.getFoodName()))) {
            return new PossibleAccessionException<>(false, "There is a food item with the same name already present.");
        }
        else {
            foodItems.add(possibleFood);
            return new PossibleAccessionException<>(true, null);
        }
    }
}
