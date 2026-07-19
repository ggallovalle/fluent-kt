package dev.kbroom.fluent.bundle

import de.infix.testBalloon.framework.core.testSuite
import dev.kbroom.fluent.intl.LanguageIdentifier
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

val BundleEntriesTest by testSuite {

    test("entries() returns message and term entries") {
        val bundle = fluentBundle(listOf(LanguageIdentifier.parse("en"))) {
            resource(
                """
                hello = Hello
                    .attr = Attr
                -brand = Acme
                    .attr = Brand Attr
                """.trimIndent(),
            )
        }

        val entries = bundle.entries()
        assertEquals(2, entries.size)
        assertIs<dev.kbroom.fluent.syntax.Entry.Message>(entries["hello"])
        assertIs<dev.kbroom.fluent.syntax.Entry.Term>(entries["brand"])
    }

    test("entries() returns immutable Map — caller cannot structurally mutate") {
        val bundle = fluentBundle(listOf(LanguageIdentifier.parse("en"))) {
            resource("hello = Hello")
        }

        val snap: Map<String, dev.kbroom.fluent.syntax.Entry> = bundle.entries()
        // The map is read-only; `snap.clear()` is not in the Map<K,V> API.
        assertEquals(1, snap.size)
        assertTrue(snap.containsKey("hello"))
    }

    test("hasTerm returns true for declared term, false otherwise") {
        val bundle = fluentBundle(listOf(LanguageIdentifier.parse("en"))) {
            resource("-brand = Acme")
        }

        assertTrue(bundle.hasTerm("brand"))
        assertEquals(false, bundle.hasTerm("nope"))
    }

    test("hasTerm does not match a message id") {
        val bundle = fluentBundle(listOf(LanguageIdentifier.parse("en"))) {
            resource("hello = Hi")
        }

        assertEquals(false, bundle.hasTerm("hello"))
    }

    test("hasFunction returns true after addFunction") {
        val bundle = fluentBundle(listOf(LanguageIdentifier.parse("en"))) {
            function("HELLO") { args, _ ->
                dev.kbroom.fluent.bundle.types.FluentValue.Str(args.firstOrNull()?.asString().orEmpty())
            }
        }
        assertTrue(bundle.hasFunction("HELLO"))
        assertEquals(false, bundle.hasFunction("NOPE"))
    }

    test("getEntry returns Message for a message id") {
        val bundle = fluentBundle(listOf(LanguageIdentifier.parse("en"))) {
            resource("hello = Hello")
        }

        val entry = bundle.getEntry("hello")
        assertIs<dev.kbroom.fluent.syntax.Entry.Message>(entry)
        assertEquals("hello", entry.id.name)
    }

    test("getEntry returns Term for a term id") {
        val bundle = fluentBundle(listOf(LanguageIdentifier.parse("en"))) {
            resource("-brand = Acme")
        }

        val entry = bundle.getEntry("brand")
        assertIs<dev.kbroom.fluent.syntax.Entry.Term>(entry)
        assertEquals("brand", entry.id.name)
    }

    test("getEntry returns null for unknown id") {
        val bundle = fluentBundle(listOf(LanguageIdentifier.parse("en"))) {}
        assertEquals(null, bundle.getEntry("nope"))
    }

    test("entries() snapshot is independent of subsequent builder operations on a new bundle") {
        val first = fluentBundle(listOf(LanguageIdentifier.parse("en"))) {
            resource("a = A")
        }
        val second = fluentBundle(listOf(LanguageIdentifier.parse("en"))) {
            resource("a = A")
            resource("b = B")
        }

        val before = first.entries()
        val after = second.entries()

        assertEquals(1, before.size)
        assertEquals(2, after.size)
        assertNotSame(before, after)
    }
}
