package dev.kbroom.fluent.bundle

import de.infix.testBalloon.framework.core.testSuite
import dev.kbroom.fluent.intl.LanguageIdentifier

/**
 * JVM-only concurrency smoke test: a freshly built [FluentBundle] is safe
 * to call [FluentBundle.format] from many threads simultaneously because
 * its internal state is populated once at build time and never mutated.
 *
 * The bundle internals are backed by the lock-free copy-on-write memoizer
 * (see [IntlLangMemoizer]) and immutable [Map] instances, so concurrent
 * reads need no synchronization.
 */
val BundleConcurrencyTest by testSuite {

    test("format with a freshly built bundle is thread-safe across many threads") {
        val bundle = fluentBundle(listOf(LanguageIdentifier.parse("en")), useIsolating = false) {
            resource("msg = Hello { \$x }")
        }
        val threads = (0 until 8).map {
            Thread {
                repeat(50) {
                    val out = bundle.format("msg", FluentArgs().apply { set("x", "World") })
                    check(out == "Hello World")
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
    }
}
