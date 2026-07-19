package dev.kbroom.fluent.bundle

import de.infix.testBalloon.framework.core.testSuite
import dev.kbroom.fluent.intl.LanguageIdentifier
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.checkAll
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Property-based tests using kotest-property. Each test exercises an
 * invariant across an input space; kotest shrinks to a minimal failing
 * case on regression.
 */
val BundlePropertyTest by testSuite {

    /**
     * Valid Fluent message identifiers: starts with `[a-zA-Z_]`, then
     * `[a-zA-Z0-9_-]*`. Same grammar as the parser's isIdentifier* rules.
     */
    val arbIdentifier: Arb<String> = arbitrary {
        val head = identifierStartChars.random()
        val tailLen = Random.nextInt(0, 9) // 0..8 tail chars
        val tail = (1..tailLen).map { identifierTailChars.random() }.joinToString("")
        head + tail
    }

    /**
     * Non-empty value text from a small alphabet (letters, digits,
     * spaces; first char non-space) — restricts to inputs the parser
     * accepts as a non-empty text value (the broken-input cases are
     * covered separately by ParseJunkMessagesTest).
     */
    val arbValueText: Arb<String> = arbitrary {
        val n = Random.nextInt(1, 17)
        val head = valueTextChars.filter { c: Char -> c != ' ' }.random()
        val tail = (1 until n).map { valueTextChars.random() }
        (listOf(head) + tail).joinToString("")
    }

    test("checkAll: addResource then getMessage retrieves the same message") {
        // The invariant from todo/02-test-coverage.md: 'addResource
        // followed by getMessage always retrieves the same message'.
        checkAll(arbIdentifier, arbValueText) { id, value ->
            val bundle = FluentBundle(listOf(LanguageIdentifier.parse("en")))
            val result = bundle.addResource(
                FluentResource.tryNew("$id = $value").getOrThrow(),
            )
            assertTrue(result.isSuccess, "addResource should succeed for valid FTL")

            val message = bundle.getMessage(id)
            assertNotNull(message, "getMessage($id) should return a value after addResource")

            // The underlying FluentMessage.id() returns the same string
            assertEquals(id, message.id())

            // format() should produce the same text we put in. Note
            // isolation marks are NOT added on plain ASCII content;
            // Unicode isolation is opt-out and not asserted here.
            val formatted = bundle.format(id)
            assertNotNull(formatted, "format($id) should not return null")
            assertTrue(
                value in formatted,
                "formatted output '$formatted' should contain input value '$value'",
            )
        }
    }

    test("checkAll: getMessage returns null for any id NOT added") {
        // For any random identifier, if we never add it to the bundle,
        // getMessage returns null. This is the inverse invariant to the
        // above — together they pin down the mapping.
        checkAll(arbIdentifier) { id ->
            val bundle = FluentBundle(listOf(LanguageIdentifier.parse("en")))
            assertEquals(null, bundle.getMessage(id), "getMessage($id) on empty bundle")
            assertEquals(null, bundle.format(id), "format($id) on empty bundle")
            assertEquals(false, bundle.hasMessage(id), "hasMessage($id) on empty bundle")
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
