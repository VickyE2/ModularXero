package com.vicky.modularxero.modules.bueats.models;

import java.util.List;
import java.util.stream.Collectors;

import jakarta.persistence.*;


@Entity
@Table(name = "orders")
public class StudentOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String orderID;

    @OneToOne
    @JoinColumn(name = "cafeteria_name", referencedColumnName = "cafeteriaName")
    private Cafeteria linkedCafeteria;

    @OneToOne
    @JoinColumn(name = "student_matric_number", referencedColumnName = "matricNumber")
    private Student linkedStudent;

    @Column
    @OneToMany(cascade = CascadeType.ALL)
    private List<FoodItem> items;
    private Integer quantity;
    private Integer price;
    private String status;
    private Boolean hasTakeaway;
    
    public StudentOrder(Student student, Cafeteria cafeteria) {
        this.linkedCafeteria = cafeteria;
        this.linkedStudent = student;
    }

    public String asjsonString() {
        return new StringBuilder().append("{")
                .append("  \"orderID\" : ").append("\"").append(orderID).append("\"")
                .append("  \"studentMatric\" : ").append("\"").append(linkedStudent.getMatricNumber()).append("\"")
                .append("  \"items\" : ").append(items.stream().map(FoodItem::getFoodName).collect(Collectors.toList()))
                .append("  \"quantity\" : ").append(quantity)
                .append("  \"price\" : ").append(price)
                .append("  \"status\" : ").append("\"").append(status).append("\"")
                .toString();
    }

    public Cafeteria getLinkedCafeteria() {
        return linkedCafeteria;
    }

    public String getOrderID() {
        return orderID;
    }

    public Student getLinkedStudent() {
        return linkedStudent;
    }
}
