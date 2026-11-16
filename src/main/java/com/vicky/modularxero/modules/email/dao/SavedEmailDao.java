package com.vicky.modularxero.modules.email.dao;

import com.vicky.modularxero.common.GenericDao;
import com.vicky.modularxero.modules.email.EmailModule;
import org.hibernate.SessionFactory;

import static com.vicky.modularxero.modules.bueats.BuEatsModule.buSF;

public class SavedEmailDao extends GenericDao<EmailModule.SentEmails, String> {
    public SavedEmailDao(SessionFactory sessionFactory) {
        super(EmailModule.SentEmails.class, sessionFactory);
    }

    public EmailModule.SentEmails findByEmail(String email) {
        try (var session = sessionFactory.openSession()) {
            return session.byNaturalId(EmailModule.SentEmails.class)
                    .using("email", email)
                    .load();
        }
    }
}
