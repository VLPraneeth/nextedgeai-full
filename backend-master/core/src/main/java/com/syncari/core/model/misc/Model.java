package com.syncari.core.model.misc;

import lombok.experimental.SuperBuilder;

import java.io.Serializable;
@SuperBuilder(toBuilder = true)
public abstract class Model implements Serializable {
    public Model(){

    }
}
