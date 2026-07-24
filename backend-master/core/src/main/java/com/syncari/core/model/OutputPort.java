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
@EqualsAndHashCode(exclude = "maxConsumers")
public class OutputPort {
    private Datatype datatype;
    private int maxConsumers = 1;

    public static OutputPort any() {
        return new OutputPort(new ObjectType(), 1);
    }
    public static OutputPort of(Datatype datatype) {
        return new OutputPort(datatype, 1);
    }
    public static OutputPort many() {
        return new OutputPort(new ObjectType(), Integer.MAX_VALUE);
    }
    public static OutputPort many(Datatype datatype) {
        return new OutputPort(datatype, Integer.MAX_VALUE);
    }
}
