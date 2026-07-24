package com.syncari.core.pipeline.jtwig;

import com.syncari.core.pipeline.expression.LiteralExpression;
import com.syncari.core.token.TokenHelper;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

public class JTwigTemplateGenerationVisitorTest {

    private TokenHelper tokenHelper;
    private JTwigTemplateGenerationVisitor visitor;

    @Before
    public void setUp() {
        tokenHelper = mock(TokenHelper.class);
        visitor = new JTwigTemplateGenerationVisitor(tokenHelper);
    }

    @Test
    public void visit_LiteralExpression_WithNullValue_ShouldNotThrowNPE() {
        // Given: a LiteralExpression with null value (isRendered = false)
        LiteralExpression nullLiteralExpression = new LiteralExpression(null, false);

        // When: visiting the expression
        visitor.visit(nullLiteralExpression);

        // Then: should push null to rendered stack without throwing NPE
        String result = visitor.getGeneratedBody();
        assertNull(result);
    }

    @Test
    public void visit_LiteralExpression_WithNullValue_Rendered_ShouldReturnNull() {
        // Given: a LiteralExpression with null value (isRendered = true)
        LiteralExpression nullLiteralExpression = new LiteralExpression(null, true);

        // When: visiting the expression
        visitor.visit(nullLiteralExpression);

        // Then: should push null to rendered stack
        String result = visitor.getGeneratedBody();
        assertNull(result);
    }

    @Test
    public void visit_LiteralExpression_WithStringValue_ShouldReturnQuotedString() {
        // Given: a LiteralExpression with string value
        LiteralExpression stringLiteral = new LiteralExpression("testValue", false);

        // When: visiting the expression
        visitor.visit(stringLiteral);

        // Then: should return quoted string
        String result = visitor.getGeneratedBody();
        assertEquals("\"testValue\"", result);
    }

    @Test
    public void visit_LiteralExpression_WithListValue_ShouldReturnListString() {
        // Given: a LiteralExpression with list value
        LiteralExpression listLiteral = new LiteralExpression(List.of("a", "b", "c"), false);

        // When: visiting the expression
        visitor.visit(listLiteral);

        // Then: should return list as string
        String result = visitor.getGeneratedBody();
        assertEquals("[\"a\", \"b\", \"c\"]", result);
    }

    @Test
    public void visit_LiteralExpression_WithMapValue_ShouldReturnMapString() {
        // Given: a LiteralExpression with map value containing multivaluetext
        Map<String, Object> mapValue = Map.of("multivaluetext", List.of("value1", "value2"));
        LiteralExpression mapLiteral = new LiteralExpression(mapValue, false);

        // When: visiting the expression
        visitor.visit(mapLiteral);

        // Then: should return map as parsable string
        String result = visitor.getGeneratedBody();
        assertNotNull(result);
        assertTrue(result.contains("multivaluetext"));
    }

    @Test
    public void visit_LiteralExpression_WithEmptyStringValue_ShouldReturnQuotedEmptyString() {
        // Given: a LiteralExpression with empty string value
        LiteralExpression emptyStringLiteral = new LiteralExpression("", false);

        // When: visiting the expression
        visitor.visit(emptyStringLiteral);

        // Then: should return quoted empty string
        String result = visitor.getGeneratedBody();
        assertEquals("\"\"", result);
    }

    @Test
    public void visit_LiteralExpression_WithIntegerValue_ShouldReturnQuotedString() {
        // Given: a LiteralExpression with integer value
        // Note: integers get converted to String via toString() before processing,
        // so they end up quoted like strings
        LiteralExpression intLiteral = new LiteralExpression(42, false);

        // When: visiting the expression
        visitor.visit(intLiteral);

        // Then: should return number as quoted string (because it's converted to String first)
        String result = visitor.getGeneratedBody();
        assertEquals("\"42\"", result);
    }
}
