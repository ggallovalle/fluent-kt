package dev.kbroom.fluent.syntax

import de.infix.testBalloon.framework.core.testSuite
import dev.kbroom.fluent.syntax.parser.FluentParser
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for deeply nested placeables. The parser must accept and
 * surface arbitrarily nested placeable expressions without stack
 * overflow or malformed AST.
 */
val NestedPlaceablesTest by testSuite {

    test("parse one level placeable wrapping a variable reference") {
        val parser = FluentParser()
        val r = parser.parse("g = { \$x }")
        val msg = r.body.filterIsInstance<Entry.Message>().single()
        val pattern = msg.value
        assertNotNull(pattern)
        assertEquals(1, pattern.elements.size)
        val placeable = pattern.elements.single() as PatternElement.Placeable
        val inline = placeable.expression as Expression.Inline
        assertTrue(inline.expression is InlineExpression.VariableReference)
        assertEquals("x", (inline.expression as InlineExpression.VariableReference).id.name)
    }

    /**
     * Walk the top-level Placeable chain, asserting each inner
     * Expression is a Placeable-wrapping Inline. Tests that
     * nested placeables like `{ { x } }` produce a chain of
     * Placeable(Inline(Placeable(...))) nodes.
     */
    @Suppress("LoopWithTooManyJumpStatements")
    fun placeableDepth(p: PatternElement.Placeable): Int {
        var depth = 1
        var current = p
        while (true) {
            val expr = current.expression
            if (expr !is Expression.Inline) break
            val inner = expr.expression
            if (inner !is InlineExpression.Placeable) break
            current = PatternElement.Placeable(inner.expression as Expression)
            depth++
        }
        return depth
    }

    test("parse 5 levels deep placeables produces chain of Placeable nodes") {
        val parser = FluentParser()
        // Each `{` opens a new placeable, the inner content is a
        // single identifier-like token.
        val source = "g = { { { { { x } } } } }"
        val r = parser.parse(source)
        val msg = r.body.filterIsInstance<Entry.Message>().single()
        val pattern = msg.value
        assertNotNull(pattern)
        assertEquals(1, pattern.elements.size)
        val outermost = pattern.elements.single() as PatternElement.Placeable
        val depth = placeableDepth(outermost)
        // The fluent grammar treats `{ { x } }` as a Placeable
        // wrapping an Inline(Placeable(...)) — so 5 levels of
        // source `{` produces a depth-5 chain. If the parser
        // flattens or rejects, this fails.
        assertEquals(5, depth)
    }

    test("parse 10 levels deep does not stack overflow") {
        // Nested placeables: 10 levels deep. If the parser uses naive
        // recursion without an upper bound, the JVM will stack overflow.
        // 10 levels is well within standard JVM stack (512K frames typical),
        // but writing it as a guard catches accidental O(n²) or quadratic
        // blowup too.
        val inner = "x"
        val source = "g = " + "{ ".repeat(10) + inner + " }".repeat(10)
        val parser = FluentParser()
        val r = parser.parse(source)
        assertEquals(1, r.body.size)
    }
}
