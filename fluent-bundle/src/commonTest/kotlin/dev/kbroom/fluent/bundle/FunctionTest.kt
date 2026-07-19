package dev.kbroom.fluent.bundle

import de.infix.testBalloon.framework.core.testSuite
import dev.kbroom.fluent.bundle.types.FluentValue
import dev.kbroom.fluent.intl.LanguageIdentifier
import kotlin.test.assertTrue

/**
 * Tests for built-in functions: NUMBER, PLURAL, DATETIME
 */
val FunctionTest by testSuite {

    test("number function basic") {
        val bundle = fluentBundle(listOf(LanguageIdentifier.parse("en"))) {
            addResource(FluentResource.tryNew("price = The price is { NUMBER(\$amount) }").getOrThrow())
            addBuiltins()
        }

        val args = FluentArgs()
        args.set("amount", 19.99)
        val result = bundle.format("price", args)
        assertTrue(result != null)
    }

    test("plural function") {
        val bundle = fluentBundle(listOf(LanguageIdentifier.parse("en"))) {
            addResource(FluentResource.tryNew("test = { PLURAL(\$num) }").getOrThrow())
            addBuiltins()
        }

        val args1 = FluentArgs()
        args1.set("num", 0)
        val result1 = bundle.format("test", args1)
        assertTrue(result1 != null)

        val args2 = FluentArgs()
        args2.set("num", 1)
        val result2 = bundle.format("test", args2)
        assertTrue(result2 != null)
    }

    test("custom function") {
        val bundle = fluentBundle(listOf(LanguageIdentifier.parse("en"))) {
            addResource(FluentResource.tryNew("greeting = { HELLO(\$name) }").getOrThrow())
            addFunction("HELLO") { args, _ ->
                FluentValue.Str("Hello, ${args.firstOrNull()?.asString()}!")
            }
        }

        val args = FluentArgs()
        args.set("name", "World")
        val result = bundle.format("greeting", args)
        assertTrue(result != null)
    }
}
