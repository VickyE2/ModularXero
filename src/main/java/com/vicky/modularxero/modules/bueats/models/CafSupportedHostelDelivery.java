package com.vicky.modularxero.modules.bueats.models;

import com.vicky.modularxero.common.util.PossibleAccessionException;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "CafHostelDeliveryMap")
public class CafSupportedHostelDelivery {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String cafToHostelMapId;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "map_id") // foreign key in FoodItem table
    private List<HostelPriceMap> hostelPriceMaps = new ArrayList<>();

    @OneToOne
    @JoinColumn(name = "cafeteria_id", referencedColumnName = "cafNumber")
    private Cafeteria linkedCafeteria;

    public PossibleAccessionException<FoodItem> addHostelDelivery(HostelPriceMap possibleHostel) {
        if (hostelPriceMaps.stream().anyMatch(item -> item.getLinkedHostel().equals(possibleHostel.getLinkedHostel()))) {
            return new PossibleAccessionException<>(false, "There is an hostel delivery already present.");
        }
        else {
            hostelPriceMaps.add(possibleHostel);
            return new PossibleAccessionException<>(true, null);
        }
    }
}
