package com.vicky.modularxero.modules.bueats.dao;

import com.vicky.modularxero.modules.bueats.models.StudentOrder;
import com.vicky.modularxero.modules.bueats.models.Cafeteria;
import com.vicky.modularxero.common.GenericDao;
import com.vicky.modularxero.common.security.PasswordUtil;
import com.vicky.modularxero.common.security.UserAccessible;
import com.vicky.modularxero.common.util.HibernateUtil;
import com.vicky.modularxero.common.util.PossibleAccessionException;

import java.util.List;

import static com.vicky.modularxero.modules.bueats.BuEatsModule.buSF;

public class CafeteriaDao extends GenericDao<Cafeteria, String> implements UserAccessible<String, Cafeteria> {

    public CafeteriaDao() {
        super(Cafeteria.class, buSF);
    }

    public Cafeteria findByUsername(String cafNumber) {
        try (var session = buSF.openSession()) {
            return session.createQuery(
                            "from Cafeteria c where c.cafNumber = :sname", Cafeteria.class)
                    .setParameter("sname", cafNumber)
                    .uniqueResult();
        }
    }

    public List<StudentOrder> getCafeteriaOrders(String cafeteriaNumber) {
        try (var session = buSF.openSession()) {
            return session.createQuery(
                    "from StudentOrder o where o.linkedCafeteria.cafNumber = :cafNo", StudentOrder.class
                    )
                    .setParameter("cafNo", cafeteriaNumber)
                    .getResultList();
        }
    }

    @Override
    public PossibleAccessionException<Cafeteria> attemptLogin(String cafNumber, String password) {
        Cafeteria possibility = findByUsername(cafNumber);
        if (possibility == null) {
            return new PossibleAccessionException<>(false, "No cafeteria with cafNo `" + cafNumber + "` registered");
        }
        boolean isSamePassword = PasswordUtil.verifyPassword(password, possibility.getPassword());
        if (!isSamePassword) {
            return new PossibleAccessionException<>(false, "Caf Number or password is wrong");
        }        
        return new PossibleAccessionException<Cafeteria>(true, null).setPassableObject(possibility);
    }
}
