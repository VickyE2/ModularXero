package com.vicky.modularxero.common.security;

import com.vicky.modularxero.common.util.PossibleAccessionException;

public interface UserAccessible<T, U> {
    PossibleAccessionException<U> attemptLogin(T identificator, String password);
}

