package com.vicky.modularxero.modules.bueats.dao;

import com.vicky.modularxero.modules.bueats.models.Student;
import com.vicky.modularxero.common.GenericDao;
import com.vicky.modularxero.common.security.PasswordUtil;
import com.vicky.modularxero.common.security.UserAccessible;
import com.vicky.modularxero.common.util.HibernateUtil;
import com.vicky.modularxero.common.util.PossibleAccessionException;

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
}
