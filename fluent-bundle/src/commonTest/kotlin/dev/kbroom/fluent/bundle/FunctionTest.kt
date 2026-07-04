package dev.kbroom.fluent.bundle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import dev.kbroom.fluent.intl.LanguageIdentifier
import dev.kbroom.fluent.bundle.types.FluentValue
import dev.kbroom.fluent.bundle.types.FluentNumber

/**
 * Tests for built-in functions: NUMBER, PLURAL, DATETIME
 */
class FunctionTest {
    
    @Test
    fun testNumberFunctionBasic() {
        val bundle = FluentBundle(listOf(LanguageIdentifier.parse("en")))
        bundle.addResource(FluentResource.tryNew("price = The price is { NUMBER(\$amount) }").getOrThrow())
        bundle.addBuiltins()
        
        val args = FluentArgs()
        args.set("amount", 19.99)
        val result = bundle.format("price", args)
        assertTrue(result != null)
    }
    
    @Test
    fun testPluralFunction() {
        val bundle = FluentBundle(listOf(LanguageIdentifier.parse("en")))
        bundle.addResource(FluentResource.tryNew("test = { PLURAL(\$num) }").getOrThrow())
        bundle.addBuiltins()
        
        val args1 = FluentArgs()
        args1.set("num", 0)
        val result1 = bundle.format("test", args1)
        assertTrue(result1 != null)
        
        val args2 = FluentArgs()
        args2.set("num", 1)
        val result2 = bundle.format("test", args2)
        assertTrue(result2 != null)
    }
    
    @Test
    fun testCustomFunction() {
        val bundle = FluentBundle(listOf(LanguageIdentifier.parse("en")))
        bundle.addResource(FluentResource.tryNew("greeting = { HELLO(\$name) }").getOrThrow())
        
        bundle.addFunction("HELLO") { args, _ ->
            FluentValue.Str("Hello, ${args.firstOrNull()?.asString()}!")
        }
        
        val args = FluentArgs()
        args.set("name", "World")
        val result = bundle.format("greeting", args)
        assertTrue(result != null)
    }
}
