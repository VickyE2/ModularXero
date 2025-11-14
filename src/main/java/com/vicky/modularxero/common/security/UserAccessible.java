package com.vicky.modularxero.common.security;

import com.vicky.modularxero.common.util.PossibleAccessionException;

import java.util.Map;

public interface UserAccessible<T, U> {
    PossibleAccessionException<U> attemptLogin(T identificator, String password);
    default PossibleAccessionException<U> attemptCreateAccount(T identificator, String password, String email) {
        return attemptCreateAccount(identificator, password, email, true);
    }
    default PossibleAccessionException<U> attemptCreateAccount(T identificator, String password) {
        return attemptCreateAccount(identificator, password, null, false);
    }
    default PossibleAccessionException<U> attemptCreateAccount(T identificator, String password, String email, boolean useEmail) {
        if (email == null) email = "";
        return attemptCreateAccount(identificator, password, Map.of("email", email, "has_email", useEmail));
    }
    PossibleAccessionException<U> attemptCreateAccount(T identificator, String password, Map<String, Object> otherItems);
}

