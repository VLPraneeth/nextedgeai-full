package com.syncari.core.model.misc;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode
@AllArgsConstructor
public class Transition<T extends Enum<T>> {
    T from;
    T to;
}
