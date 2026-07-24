package com.syncari.core.datatype;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode
@Data
public class PolymorphicReferenceType extends ReferenceType {
    public static final String NAME = "polymorphicreference";
    public static final PolymorphicReferenceType VALUE = new PolymorphicReferenceType();

    @Override
    public String getName() {
        return NAME;
    }

}
