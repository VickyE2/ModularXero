package com.vicky.modularxero.common.util;

import org.jetbrains.annotations.Nullable;

public class PossibleAccessionException<T> {

    private final boolean wasAddedSuccseffuly;
    private final String reason;
    private T passableObject;

    public PossibleAccessionException(boolean wasAddedSuccseffuly, @Nullable String reason) {
        this.reason = reason;
        this.wasAddedSuccseffuly = wasAddedSuccseffuly;
    }

    public PossibleAccessionException<T> setPassableObject(T passableObject) {
        this.passableObject = passableObject;
        return this;
    }

    public T getPassableObject() {
        return passableObject;
    }

    public String getReason() {
        if (reason == null && wasAddedSuccseffuly) {
            return "Successfully added...";
        }
        else if (reason == null) {
            return "Failed to add...";
        }
        return reason;
    }

    public boolean isAddedSuccseffuly() {
        return wasAddedSuccseffuly;
    }
}
