package com.syncari.utils;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@SpringBootTest
@ContextConfiguration(classes = TestConfig.class, loader = AnnotationConfigContextLoader.class)
public class TextUtilTest {

    @Autowired
    TextUtil textUtil;

    @Test
    public void createApiName(){
        assertEquals("nested_field", textUtil.createApiName("Nested Field"));
        assertEquals("nested_field", textUtil.createApiName("Nested   Field"));
        assertEquals("nested-field", textUtil.createApiName("Nested-Field"));
        assertEquals("nested_field", textUtil.createApiName("nested_Field"));
        assertEquals("nested_field", textUtil.createApiName("  Nested   Field  "));
        assertEquals("nested_field", textUtil.createApiName("NESTED ** \\\\   Field ! "));
    }

    @Test
    public void isValidApiName(){
        assertFalse(textUtil.isValidApiName("Nested Field"));
        assertFalse(textUtil.isValidApiName("Nested_Field!@"));
        assertTrue(textUtil.isValidApiName("Nested-Field"));
        assertTrue(textUtil.isValidApiName("NestedField"));
        assertTrue(textUtil.isValidApiName("nested_Field"));
        assertTrue(textUtil.isValidApiName("nested_Fiel+d"));
        assertTrue(textUtil.isValidApiName("_nested_Field"));
        assertTrue(textUtil.isValidApiName("nested_Field_"));
        assertTrue(textUtil.isValidApiName("-nested_Field"));
    }

    @Test
    public void isValidEmail(){
        assertTrue(TextUtil.isValidEmail("valid@email.com"));
        assertTrue(TextUtil.isValidEmail("valid@email.net"));
        assertTrue(TextUtil.isValidEmail("valid@syncari.org"));
        assertFalse(TextUtil.isValidEmail("valid@email.commmmmm"));
        assertFalse(TextUtil.isValidEmail("@email.com"));
        assertTrue(TextUtil.isValidEmail("valid@email.co.us"));
        assertFalse(TextUtil.isValidEmail("INVALID"));
        assertFalse(TextUtil.isValidEmail("valid-syncari.com"));
        assertFalse(TextUtil.isValidEmail("valid@syncari@gmail.com"));
        assertFalse(TextUtil.isValidEmail(""));
        assertFalse(TextUtil.isValidEmail(null));
        assertTrue(TextUtil.isValidEmail("valid+tag@email.com"));
    }

    @Test
    public void validateRegex(){
        assertTrue("select * from oppty where name like \" abc\\s \"".matches(".*(?<!\\\\)\\\\(?!\\\\).*"));
        assertTrue("select * from oppty where name like \" abc\\s \" and name like \" abc\\\\s \"".matches(".*(?<!\\\\)\\\\(?!\\\\).*"));
        assertFalse("select * from oppty where name like \" abc\\\\s \"".matches(".*(?<!\\\\)\\\\(?!\\\\).*"));

    }
}
