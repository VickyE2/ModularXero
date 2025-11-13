package com.vicky.modularxero.common;

import com.vicky.modularxero.common.util.HibernateUtil;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;

import java.io.Serializable;
import java.util.List;

public class GenericDao<T, ID extends Serializable> {
    private final Class<T> persistentClass;
    private final SessionFactory sessionFactory;

    public GenericDao(Class<T> type, SessionFactory sf) {
        this.persistentClass = type;
        this.sessionFactory = sf;
    }

    public T findById(ID id) {
        try (Session session = sessionFactory.openSession()) {
            return session.get(persistentClass, id);
        }
    }

    @SuppressWarnings("unchecked")
    public List<T> findAll() {
        try (Session session = sessionFactory.openSession()) {
            return session.createQuery("from " + persistentClass.getName()).list();
        }
    }

    public void save(T entity) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            session.saveOrUpdate(entity);
            tx.commit();
        } catch (Exception e) {
            if (tx != null) tx.rollback();
            throw e;
        }
    }

    public void delete(T entity) {
        Transaction tx = null;
        try (Session session = sessionFactory.openSession()) {
            tx = session.beginTransaction();
            session.delete(entity);
            tx.commit();
        } catch (Exception e) {
            if (tx != null) tx.rollback();
            throw e;
        }
    }
}