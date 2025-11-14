package com.vicky.modularxero.modules.bueats.dao;

import com.vicky.modularxero.modules.bueats.models.Cafeteria;
import com.vicky.modularxero.modules.bueats.models.Student;
import com.vicky.modularxero.common.GenericDao;
import com.vicky.modularxero.common.security.PasswordUtil;
import com.vicky.modularxero.common.security.UserAccessible;
import com.vicky.modularxero.common.util.HibernateUtil;
import com.vicky.modularxero.common.util.PossibleAccessionException;

import java.util.Map;

import static com.vicky.modularxero.modules.bueats.BuEatsModule.buSF;

public class StudentDao extends GenericDao<Student, String> implements UserAccessible<String, Student> {

    public StudentDao() {
        super(Student.class, buSF);
    }

    public Student findByUsername(String matricNumber) {
        try (var session = buSF.openSession()) {
            return session.createQuery(
                            "from Student s where s.matricNumber = :sname", Student.class)
                    .setParameter("sname", matricNumber)
                    .uniqueResult();
        }
    }

    @Override
    public PossibleAccessionException<Student> attemptLogin(String matricNumber, String password) {
        Student possibility = findByUsername(matricNumber);
        if (possibility == null) {
            return new PossibleAccessionException<>(false, "Student with matric number `" + matricNumber + "` dosent exist");
        }
        boolean isSamePassword = PasswordUtil.verifyPassword(password, possibility.getPassword());
        if (!isSamePassword) {
            return new PossibleAccessionException<>(false, "Matric number or password is wrong");
        }
        return new PossibleAccessionException<Student>(true, null).setPassableObject(possibility);
    }

    @Override
    public PossibleAccessionException<Student> attemptCreateAccount(String cafNumber, String password, Map<String, Object> otherItems) {
        Student possibility = findByUsername(cafNumber);
        if (possibility == null) {
            if (!((Boolean) otherItems.get("has_email"))) {
                return new PossibleAccessionException<>(false, "No cafeteria with cafName was passed");
            }
            var caf = new Student(cafNumber, (String) otherItems.get("first_name"), (String) otherItems.get("last_name"),
                    password, null /* Too lazy to implement rn.. */);
            save(caf);
            return new PossibleAccessionException<Student>(true, null).setPassableObject(caf);
        }
        return new PossibleAccessionException<>(false, "Person with username `" + cafNumber + "` is already registered");
    }
}
