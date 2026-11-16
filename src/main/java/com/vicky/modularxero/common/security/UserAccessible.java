package com.vicky.modularxero.common.security;

import com.vicky.modularxero.common.util.PossibleAccessionException;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public interface UserAccessible<T, U> {
    PossibleAccessionException<U> attemptLogin(@NotNull T identificator, @NotNull String password);
    default PossibleAccessionException<U> attemptCreateAccount(@NotNull T identificator, @NotNull String password, @NotNull String email) {
        return attemptCreateAccount(identificator, password, email, true);
    }
    default PossibleAccessionException<U> attemptCreateAccount(@NotNull T identificator, @NotNull String password) {
        return attemptCreateAccount(identificator, password, null, false);
    }
    default PossibleAccessionException<U> attemptCreateAccount(@NotNull T identificator, @NotNull String password, String email, boolean useEmail) {
        if (email == null) email = "";
        return attemptCreateAccount(identificator, password, Map.of("email", email, "has_email", useEmail));
    }
    PossibleAccessionException<U> attemptCreateAccount(@NotNull T identificator, @NotNull String password, @NotNull Map<String, Object> otherItems);
}

