package com.syncari.core.datatype;

import com.syncari.core.model.AttributeDefinition;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.Assert.*;

public class DataTypeTest {

	@Test
	public void canConvertStringtoURL() {
		assertEquals(new UrlType().convert("xyz"), "xyz");
	}

	@Test
	public void canConvertObjectToUrl() {
		Object xyyz = "xyz";
		assertEquals(new UrlType().convert(xyyz), "xyz");
	}

	@Test
	public void isEmptyDoesNotDoToString() {
		Object a = Mockito.mock(Object.class);
		Mockito.when(a.toString()).thenReturn("");
		assertFalse(ObjectType.VALUE.isEmpty(a));
		//no tostring, but still handles empty strings
		assertTrue(ObjectType.VALUE.isEmpty(""));
		Mockito.verifyZeroInteractions(a);

	}

	@Test
	public void stringEmptiness() {
		assertFalse(StringType.VALUE.isEmpty("Some Value"));
		assertFalse(StringType.VALUE.isEmpty(new Object()));
		assertTrue(StringType.VALUE.isEmpty(null));
		assertTrue(StringType.VALUE.isEmpty(""));

		Object a = Mockito.mock(Object.class);
		Mockito.when(a.toString()).thenReturn("");
		//tostring not called on non-string types
		assertFalse(StringType.VALUE.isEmpty(a));
		Mockito.verifyZeroInteractions(a);

		Object b = Mockito.mock(Object.class);
		Mockito.when(a.toString()).thenReturn("Hello");
		assertFalse(StringType.VALUE.isEmpty(b));
		Mockito.verifyZeroInteractions(a);

	}
	@Test
	public void nonMultivaluedPicklist() {
		AttributeDefinition def = new AttributeDefinition().setDataType(new PicklistType());
		assertEquals("a", def.convert(List.of("a", "b")));
		assertEquals("a", def.convert(List.of("a")));
		assertNull(def.convert(List.of()));
		assertNull(def.convert(null));
	}
	
	@Test
	public void multivaluedPicklist() {
		AttributeDefinition def = new AttributeDefinition().setDataType(new PicklistType()).setMultiValueField(true);
		assertEquals(List.of("a", "b"), def.convert(List.of("a", "b")));
		assertEquals(List.of("a"), def.convert(List.of("a")));
		assertEquals(List.of(), def.convert(List.of()));
		assertNull(def.convert(null));
	}

	@Test
	public void doubleToString() {
		AttributeDefinition def = new AttributeDefinition().setDataType(new StringType());
		Double d = Double.parseDouble("3000002096");
		assertEquals("3000002096", def.convert(d));

		d = Double.parseDouble("0.224234");
		assertEquals(".2242", def.convert(d));

		d = Double.parseDouble("3424.224234");
		assertEquals("3424.2242", def.convert(d));

		d = Double.parseDouble("3424.224234");
		assertEquals("3424.2242", def.convert(d));

		d = Double.parseDouble("242342434324.2245");
		assertEquals("242342434324.2245", def.convert(d));
	}

}