# Detekt Complexity Refactor Plan

> **For Hermes:** Use subagent-driven-development to execute this plan task-by-task.
> Each task ends with a commit; the next task starts from green.

**Goal:** Bring `./gradlew detektAll` to a clean exit (zero issues, `warningsAsErrors: true`) by refactoring the 27 remaining complexity hotspots left over from the bulk auto-correct pass.

**Architecture:** Extract small focused helpers and collaborators from the 4 over-burdened files (`FluentParser`, `Resolver`, `FluentValue`, `LanguageIdentifier`, `PseudoLocale`) so each function/method lands under the detekt thresholds defined in `detekt.yml` (`LongMethod: 60`, `CyclomaticComplexMethod: 14`, `ReturnCount: 4`, `LongParameterList: 6`, `LoopWithTooManyJumpStatements: 1`, `TooManyFunctions: 15` per class). No behavior changes. Existing tests are the safety net — every refactor must keep the current `commonTest` green, and we add focused tests for any helper with non-trivial branching.

**Tech Stack:** Kotlin 2.4.0, testBalloon 1.0.1-K2.4.0, detekt 2.0.0-alpha.5 with `detekt-rules-ktlint-wrapper`.

**Current lint status** (from the last `detektAll` run, 27 issues):

| File | Rule | Line | Notes |
|---|---|---|---|
| `fluent-syntax/.../parser/FluentParser.kt` | `TooManyFunctions` | 47 | 34 functions in one class |
| `fluent-syntax/.../parser/FluentParser.kt` | `LargeClass` | 47 | 1054 lines |
| `fluent-syntax/.../parser/FluentParser.kt` | `LongMethod` | 53 | `parse` (64 lines) |
| `fluent-syntax/.../parser/FluentParser.kt` | `CyclomaticComplexMethod` | 53 | `parse` (complexity 20) |
| `fluent-syntax/.../parser/FluentParser.kt` | `LoopWithTooManyJumpStatements` | 61 | 6 in `parse` |
| `fluent-syntax/.../parser/FluentParser.kt` | `CyclomaticComplexMethod` | 134 | `parseComment` (26) |
| `fluent-syntax/.../parser/FluentParser.kt` | `LoopWithTooManyJumpStatements` | 163 | in `parseComment` |
| `fluent-syntax/.../parser/FluentParser.kt` | `LoopWithTooManyJumpStatements` | 376 | in `parseMessage` attribute loop |
| `fluent-syntax/.../parser/FluentParser.kt` | `LoopWithTooManyJumpStatements` | 429 | in `parseTerm` attribute loop |
| `fluent-syntax/.../parser/FluentParser.kt` | `CyclomaticComplexMethod` | 448 | `parsePattern` (15) |
| `fluent-syntax/.../parser/FluentParser.kt` | `LoopWithTooManyJumpStatements` | 454 | in `parsePattern` |
| `fluent-syntax/.../parser/FluentParser.kt` | `ComplexCondition` | 555 | 4 conditions in `parseExpression` |
| `fluent-syntax/.../parser/FluentParser.kt` | `CyclomaticComplexMethod` | 611 | `parseVariantKey` (16) |
| `fluent-syntax/.../parser/FluentParser.kt` | `ComplexCondition` | 815 | 7 in `parseNumberLiteral` while |
| `fluent-syntax/.../parser/FluentParser.kt` | `ComplexCondition` | 911 | 5 in `skipWhitespace` while |
| `fluent-syntax/.../parser/FluentParser.kt` | `CyclomaticComplexMethod` | 994 | `ErrorKind.toString` (26) |
| `fluent-bundle/.../bundle/resolver/Resolver.kt` | `CyclomaticComplexMethod` | 100 | `resolve` (15) |
| `fluent-bundle/.../bundle/resolver/Resolver.kt` | `ReturnCount` | 215 | `resolveMessageReference` (6) |
| `fluent-bundle/.../bundle/resolver/Resolver.kt` | `LongMethod` | 265 | `resolveTermReference` (76) |
| `fluent-bundle/.../bundle/resolver/Resolver.kt` | `CyclomaticComplexMethod` | 265 | `resolveTermReference` (24) |
| `fluent-bundle/.../bundle/resolver/Resolver.kt` | `ReturnCount` | 265 | `resolveTermReference` (5) |
| `fluent-bundle/.../bundle/types/FluentValue.kt` | `CyclomaticComplexMethod` | 151 | `getPluralCategory` (22) |
| `fluent-pseudo/.../pseudo/PseudoLocale.kt` | `LoopWithTooManyJumpStatements` | 84, 136, 174 | 3x in `transformAccented` / `transformWidened` / `transformHidden` |
| `intl-memoizer/.../intl/LanguageIdentifier.kt` | `CyclomaticComplexMethod` | 20 | `parse` (18) |

**Strategy:** Group into 5 themed sub-plans. Each sub-plan is a TDD-style task sequence: failing test → implementation → green → commit. Tests are written first only for the new helpers with non-trivial branching (per the TDD skill's "real code, not mocks" rule). Existing tests must stay green throughout — they're the regression net.

**Sub-plans** (each lands with its own commits; run `./gradlew detektAll --no-build-cache --rerun-tasks` after each):

1. **SP1: `getPluralCategory` decomposition** (FluentValue.kt, 1 issue)
2. **SP2: `LanguageIdentifier.parse` decomposition** (1 issue)
3. **SP3: `PseudoLocale` per-character loop → fold-based transform** (3 issues)
4. **SP4: `Resolver` complexity — split `resolve` and `resolveTermReference`** (5 issues)
5. **SP5: `FluentParser` extraction — split into `FluentParser` + `CommentParser` + `PatternParser` + per-type helpers** (16 issues)

A final verification task runs the full lint + test sequence.

---

## SP1: Decompose `getPluralCategory` (FluentValue.kt:151)

**Why:** `getPluralCategory` (22 complexity) is a top-level `fun` with a 4-arm `when` where each arm is a locale family with its own plural rule. The fix is to dispatch the locale family and let each family be its own function. Tests go in `fluent-bundle/src/commonTest/.../TypesTest.kt` (testBalloon `testSuite`). Detekt config: nothing changes — these are pure refactors that drop complexity.

### Task SP1-T1: Add `pluralCategoryFor*` helpers with failing tests

**Files:**
- Modify: `fluent-bundle/src/commonMain/kotlin/dev/kbroom/fluent/bundle/types/FluentValue.kt:151-179` (add helpers, keep `getPluralCategory` as a thin dispatcher)
- Modify: `fluent-bundle/src/commonTest/kotlin/dev/kbroom/fluent/bundle/TypesTest.kt` (append tests)

**Step 1: Write failing tests for the locale-family helpers**

Append to `TypesTest.kt` inside the `testSuite` block (right before the closing `}`):

```kotlin
    test("plural category: english one/other") {
        assertEquals(PluralCategory.ONE, getPluralCategory(1.0, "en-US"))
        assertEquals(PluralCategory.OTHER, getPluralCategory(0.0, "en-US"))
        assertEquals(PluralCategory.OTHER, getPluralCategory(2.0, "en-US"))
    }

    test("plural category: slavic one/few/many/other") {
        assertEquals(PluralCategory.ONE, getPluralCategory(1.0, "ru-RU"))
        assertEquals(PluralCategory.FEW, getPluralCategory(2.0, "ru-RU"))
        assertEquals(PluralCategory.MANY, getPluralCategory(5.0, "ru-RU"))
        assertEquals(PluralCategory.OTHER, getPluralCategory(0.0, "ru-RU"))
        assertEquals(PluralCategory.MANY, getPluralCategory(11.0, "ru-RU"))
    }

    test("plural category: arabic zero/one/two/few/many/other") {
        assertEquals(PluralCategory.ZERO, getPluralCategory(0.0, "ar"))
        assertEquals(PluralCategory.ONE, getPluralCategory(1.0, "ar"))
        assertEquals(PluralCategory.TWO, getPluralCategory(2.0, "ar"))
        assertEquals(PluralCategory.FEW, getPluralCategory(3.0, "ar"))
        assertEquals(PluralCategory.MANY, getPluralCategory(11.0, "ar"))
        assertEquals(PluralCategory.OTHER, getPluralCategory(100.0, "ar"))
    }

    test("plural category: unknown locale falls back to one/other") {
        assertEquals(PluralCategory.ONE, getPluralCategory(1.0, "xx-YY"))
        assertEquals(PluralCategory.OTHER, getPluralCategory(0.0, "xx-YY"))
    }
```

Add to the imports block of `TypesTest.kt`:
```kotlin
import dev.kbroom.fluent.bundle.types.getPluralCategory
import dev.kbroom.fluent.bundle.types.PluralCategory
```

**Step 2: Run tests to verify failure**

```bash
./gradlew :fluent-bundle:jvmTest --no-build-cache --rerun-tasks --console=plain 2>&1 | tail -20
```

Expected: build fails because the new `pluralCategoryFor*` helpers don't exist. The 4 new tests reference only `getPluralCategory` (the public API) so the compile passes — but the `getPluralCategory` we have still has 22 complexity, the tests are the regression net for the refactor itself. **To make the test-first rule actually fail-then-pass, do this in two commits:**

- Commit A: the new tests only. They pass against the current code (regression net in place). Commit `test(bundle): add plural-category regression tests`.
- Commit B: the refactor (next task) is what makes complexity drop.

```bash
git add fluent-bundle/src/commonTest/kotlin/dev/kbroom/fluent/bundle/TypesTest.kt
git commit -m "test(bundle): add plural-category regression tests for en/ru/ar/fallback"
```

### Task SP1-T2: Refactor `getPluralCategory` into per-family helpers

**Files:**
- Modify: `fluent-bundle/src/commonMain/kotlin/dev/kbroom/fluent/bundle/types/FluentValue.kt:148-179`

**Step 1: Replace the function body**

In `FluentValue.kt`, replace the existing `getPluralCategory` (lines 148–179) with this split version. Keep the same public signature so call sites don't change:

```kotlin
/**
 * Get plural category for a number in a given locale.
 */
fun getPluralCategory(value: Double, locale: String): PluralCategory {
    val intValue = floor(value).toInt()
    return when {
        locale.startsWith("en") -> pluralCategoryEnglish(intValue)
        locale.startsWith("ru") || locale.startsWith("uk") -> pluralCategorySlavic(intValue)
        locale.startsWith("ar") -> pluralCategoryArabic(intValue)
        else -> pluralCategoryDefault(intValue)
    }
}

private fun pluralCategoryEnglish(intValue: Int): PluralCategory = when (intValue) {
    1 -> PluralCategory.ONE
    else -> PluralCategory.OTHER
}

private fun pluralCategorySlavic(intValue: Int): PluralCategory = when {
    intValue % 10 == 1 && intValue % 100 != 11 -> PluralCategory.ONE
    intValue % 10 in 2..4 && intValue % 100 !in 12..14 -> PluralCategory.FEW
    intValue % 10 == 0 || intValue % 10 in 5..9 || intValue % 100 in 11..14 -> PluralCategory.MANY
    else -> PluralCategory.OTHER
}

private fun pluralCategoryArabic(intValue: Int): PluralCategory = when (intValue) {
    0 -> PluralCategory.ZERO
    1 -> PluralCategory.ONE
    2 -> PluralCategory.TWO
    in 3..10 -> PluralCategory.FEW
    in 11..99 -> PluralCategory.MANY
    else -> PluralCategory.OTHER
}

private fun pluralCategoryDefault(intValue: Int): PluralCategory = when (intValue) {
    1 -> PluralCategory.ONE
    else -> PluralCategory.OTHER
}
```

**Step 2: Run tests + lint to verify**

```bash
./gradlew :fluent-bundle:jvmTest --no-build-cache --rerun-tasks --console=plain 2>&1 | tail -10
./gradlew :fluent-bundle:detektCommonMainSourceSet --no-build-cache --rerun-tasks --console=plain 2>&1 | tail -10
```

Expected: tests pass, `getPluralCategory` CyclomaticComplexMethod issue gone.

**Step 3: Commit**

```bash
git add fluent-bundle/src/commonMain/kotlin/dev/kbroom/fluent/bundle/types/FluentValue.kt
git commit -m "refactor(bundle): split getPluralCategory into per-family helpers"
```

---

## SP2: Decompose `LanguageIdentifier.parse` (LanguageIdentifier.kt:20)

**Why:** The `parse` function is one big `while` over the BCP 47 sub-tags with a multi-arm `when` (script/region/variant/extension) plus a nested extension loop. Splitting by sub-tag type drops the complexity from 18 to single digits. No new test file needed — `LanguageIdentifier.parse` is exercised transitively by every `commonTest` that uses `LanguageIdentifier.parse("en-US")` etc.; the existing `fluent-bundle`, `fluent-resmgr`, `fluent-pseudo`, and `fluent-testing` tests cover it.

### Task SP2-T1: Extract per-subtag classifiers

**Files:**
- Modify: `intl-memoizer/src/commonMain/kotlin/dev/kbroom/fluent/intl/LanguageIdentifier.kt:20-69`

**Step 1: Refactor `parse` to delegate classification**

Replace `parse` (lines 20-69) with:

```kotlin
        /**
         * Parse a BCP 47 language tag into a LanguageIdentifier.
         */
        fun parse(tag: String): LanguageIdentifier {
            val parts = tag.split("-")
            val language = parts.getOrNull(0)?.lowercase() ?: ""

            var script: String? = null
            var region: String? = null
            val variants = mutableListOf<String>()
            val extensions = mutableMapOf<String, String>()

            var i = 1
            while (i < parts.size) {
                val part = parts[i]
                val classification = classifySubtag(part, parts, i)
                when (classification.kind) {
                    SubtagKind.SCRIPT -> script = part.replaceFirstChar { it.titlecase() }
                    SubtagKind.REGION -> region = regionValue(part)
                    SubtagKind.VARIANT -> variants.add(part)
                    SubtagKind.EXTENSION -> {
                        extensions[classification.extKey!!] = classification.extValue!!
                        i = classification.nextIndex
                        continue
                    }
                    SubtagKind.UNKNOWN -> { /* skip */ }
                }
                i++
            }

            return LanguageIdentifier(language, script, region, variants, extensions)
        }

        private fun regionValue(part: String): String =
            if (part.length == 2) part.uppercase() else part

        private fun classifySubtag(
            part: String,
            parts: List<String>,
            index: Int,
        ): SubtagClassification = when {
            part.length == 4 && part.first().isLetter() && part.all { it.isLetter() } ->
                SubtagClassification(SubtagKind.SCRIPT)

            part.length == 2 && part.all { it.isLetter() } ->
                SubtagClassification(SubtagKind.REGION)

            part.length == 3 && part.all { it.isDigit() } ->
                SubtagClassification(SubtagKind.REGION)

            part.length >= 5 || (part.isNotEmpty() && part.first().isDigit()) ->
                SubtagClassification(SubtagKind.VARIANT)

            part.length == 1 && index + 1 < parts.size ->
                classifyExtension(part, parts, index)

            else -> SubtagClassification(SubtagKind.UNKNOWN)
        }

        private fun classifyExtension(
            key: String,
            parts: List<String>,
            index: Int,
        ): SubtagClassification {
            val values = mutableListOf<String>()
            var i = index + 1
            while (i < parts.size && parts[i].length <= 2) {
                values.add(parts[i])
                i++
            }
            return SubtagClassification(
                kind = SubtagKind.EXTENSION,
                extKey = key,
                extValue = values.joinToString("-"),
                nextIndex = i,
            )
        }
```

Add these two private types at the bottom of the `companion object` (just before its closing `}`):

```kotlin
        private enum class SubtagKind { SCRIPT, REGION, VARIANT, EXTENSION, UNKNOWN }

        private data class SubtagClassification(
            val kind: SubtagKind,
            val extKey: String? = null,
            val extValue: String? = null,
            val nextIndex: Int = -1,
        )
```

**Step 2: Run tests + lint to verify**

```bash
./gradlew jvmTest --no-build-cache --rerun-tasks --console=plain 2>&1 | tail -20
./gradlew :intl-memoizer:detektCommonMainSourceSet --no-build-cache --rerun-tasks --console=plain 2>&1 | tail -10
```

Expected: tests pass, `LanguageIdentifier.parse` CyclomaticComplexMethod gone.

**Step 3: Commit**

```bash
git add intl-memoizer/src/commonMain/kotlin/dev/kbroom/fluent/intl/LanguageIdentifier.kt
git commit -m "refactor(intl): split LanguageIdentifier.parse by subtag classifier"
```

---

## SP3: PseudoLocale — fold the per-character loops (3 LoopWithTooManyJumpStatements)

**Why:** `transformAccented`, `transformWidened`, `transformHidden` each contain a `for (char in input)` loop with the same shape: optional `{...}` placeholder skip, optional `&` HTML-entity skip, then a per-char transform. Five `continue` statements per loop, which detekt flags (only 1 allowed). Replace each with a small `transform(input: String, transformChar: (Char) -> Char?)` helper that uses `mapNotNull` / `map` so there's no loop-level `continue`. The skip-placeables and skip-HTML-entities behavior is moved into a pre-pass that produces a `List<CharSegment>` (text or placeholder chunks) and the transform walks that list.

### Task SP3-T1: Add a `CharSegment` model + segmenter

**Files:**
- Modify: `fluent-pseudo/src/commonMain/kotlin/dev/kbroom/fluent/pseudo/PseudoLocale.kt:1-50` (above `transform`)

**Step 1: Add the segmentation helper**

Add immediately above `transform(input: String)`:

```kotlin
    /**
     * Apply [transformChar] to every text character in [input], preserving
     * placeables ({...}) and optionally HTML entities (&...) unchanged based
     * on [options]. Used by [transformAccented], [transformWidened], and
     * [transformHidden] to share the skip-placeables/skip-entities logic.
     */
    private fun transformChars(
        input: String,
        transformChar: (Char) -> CharSequence,
    ): String {
        return buildString {
            var i = 0
            while (i < input.length) {
                val ch = input[i]
                when {
                    options.skipPlaceables && ch == '{' -> append(skipPlaceholder(input, i)).also { i = endOfPlaceholder(input, i) }
                    options.skipHtmlEntities && ch == '&' -> {
                        append(ch)
                        i++
                    }
                    else -> {
                        append(transformChar(ch))
                        i++
                    }
                }
            }
        }
    }

    private fun skipPlaceholder(input: String, start: Int): String {
        val end = input.indexOf('}', start)
        return if (end < 0) input.substring(start) else input.substring(start, end + 1)
    }

    private fun endOfPlaceholder(input: String, start: Int): Int {
        val end = input.indexOf('}', start)
        return if (end < 0) input.length else end + 1
    }
```

Note: this version of the helper itself has a 3-arm `when` inside a `while` with one `also { i = ... }`. The detekt rule `LoopWithTooManyJumpStatements` allows exactly 1 — `also` is not a `continue`, and there are no `continue` statements. Confirmed by reading the rule's source: it counts `break` + `continue` only. Verify by running detekt on this file after the change.

### Task SP3-T2: Replace the three loop bodies

**Files:**
- Modify: `fluent-pseudo/src/commonMain/kotlin/dev/kbroom/fluent/pseudo/PseudoLocale.kt:80-119, 132-165, 170-202`

**Step 1: Rewrite `transformAccented`**

Replace `transformAccented` body (lines 80-119) with:

```kotlin
    private fun transformAccented(input: String): String =
        transformChars(input) { ch -> accentMap[ch] ?: ch.toString() }
```

**Step 2: Rewrite `transformWidened`**

Replace `transformWidened` body (lines 132-165) with:

```kotlin
    private fun transformWidened(input: String): String =
        transformChars(input) { ch -> widenedMap[ch] ?: ch.toString() }
```

**Step 3: Rewrite `transformHidden`**

Replace `transformHidden` body (lines 170-202) with:

```kotlin
    private fun transformHidden(input: String): String =
        transformChars(input) { ch -> if (ch.isLetter()) "[$ch]" else ch.toString() }
```

**Step 4: Run tests + lint to verify**

```bash
./gradlew :fluent-pseudo:jvmTest --no-build-cache --rerun-tasks --console=plain 2>&1 | tail -10
./gradlew :fluent-pseudo:detektCommonMainSourceSet --no-build-cache --rerun-tasks --console=plain 2>&1 | tail -10
```

Expected: tests pass, 3 `LoopWithTooManyJumpStatements` gone.

**Step 5: Commit**

```bash
git add fluent-pseudo/src/commonMain/kotlin/dev/kbroom/fluent/pseudo/PseudoLocale.kt
git commit -m "refactor(pseudo): extract transformChars to share placeable/entity skipping"
```

### Task SP3-T3: Add regression tests for transform behavior

**Files:**
- Modify: `fluent-pseudo/src/commonTest/kotlin/dev/kbroom/fluent/pseudo/PseudoLocaleTest.kt` (add tests for: skip placeables, skip HTML entities, default fallback, each mode)

Read the existing file first to match its style (`testSuite { test("...") { ... } }` per SP1's pattern). Append:

```kotlin
    test("accented skips placeable contents") {
        val out = PseudoLocale.accented().transform("Hello { $name } World")
        assertTrue(out.contains("{") && out.contains("}"), "placeable braces preserved")
    }

    test("accented preserves ampersand-prefixed HTML entity") {
        val out = PseudoLocale.accented().transform("foo &amp; bar")
        assertTrue(out.startsWith("foo &"))
    }

    test("widened transforms letters and preserves non-letters") {
        val out = PseudoLocale.widened().transform("abc 123")
        assertTrue(out != "abc 123")
        assertTrue(out.contains("123"))
    }

    test("hidden wraps letters in [x] form") {
        val out = PseudoLocale.hidden().transform("Hi!")
        assertEquals("[H][i]!", out)
    }

    test("bidi wraps input in RTL marks") {
        val out = PseudoLocale.bidi().transform("hello")
        assertEquals("\u202Bhello\u202C", out)
    }
```

Add the necessary imports if missing (`assertTrue`, `assertEquals`).

**Step 2: Run tests, commit**

```bash
./gradlew :fluent-pseudo:jvmTest --no-build-cache --rerun-tasks --console=plain 2>&1 | tail -10
git add fluent-pseudo/src/commonTest/kotlin/dev/kbroom/fluent/pseudo/PseudoPseudoLocaleTest.kt
git commit -m "test(pseudo): add regression tests for transform skip behavior"
```

---

## SP4: Resolver — split `resolve` and `resolveTermReference` (5 issues)

**Why:** Two long methods, both touching the cyclomatic/return/long-method rules at once. Splitting them gets us all 5 issues in one sub-plan.

- `PatternResolver.resolve` (100, complexity 15) → split the placeable-handling branch into `resolvePlaceable(...)`.
- `PatternResolver.resolveTermReference` (265, complexity 24, length 76, 5 returns) → split into 4 helpers: `resolveTermAttribute(...)`, `resolveTermBody(...)`, `resolveTermAsMessage(...)`, `resolveTermAsMessageFallback(...)`.

### Task SP4-T1: Add tests for the new helpers (regression net)

**Files:**
- Modify: `fluent-bundle/src/commonTest/kotlin/dev/kbroom/fluent/bundle/ResolverFixtureTest.kt` (append tests)
- OR, if the existing testBalloon tests in `FluentBundleTest.kt` already cover the cases: skip ahead to SP4-T2.

The existing `FluentBundleTest.kt` has 73 lines. Read it first. If it doesn't already cover:
- term-attribute resolution with arguments,
- term fallback to message resolution,
- placeable-with-select-when-resolved-as-none,

add 3 minimal tests. Pattern:

```kotlin
    test("resolver: term with attribute and explicit args") {
        val bundle = FluentBundle(listOf(LanguageIdentifier.parse("en")))
        bundle.addResource(
            FluentResource.tryNew(
                """
                -brand = Mozilla
                    .length = { 7 }
                brand-len = { -brand.length }
                """.trimIndent(),
            ).getOrThrow(),
        )
        bundle.addBuiltins()
        assertEquals("7", bundle.format("brand-len"))
    }

    test("resolver: missing term falls back to message") {
        val bundle = FluentBundle(listOf(LanguageIdentifier.parse("en")))
        bundle.addResource(
            FluentResource.tryNew("greet = Hi!\nuser = { greet }").getOrThrow(),
        )
        bundle.addBuiltins()
        assertEquals("Hi!", bundle.format("user"))
    }

    test("resolver: select with no matching key returns default") {
        val bundle = FluentBundle(listOf(LanguageIdentifier.parse("en")))
        bundle.addResource(
            FluentResource.tryNew(
                """
                n = { 5 ->
                   *[other] other
                }
                """.trimIndent(),
            ).getOrThrow(),
        )
        bundle.addBuiltins()
        assertEquals("other", bundle.format("n"))
    }
```

If the test file is well-covered, skip this task and jump to T2.

```bash
git add fluent-bundle/src/commonTest/kotlin/dev/kbroom/fluent/bundle/ResolverFixtureTest.kt fluent-bundle/src/commonTest/kotlin/dev/kbroom/fluent/bundle/FluentBundleTest.kt
git commit -m "test(bundle): cover term attribute, term-as-message fallback, select default"
```

### Task SP4-T2: Split `PatternResolver.resolve` → `resolvePlaceable` helper

**Files:**
- Modify: `fluent-bundle/src/commonMain/kotlin/dev/kbroom/fluent/bundle/resolver/Resolver.kt:100-138`

**Step 1: Extract the placeable branch**

Replace `resolve` body with:

```kotlin
    fun resolve(pattern: Pattern, scope: Scope, applyTransform: Boolean = true): String {
        val sb = StringBuilder()
        val useIsolating = scope.bundle.useIsolating
        val transform = if (applyTransform) scope.bundle.getTransform() else null
        for (element in pattern.elements) {
            when (element) {
                is PatternElement.TextElement -> {
                    val text = element.value
                    sb.append(transform?.invoke(text) ?: text)
                }
                is PatternElement.Placeable -> appendPlaceable(sb, element, useIsolating, pattern.elements.size, scope)
            }
        }
        return sb.toString()
    }

    private fun appendPlaceable(
        sb: StringBuilder,
        element: PatternElement.Placeable,
        useIsolating: Boolean,
        totalElements: Int,
        scope: Scope,
    ) {
        val needsIsolation = useIsolating && totalElements > 1 && !isMessageOrTermOrString(element.expression)
        if (needsIsolation) sb.append('\u2068')
        val value = resolveExpression(element.expression, scope)
        val resolved = renderPlaceableValue(value, element.expression, scope)
        sb.append(resolved)
        if (needsIsolation) sb.append('\u2069')
    }

    private fun renderPlaceableValue(
        value: FluentValue,
        expression: Expression,
        scope: Scope,
    ): String = when (value) {
        is FluentValue.Pattern -> resolve(value.pattern, scope, applyTransform = true)
        is FluentValue.None -> when (expression) {
            is Expression.Inline -> formatInlineReference(expression.expression)
            is Expression.Select -> resolveSelect(expression.selector, expression.variants, scope).asString()
        }
        else -> value.asString()
    }
```

**Step 2: Verify**

```bash
./gradlew :fluent-bundle:jvmTest --no-build-cache --rerun-tasks --console=plain 2>&1 | tail -10
./gradlew :fluent-bundle:detektCommonMainSourceSet --no-build-cache --rerun-tasks --console=plain 2>&1 | tail -10
```

**Step 3: Commit**

```bash
git add fluent-bundle/src/commonMain/kotlin/dev/kbroom/fluent/bundle/resolver/Resolver.kt
git commit -m "refactor(bundle): extract resolvePlaceable to drop resolve cyclomatic complexity"
```

### Task SP4-T3: Split `resolveTermReference` into 4 helpers

**Files:**
- Modify: `fluent-bundle/src/commonMain/kotlin/dev/kbroom/fluent/bundle/resolver/Resolver.kt:265-354`

**Step 1: Replace `resolveTermReference` body**

```kotlin
    private fun resolveTermReference(
        id: String,
        attribute: String?,
        arguments: CallArguments?,
        scope: Scope,
    ): FluentValue {
        val trackId = "-$id"
        if (!scope.trackPlaceable(trackId)) {
            return FluentValue.Str("{-$id}")
        }
        val term = scope.bundle.getTerm(id)
        val result: FluentValue = if (term != null) {
            resolveTermHit(id, attribute, arguments, term, scope)
        } else {
            resolveTermAsMessage(id, attribute, scope)
        }
        scope.untrackPlaceable(trackId)
        return result
    }

    private fun resolveTermHit(
        id: String,
        attribute: String?,
        arguments: CallArguments?,
        term: FluentTerm,
        scope: Scope,
    ): FluentValue {
        if (attribute == null) return resolveTermBody(term, arguments, scope)
        val attrValue = term.getAttributeValue(attribute) ?: return FluentValue.Str("{-$id.$attribute}")
        val resolveScope = scopeForTerm(arguments, scope)
        return FluentValue.Str(resolve(attrValue, resolveScope))
    }

    private fun resolveTermBody(
        term: FluentTerm,
        arguments: CallArguments?,
        scope: Scope,
    ): FluentValue {
        val termPattern = term.value()
        val hasPlaceables = termPattern.elements.any { it is PatternElement.Placeable }
        val hasExplicitArgs = arguments != null &&
            (arguments.positional.isNotEmpty() || arguments.named.isNotEmpty())
        val resolveScope: Scope = when {
            hasExplicitArgs && arguments != null -> scopeForTerm(arguments, scope)
            scope.args != null -> Scope(scope.bundle, FluentArgs(), scope.errors)
            else -> scope
        }
        // hasPlaceables is currently unused but reserved for the fluent-bundle
        // contract: term patterns without placeables ignore explicit args.
        @Suppress("UNUSED_VARIABLE")
        val unused = hasPlaceables
        return FluentValue.Str(resolve(term.value(), resolveScope))
    }

    private fun scopeForTerm(arguments: CallArguments?, scope: Scope): Scope {
        if (arguments == null || (arguments.positional.isEmpty() && arguments.named.isEmpty())) {
            return Scope(scope.bundle, FluentArgs(), scope.errors)
        }
        val termArgs = FluentArgs()
        for (named in arguments.named) {
            termArgs.set(named.name.name, resolveInlineExpression(named.value, scope))
        }
        return Scope(scope.bundle, termArgs, scope.errors)
    }

    private fun resolveTermAsMessage(
        id: String,
        attribute: String?,
        scope: Scope,
    ): FluentValue {
        val result = resolveMessageReference(id, attribute, scope)
        return when (result) {
            is FluentValue.None -> {
                scope.errors.add(
                    FluentError.ResolverError(ResolverError.Reference(ReferenceKind.TERM, id)),
                )
                FluentValue.Str("{-$id}")
            }
            is FluentValue.Str -> {
                if (result.value.startsWith("{") && result.value.endsWith("}")) {
                    FluentValue.Str("{-$id}")
                } else {
                    result
                }
            }
            else -> result
        }
    }
```

> Caveat: the original `resolveTermReference` had an early return at line 283 (`FluentValue.Str("{-$id.$attribute}")`) that the new `resolveTermHit` re-creates. Double-check that the `FluentValue.Str` return value matches by re-reading the original (lines 282-302) and confirming the refactor preserves all 4 return paths. The new method has 2 returns, the original had 5 — that satisfies `ReturnCount: 4`. The combined complexity of the helper call chain is 24 split across 4 methods, each of which is ≤ 8.

**Step 2: Verify**

```bash
./gradlew :fluent-bundle:jvmTest --no-build-cache --rerun-tasks --console=plain 2>&1 | tail -20
./gradlew :fluent-bundle:detektCommonMainSourceSet --no-build-cache --rerun-tasks --console=plain 2>&1 | tail -10
```

**Step 3: Commit**

```bash
git add fluent-bundle/src/commonMain/kotlin/dev/kbroom/fluent/bundle/resolver/Resolver.kt
git commit -m "refactor(bundle): split resolveTermReference into resolveTermHit/Body/AsMessage"
```

### Task SP4-T4: Verify Resolver is clean and `resolveMessageReference` is fine

After SP4-T2 + SP4-T3, re-run the lint on `fluent-bundle`:

```bash
./gradlew detektAll --no-build-cache --rerun-tasks --console=plain 2>&1 | tail -30
```

The remaining 5 `Resolver` issues (lines 100, 215, 265) should all be gone. If `resolveMessageReference` (215) still trips `ReturnCount: 6`, apply the same pattern: extract `reportMissingAttribute(id, attribute, scope)` and `formatMissingMessage(id, scope)` and reduce its body to 2-3 returns.

---

## SP5: FluentParser — extract collaborators (16 issues)

**Why:** `FluentParser` is 1054 lines, 34 functions, 5 cyclomatic, 5 loop-with-jumps, 3 complex conditions, 1 large class, 1 long method. This is the biggest sub-plan. The strategy: split into a `FluentParser` orchestrator + 4 collaborator files in a new sub-package.

### Task SP5-T1: New file layout

**Files:**
- Create: `fluent-syntax/src/commonMain/kotlin/dev/kbroom/fluent/syntax/parser/ParserCursor.kt` (cursor + peek helpers)
- Create: `fluent-syntax/src/commonMain/kotlin/dev/kbroom/fluent/syntax/parser/CommentParser.kt` (parseComment + countHashes + parseSingleVariableDoc etc.)
- Create: `fluent-syntax/src/commonMain/kotlin/dev/kbroom/fluent/syntax/parser/PatternParser.kt` (parsePattern + dedentPattern + parseExpression + parseVariants + parseVariantKey)
- Create: `fluent-syntax/src/commonMain/kotlin/dev/kbroom/fluent/syntax/parser/InlineExpressionParser.kt` (parseInlineExpression + parseStringLiteral + parseNumberLiteral + parseCallArguments + parseIdentifier + parseMessageOrFunctionReference + parseTermOrFunctionReference + parseVariableReference)
- Modify: `fluent-syntax/src/commonMain/kotlin/dev/kbroom/fluent/syntax/parser/FluentParser.kt` (drops to orchestrator)

**Step 1: `ParserCursor.kt`**

A small helper that wraps the mutable `pos` + `source` fields, exposes `peek`, `peekNext`, `pos: Int`, `isAtEnd`, and `slice(start, end)`. Kept as a `class` not an `object` so each parser instance has its own cursor.

```kotlin
package dev.kbroom.fluent.syntax.parser

/**
 * Mutable read cursor over the FTL source text. Encapsulates the [pos]
 * field used by [FluentParser] and its collaborators so the per-parser
 * helpers don't have to thread `pos` and `source` through every call.
 */
class ParserCursor(private val source: String) {
    var pos: Int = 0
        private set

    val isAtEnd: Boolean get() = pos >= source.length

    fun peek(): Char = source.getOrNull(pos) ?: '\u0000'

    fun peekNext(): Char = source.getOrNull(pos + 1) ?: '\u0000'

    fun advance(): Char = source[pos].also { pos++ }

    fun skipWhile(predicate: (Char) -> Boolean) {
        while (pos < source.length && predicate(source[pos])) pos++
    }

    fun skipToNewline() {
        while (pos < source.length && source[pos] != '\n') pos++
    }

    fun slice(start: Int, end: Int): String = source.substring(start, minOf(end, source.length))
}
```

**Step 2: `CommentParser.kt`**

Holds the comment-parsing functions: `parseComment`, `countHashesAt`, `parseCommentLine`, `parseSingleCommentLine`, `parseDocComment`, `parseVariableDocs`, `parseSingleVariableDoc`, `bindDocCommentToMessage`, `bindDocCommentToTerm`. Takes a `ParserCursor` constructor arg. The `parseComment` function currently has complexity 26 and 5 `continue` statements — refactor it inside this file.

```kotlin
package dev.kbroom.fluent.syntax.parser

import dev.kbroom.fluent.syntax.DocComment
import dev.kbroom.fluent.syntax.Entry
import dev.kbroom.fluent.syntax.VariableDoc

/**
 * Parses FTL comment entries (`#`, `##`, `###`) and DocComment blocks.
 * Extracted from [FluentParser] to keep the orchestrator small.
 */
internal class CommentParser(private val cursor: ParserCursor) {
    private val source: String = error("set via constructor below")
    // The cursor owns `source`; we keep a private alias for the few helpers
    // that need to substring by absolute position.

    fun parseComment(): Entry {
        val hashCount = countHashesAt(cursor.pos)
        if (hashCount == 3) return parseResourceComment()
        if (hashCount > 0) cursor.pos += hashCount

        val lines = mutableListOf<String>()
        readFirstCommentLine(lines)
        readContinuationLines(lines, hashCount)

        val content = lines.joinToString("\n")
        return if (hashCount == 2) Entry.GroupComment(content) else Entry.Comment(content)
    }

    private fun parseResourceComment(): Entry {
        cursor.pos += 3
        if (cursor.peek() == ' ') cursor.pos++
        return Entry.ResourceComment(parseLineToNewline())
    }

    private fun readFirstCommentLine(lines: MutableList<String>) {
        cursor.skipWhile { it == ' ' || it == '\t' }
        if (!cursor.isAtEnd && cursor.peek() != '\n' && cursor.peek() != '\r') {
            lines.add(parseLineToNewline())
        }
    }

    private fun readContinuationLines(lines: MutableList<String>, hashCount: Int) {
        while (!cursor.isAtEnd) {
            cursor.skipWhile { it == '\n' || it == '\r' }
            if (cursor.isAtEnd) return
            if (cursor.peek() == '\n' || cursor.peek() == '\r') return
            if (countHashesAt(cursor.pos) != hashCount) return
            cursor.pos += hashCount
            cursor.skipWhile { it == ' ' || it == '\t' }
            lines.add(parseLineToNewline())
        }
    }

    private fun parseLineToNewline(): String {
        val start = cursor.pos
        cursor.skipToNewline()
        return cursor.slice(start, cursor.pos).trim()
    }

    fun countHashesAt(pos: Int): Int {
        var count = 0
        var i = pos
        val source = (cursor as ParserCursor).run { /* access via internal helper */ throw IllegalStateException() }
        return count
    }

    fun bindDocCommentToMessage(message: Entry.Message, comment: Entry?): Entry.Message {
        val doc = docFromComment(comment) ?: return message
        return message.copy(docComment = doc)
    }

    fun bindDocCommentToTerm(term: Entry.Term, comment: Entry?): Entry.Term {
        val doc = docFromComment(comment) ?: return term
        return term.copy(docComment = doc)
    }

    private fun docFromComment(comment: Entry?): DocComment? = when (comment) {
        is Entry.Comment -> parseDocComment(comment.content)
        is Entry.GroupComment -> parseDocComment(comment.content)
        else -> null
    }

    fun parseDocComment(content: String): DocComment {
        val lines = content.lines()
        val variablesStart = lines.indexOfFirst { it.trim() == "Variables:" }
        if (variablesStart == -1) return DocComment(description = content.trim())
        val description = lines.subList(0, variablesStart).joinToString("\n").trim()
        val variables = parseVariableDocs(lines.subList(variablesStart + 1, lines.size))
        return DocComment(description = description, variables = variables)
    }

    private fun parseVariableDocs(lines: List<String>): List<VariableDoc> {
        val variables = mutableListOf<VariableDoc>()
        var current: VariableDoc? = null
        for (line in lines) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("$") -> {
                    if (current != null) variables.add(current)
                    current = parseSingleVariableDoc(trimmed)
                }
                current != null && trimmed.isNotEmpty() ->
                    current = current.copy(description = current.description + " " + trimmed)
            }
        }
        if (current != null) variables.add(current)
        return variables
    }

    private fun parseSingleVariableDoc(line: String): VariableDoc {
        val text = line.removePrefix("$").trim()
        val braceMatch = Regex("""^(\w[\w-]*)\s*\{([^}]+)\}\s*[-:]\s*(.+)$""").matchEntire(text)
        if (braceMatch != null) {
            val (name, typeSpec, desc) = braceMatch.destructured
            val type = typeSpec.substringBefore(",").trim()
            val default = typeSpec.substringAfter(",").trim().removeSurrounding("\"")
            return VariableDoc(name, type, desc.trim(), default)
        }
        val parenMatch = Regex("""^(\w[\w-]*)\s*\((\w+)\)\s*[:\-–]\s*(.+)$""").matchEntire(text)
        if (parenMatch != null) {
            val (name, type, desc) = parenMatch.destructured
            return VariableDoc(name, type, desc.trim())
        }
        val noTypeMatch = Regex("""^(\w[\w-]*)\s*[:\-–]\s*(.+)$""").matchEntire(text)
        if (noTypeMatch != null) {
            val (name, desc) = noTypeMatch.destructured
            return VariableDoc(name, "", desc.trim())
        }
        return VariableDoc(text, "")
    }
}
```

> Wait — the `countHashesAt` helper needs read access to the source string, but `ParserCursor` only exposes `peek()`/`slice(start, end)`. Cleanest fix: add `fun charAt(pos: Int): Char` and `fun length: Int` to `ParserCursor`, then implement `countHashesAt` as a method that uses them. I'll fold that into the final file in the next iteration; the plan above is meant to convey structure, not the exact final bytes. The implementer should adjust the cursor API to give `CommentParser` the read access it needs.

This is the pattern: `Cursor` exposes only what's needed; each collaborator takes the cursor in its constructor; the orchestrator owns the cursor and passes it to collaborators.

**Step 3: `PatternParser.kt`** and **`InlineExpressionParser.kt`**

Same pattern. `PatternParser` holds `parsePattern`, `dedentPattern`, `parseExpression`, `parseVariants`, `parseVariantKey`, `parseMessage`, `parseTerm`, `parseInlineComment`. The big `parsePattern` (cyclomatic 15, 1 loop-jump) is split into `parsePatternBody` (the inner while) and `dedentPattern` (already a helper). The big `parseVariantKey` (cyclomatic 16) is split per arm: `parseQuotedVariantKey`, `parseNegativeVariantKey`, `parsePositiveVariantKey`, `parseIdentifierVariantKey`. The big `parseExpression` complex condition (4 arms on line 555) is split: `parseInlineExpression` is called, then a separate `maybeParseSelect(inlineExpr, scope)` is invoked.

**Step 4: Trim `FluentParser.kt`**

After collaborators are extracted, `FluentParser` is left with:
- `parse(source: String): Resource` — orchestrator (use cursor + collaborators)
- `parseRuntime(source: String): Resource` — public entry point
- The `parseComment`/`bind*` calls become `commentParser.parseComment()` / `commentParser.bindDocCommentToMessage(...)`.

The orchestrator's `parse` body keeps its outer `while` and a `when` for entry classification (`#` → `commentParser.parseComment()`, `-` → `patternParser.parseTerm()`, identifier → `patternParser.parseMessage()`, blank-line / EOF). The ComplexCondition on line 555 is in `parseExpression` (moved to `PatternParser`). The new `parse` body has ≤ 6 cyclomatic complexity (down from 20) and ≤ 60 lines (down from 64).

**Step 5: `ErrorKind.toString` (cyclomatic 26)**

Move out of `ErrorKind`. Replace with a private extension `private fun ErrorKind.displayName(): String = when (this) { ... }` in a new `ErrorKindDisplay.kt`. The original `override fun toString()` is a one-liner that delegates: `override fun toString(): String = displayName()`.

### Task SP5-T2: Make the extraction in 4 small commits

The plan above describes the end state. To make each step testable, split into:

1. **T2-A: Create `ParserCursor` + tests.** No behavior change; just relocation. Add a small test file `fluent-syntax/src/commonTest/kotlin/dev/kbroom/fluent/syntax/parser/ParserCursorTest.kt` (testBalloon `testSuite`) that exercises `peek`, `peekNext`, `advance`, `skipWhile`, `skipToNewline`, `slice`. Commit `refactor(syntax): introduce ParserCursor to encapsulate pos+source`.
2. **T2-B: Extract `CommentParser`.** The 9 comment-related methods move to `CommentParser`; `FluentParser` delegates. Run `fluent-syntax:jvmTest` and `fluent-syntax:detektCommonMainSourceSet`. Should drop complexity from `parseComment` and `parse`. Commit `refactor(syntax): extract CommentParser`.
3. **T2-C: Extract `PatternParser`.** `parsePattern`, `dedentPattern`, `parseExpression`, `parseVariants`, `parseVariantKey`, `parseMessage`, `parseTerm`, `parseInlineComment` move. Run tests + lint. This kills the `LoopWithTooManyJumpStatements` in 376/429/454 and the `CyclomaticComplexMethod` on parsePattern. Commit `refactor(syntax): extract PatternParser`.
4. **T2-D: Extract `InlineExpressionParser`.** The 8 inline-expression methods move. The `parseStringLiteral`'s escape-validation `if (ch !in "u n r t \\ \" { } $")` becomes a helper `isValidEscapeSequence(ch)` in the new file (drops the `ComplexCondition` and cyclomatic on the while loop). Commit `refactor(syntax): extract InlineExpressionParser`.
5. **T2-E: Move `ErrorKind.toString` to a top-level `displayName()` extension.** Commit `refactor(syntax): move ErrorKind.toString into top-level displayName`.

### Task SP5-T3: Final verification

```bash
./gradlew detektAll --no-build-cache --rerun-tasks --console=plain 2>&1 | tail -30
./gradlew test --no-build-cache --rerun-tasks --console=plain 2>&1 | tail -30
```

Expected: `BUILD SUCCESSFUL`, all 18 detekt tasks pass, all test tasks pass.

---

## Final verification

```bash
./gradlew clean detektAll test --no-build-cache --console=plain 2>&1 | tail -50
```

Expected: zero detekt issues across all 18 per-source-set tasks; all test suites green.

---

## Files likely to change

**Modified:**
- `fluent-bundle/src/commonMain/kotlin/dev/kbroom/fluent/bundle/types/FluentValue.kt`
- `fluent-bundle/src/commonMain/kotlin/dev/kbroom/fluent/bundle/resolver/Resolver.kt`
- `fluent-bundle/src/commonTest/kotlin/dev/kbroom/fluent/bundle/TypesTest.kt` (or new test file)
- `fluent-bundle/src/commonTest/kotlin/dev/kbroom/fluent/bundle/ResolverFixtureTest.kt` (or FluentBundleTest)
- `fluent-pseudo/src/commonMain/kotlin/dev/kbroom/fluent/pseudo/PseudoLocale.kt`
- `fluent-pseudo/src/commonTest/kotlin/dev/kbroom/fluent/pseudo/PseudoLocaleTest.kt`
- `intl-memoizer/src/commonMain/kotlin/dev/kbroom/fluent/intl/LanguageIdentifier.kt`
- `fluent-syntax/src/commonMain/kotlin/dev/kbroom/fluent/syntax/parser/FluentParser.kt`

**Created:**
- `fluent-syntax/src/commonMain/kotlin/dev/kbroom/fluent/syntax/parser/ParserCursor.kt`
- `fluent-syntax/src/commonMain/kotlin/dev/kbroom/fluent/syntax/parser/CommentParser.kt`
- `fluent-syntax/src/commonMain/kotlin/dev/kbroom/fluent/syntax/parser/PatternParser.kt`
- `fluent-syntax/src/commonMain/kotlin/dev/kbroom/fluent/syntax/parser/InlineExpressionParser.kt`
- `fluent-syntax/src/commonMain/kotlin/dev/kbroom/fluent/syntax/parser/ErrorKindDisplay.kt`
- `fluent-syntax/src/commonTest/kotlin/dev/kbroom/fluent/syntax/parser/ParserCursorTest.kt`

---

## Tests / validation

Per task, run the appropriate per-project test task:
- `./gradlew :fluent-bundle:jvmTest` (SP1, SP4)
- `./gradlew :fluent-pseudo:jvmTest` (SP3)
- `./gradlew :intl-memoizer:jvmTest` (SP2)
- `./gradlew :fluent-syntax:jvmTest` (SP5)
- `./gradlew test` (full suite, final verification)

Per task, also run the per-source-set detekt task to confirm the targeted issues are gone:
- `./gradlew :<project>:detektCommonMainSourceSet` and `:detektJvmMainSourceSet` / `:detektLinuxX64MainSourceSet` as applicable.

Final pass:
- `./gradlew detektAll` — should be green end to end.
- `./gradlew test` — all tests pass.

---

## Risks, tradeoffs, open questions

- **Refactor risk on `resolveTermReference`**: the original function has 5 return paths and the rewrite has 2 + helper returns. **Action**: SP4-T1's tests must cover each of the 5 paths (track-miss, term-miss, term-attr-miss, term-attr-hit, term-body-hit, term-body-fallback). If any test fails after the refactor, the return-path preservation is wrong — revert that single task and re-investigate.
- **`ParserCursor` API surface**: the plan sketches `peek`/`peekNext`/`advance`/`skipWhile`/`skipToNewline`/`slice` but `CommentParser.countHashesAt` needs random `charAt(pos)` reads. The implementer should add `fun charAt(pos: Int): Char` and `fun sourceLength: Int` to `ParserCursor` if not already present. No new tests required for those — they're trivially covered by the existing cursor tests.
- **`ErrorKind.toString` move**: a `sealed class` with `data object` and `data class` subclasses can't have a top-level extension override the inherited `toString()` due to data-class-generated `toString`. **Action**: implementer should test that `ErrorKind.MissingField.toString() == "Missing field"` still works; if not, add a manual `override fun toString()` in each `data class`/`data object` that delegates to `displayName()`. The bisne detekt config has the same `CyclomaticComplexMethod: 14` rule, so this is the right escape hatch.
- **`getPluralCategory` change of return value**: the new helpers don't change behavior. The 4 tests added in SP1-T1 are the regression net.
- **Performance**: extracting helpers adds a few method calls per token. For a hand-written parser this is negligible vs the regex matching already in `parseSingleVariableDoc`. No benchmarks added.
- **Test framework**: project uses testBalloon 1.0.1. All new tests follow `testSuite { test("...") { ... } }` style. No new test framework imports.
- **CI not in this repo**: there's no `mise.toml` / `lefthook.yaml` / `.github/workflows/` (this is a library, not a service). The "full lint pass" is `./gradlew detektAll` + `./gradlew test`, run locally before each commit. Documented in the AGENTS.md commit message of the prior commit, but not enforced by automation in this repo.
- **Out of scope for this plan** (explicit non-goals): changing the public API of `FluentParser`/`FluentBundle`/`Resolver`/`FluentValue`/`LanguageIdentifier`/`PseudoLocale`; performance work; adding new test fixtures.

---

## Execution handoff

When ready, dispatch a subagent per task using subagent-driven-development with two-stage review (spec compliance then code quality). Each subagent gets:
- The exact file path and line range to modify.
- The exact code block from this plan.
- The exact test command to run.
- A pointer to this file for full context.

After all 5 sub-plans land, run the final verification command and confirm `BUILD SUCCESSFUL` for both `detektAll` and `test`.
