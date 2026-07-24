package com.syncari.core.datatype;

import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

@EqualsAndHashCode
@Slf4j
public class CombolistType extends PicklistType {
    public static final CombolistType VALUE = new CombolistType();

    @Override
    public String getName() {
        return "combolist";
    }
}
