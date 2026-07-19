package dev.kbroom.fluent.syntax

import de.infix.testBalloon.framework.core.testSuite
import dev.kbroom.fluent.syntax.parser.FluentParser
import dev.kbroom.fluent.syntax.serializer.SerializerOptions
import dev.kbroom.fluent.syntax.serializer.serialize
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.checkAll
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Property-based tests using kotest-property. The property loop is driven
 * by testBalloon's `test { }` block; kotest's `checkAll` runs the random
 * generation and shrinking inside that block.
 */
val PropertyTest by testSuite {

    /**
     * Valid Fluent message identifiers: starts with `[a-zA-Z_]`, then
     * `[a-zA-Z0-9_-]*`. Mirrors the parser's isIdentifierStart and
     * isIdentifierPart. The generator is intentionally bounded so each
     * iteration stays under a few ms.
     */
    val arbIdentifier: Arb<String> = arbitrary {
        val head = identifierStartChars.random()
        val tailLen = Random.nextInt(0, 9) // 0..8 tail chars
        val tail = (1..tailLen).map { identifierTailChars.random() }.joinToString("")
        head + tail
    }

    /** Safe ASCII value text — letters, digits, and spaces, with at least
     *  one non-space character. Restricting to a small alphabet avoids
     *  special characters (`{`, `}`, `$`, `\`, `.` etc.) that interact
     *  with fluent parsing in ways that legitimately produce Junk
     *  entries (which ParseJunkMessagesTest covers separately). The
     *  non-space requirement ensures the parser sees a non-empty value
     *  pattern rather than trailing whitespace at EOL. */
    val arbValueText: Arb<String> = arbitrary {
        val n = Random.nextInt(1, 17) // total length 1..16 chars
        // First char is non-space
        val head = valueTextChars.filter { c: Char -> c != ' ' }.random()
        // Tail can include spaces
        val tail = (1 until n).map { valueTextChars.random() }
        (listOf(head) + tail).joinToString("")
    }

    test("checkAll: 'id = Text' round-trips structurally for any valid identifier") {
        val parser = FluentParser()
        val opts = SerializerOptions(withJunk = true)
        checkAll(arbIdentifier) { id ->
            val source = "$id = hello"
            val ast1 = parser.parse(source)

            val msgs = ast1.body.filterIsInstance<Entry.Message>()
            assertEquals(1, msgs.size, "expected one Message for input '$source'")
            assertEquals(id, msgs.single().id.name, "id should match the input identifier")

            // Round-trip: serialize with junk, parse, compare message id.
            val ftln = serialize(ast1, opts)
            val ast2 = parser.parse(ftln)
            val idAfter = ast2.body.filterIsInstance<Entry.Message>().single().id.name
            assertEquals(id, idAfter, "id changed across round-trip for '$id'")
        }
    }

    test("checkAll: simple 'id = Text' produces a single-element Pattern") {
        val parser = FluentParser()
        checkAll(arbIdentifier, arbValueText) { id, value ->
            val ast = parser.parse("$id = $value")
            val messages = ast.body.filterIsInstance<Entry.Message>()
            assertEquals(1, messages.size, "expected one Message for '$id = $value' but got: ${ast.body}")
            val msg = messages.single()
            assertEquals(id, msg.id.name)
            val pattern = msg.value
            assertNotNull(pattern, "value Pattern should be present for 'id = value'")
            assertEquals(1, pattern.elements.size, "expected single TextElement")
            assertTrue(
                pattern.elements.single() is PatternElement.TextElement,
                "expected TextElement, got ${pattern.elements.single()::class.simpleName}",
            )
        }
    }

    test("checkAll: parsing twice yields identical ASTs (parser is referentially transparent)") {
        val parser = FluentParser()
        checkAll(arbIdentifier) { id ->
            val source = "$id = Hi there"
            val a = parser.parse(source)
            val b = parser.parse(source)
            val json = kotlinx.serialization.json.Json
            // Encode both and compare — direct equality is the test goal.
            assertEquals(
                json.encodeToJsonElement(Resource.serializer(), a),
                json.encodeToJsonElement(Resource.serializer(), b),
            )
        }
    }
}

private val identifierStartChars: CharArray =
    buildList<Char> {
        addAll(('a'..'z'))
        addAll(('A'..'Z'))
        add('_')
    }.toCharArray()
private val identifierTailChars: CharArray =
    buildList<Char> {
        addAll(('a'..'z'))
        addAll(('A'..'Z'))
        addAll(('0'..'9'))
        add('_')
        add('-')
    }.toCharArray()
private val valueTextChars: CharArray =
    buildList<Char> {
        addAll(('a'..'z'))
        addAll(('A'..'Z'))
        addAll(('0'..'9'))
        add(' ')
    }.toCharArray()
