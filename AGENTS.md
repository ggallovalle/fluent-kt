# Agent Guidelines

## Import Style

**NEVER use wildcard imports (`import foo.bar.*`) in main source code.** Always use explicit imports.

### Correct
```kotlin
import dev.kbroom.fluent.syntax.Entry
import dev.kbroom.fluent.syntax.Pattern
import dev.kbroom.fluent.bundle.FluentBundle
```

### Incorrect
```kotlin
import dev.kbroom.fluent.syntax.*
import dev.kbroom.fluent.bundle.*
```

### Test Files
Test files (`*Test.kt`) may use wildcard imports for test helpers, as clarity is less critical in tests.

## Why This Matters

1. **Explicit dependencies** — Makes it clear what the code actually uses
2. **Avoids conflicts** — Prevents naming collisions between packages
3. **Refactoring safety** — Easier to track what needs to change
4. **IDE support** — Better autocomplete and navigation

## Common Patterns

### Re-exporting types
If you need types from another package, import them explicitly rather than re-exporting:
```kotlin
// Good
import dev.kbroom.fluent.syntax.Resource

// Avoid
import dev.kbroom.fluent.syntax.*
```

### Type aliases
Avoid using typealiases to re-export types from other packages:
```kotlin
// Good - use directly
import dev.kbroom.fluent.bundle.FluentBundle
fun create(): FluentBundle = FluentBundle(listOf())

// Avoid - typealias for re-export
typealias Fluent = dev.kbroom.fluent.bundle.FluentBundle
```

## Enforcement

This codebase uses explicit imports. Linters and IDEs should flag wildcard imports except in test files.
