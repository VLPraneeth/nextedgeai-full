package com.syncari.restutils.data;

import com.syncari.core.model.InputPort;
import com.syncari.core.model.OutputPort;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;

@Data
@Accessors(chain = true)
@EqualsAndHashCode
public class PortDTO implements Serializable {
    private PortType portType;
    private String datatype;
    private int maxConnections = 1;

    public static PortDTO fromInputPort(InputPort inputPort){
        return new PortDTO().setDatatype(inputPort.getDatatype().getName())
                .setMaxConnections(inputPort.getMaxProducers())
                .setPortType(PortType.INPUT);
    }
    public static PortDTO fromOutputPort(OutputPort outputPort){
        return new PortDTO().setDatatype(outputPort.getDatatype()==null? "string":outputPort.getDatatype().getName())
                .setMaxConnections(outputPort.getMaxConsumers())
                .setPortType(PortType.OUTPUT);
    }
}
