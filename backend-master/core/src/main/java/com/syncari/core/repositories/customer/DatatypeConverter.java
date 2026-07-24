package com.syncari.core.repositories.customer;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import com.syncari.core.datatype.Datatype;
import com.syncari.core.datatype.DatatypeFactory;

@Component
public class DatatypeConverter implements Converter<String, Datatype>{

	@Override
	public Datatype convert(String source) {
		return DatatypeFactory.getDatatype(source);
	}

}
