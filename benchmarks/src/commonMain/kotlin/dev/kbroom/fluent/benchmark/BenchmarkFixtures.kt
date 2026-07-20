package dev.kbroom.fluent.benchmark

/**
 * Shared FTL fixtures for microbenchmarks.
 *
 * [hundredMessageFtl] is sized to exercise a realistic parse / serialize
 * workload (~100 messages) without depending on the large browser fixtures
 * under `fluent-testing`.
 */
internal object BenchmarkFixtures {
    const val MESSAGE_COUNT: Int = 100

    /** 100 simple messages plus a few placeable / term cases used by format benchmarks. */
    val hundredMessageFtl: String by lazy {
        buildString {
            repeat(MESSAGE_COUNT) { i ->
                append("msg-")
                append(i)
                append(" = Message number ")
                append(i)
                append(" says hello to { \$name }\n")
            }
            append("-brand-name = Fluent\n")
            append("hello = Hello, { \$name }!\n")
            append("welcome = Welcome to { -brand-name }!\n")
        }
    }

    /** Compact FTL used when the benchmark only needs a single placeable pattern. */
    val placeableFtl: String =
        """
        hello = Hello, { ${'$'}name }!
        count-msg = You have { ${'$'}count } items in { -brand }.
        -brand = Fluent-kt
        """.trimIndent()
}
