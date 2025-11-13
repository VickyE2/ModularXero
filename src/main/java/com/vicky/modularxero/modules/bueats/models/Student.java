package com.vicky.modularxero.modules.bueats.models;

import jakarta.persistence.*;

@Entity
@Table(name = "students")
public class Student {
    @Id
    private String matricNumber;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @OneToOne
    @JoinColumn(name = "hostel", referencedColumnName = "hostelId")
    private Hostel studentsHostel;

    // Constructors, getters, setters
    public Student() {}

    public Student(String matricNumber, String firstName, String lastName, String password, Hostel studentsHostel) {
        this.matricNumber = matricNumber;
        this.firstName = firstName;
        this.password = password;
        this.lastName = lastName;
        this.studentsHostel = studentsHostel;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public Hostel getStudentsHostel() {
        return studentsHostel;
    }

    public void setStudentsHostel(Hostel studentsHostel) {
        this.studentsHostel = studentsHostel;
    }

    public void setMatricNumber(String matricNumber) {
        this.matricNumber = matricNumber;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getMatricNumber() {
        return matricNumber;
    }

    public String getPassword() {
        return password;
    }

    public String getLastName() {
        return lastName;
    }
}
