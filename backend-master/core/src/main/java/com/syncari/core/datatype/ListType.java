package com.syncari.core.datatype;

import lombok.EqualsAndHashCode;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@EqualsAndHashCode
public class ListType extends AbstractDataType<List> {
	public static final ListType VALUE= new ListType();
	public static final String NAME="list";
	public static final Map<Class<?>, Function<Object, List>> CONVERTERS = Map.of(
			Object.class, value -> value==null? List.of():List.of(value)
	);

	@Override
	public String getName() {
		return "list";
	}

	@Override
	public Class<List> getJavaType() {
		return List.class;
	}

	@Override
	protected List nullEquivalent() {
		return Collections.emptyList();
	}

	@Override
	public boolean canConvert(Datatype other) {
		return true;
	}
	@Override
	protected Map<Class<?>, Function<Object, List>> getConverters() {
		return CONVERTERS;
	}
}
