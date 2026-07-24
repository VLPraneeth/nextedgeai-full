package com.syncari.core.model;

import com.syncari.core.datatype.Datatype;
import com.syncari.core.datatype.ObjectType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@AllArgsConstructor
@Data
@Accessors(chain = true)
@EqualsAndHashCode(exclude = "maxProducers")
public class InputPort {

    private Datatype datatype;
    private int maxProducers = 1;

    public static InputPort many(Datatype datatype) {
        return new InputPort(datatype, Integer.MAX_VALUE);
    }
    public static InputPort many() {
        return new InputPort(new ObjectType(), Integer.MAX_VALUE);
    }

    public static InputPort any() {
        return new InputPort(new ObjectType(), 1);
    }

    public static InputPort of(Datatype datatype) {
        return new InputPort(datatype, 1);
    }

}
