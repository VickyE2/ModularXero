package com.vicky.modularxero.modules.email.dao;

import com.vicky.modularxero.common.GenericDao;
import com.vicky.modularxero.modules.email.EmailModule;
import org.hibernate.SessionFactory;

public class EmailVerificationDao extends GenericDao<EmailModule.EmailVerificationEntity, String> {
    public EmailVerificationDao(SessionFactory sessionFactory) {
        super(EmailModule.EmailVerificationEntity.class, sessionFactory);
    }

    public EmailModule.EmailVerificationEntity findByEmail(String email, String provider) {
        try (var session = sessionFactory.openSession()) {
            return session.byNaturalId(EmailModule.EmailVerificationEntity.class)
                    .using("email", email)
                    .using("provider", provider)
                    .load();
        }
    }
}
