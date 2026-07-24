package com.syncari.core.model;

import com.syncari.core.datatype.Datatype;
import com.syncari.core.datatype.DatatypeFactory;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.io.Serializable;

@Getter
@AllArgsConstructor
@EqualsAndHashCode
public class Parameter implements Serializable {
    private String name;
    private Datatype datatype;
    private boolean vararg=false;

    public Parameter(String name, Datatype datatype){
        this.name = name;
        this.datatype = datatype;
    }

    public Parameter(){

    }

    public void setOutputType(String datatype) {
    	this.datatype = DatatypeFactory.getDatatype(datatype);
    }
    
    public void setOutputType(Datatype datatype) {
    	this.datatype = datatype;
    }
}
