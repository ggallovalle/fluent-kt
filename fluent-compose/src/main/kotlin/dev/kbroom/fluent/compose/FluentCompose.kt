package dev.kbroom.fluent.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import dev.kbroom.fluent.bundle.FluentArgs
import dev.kbroom.fluent.fallback.ResourceId
import dev.kbroom.fluent.intl.LanguageIdentifier
import java.util.Locale

/**
 * CompositionLocal for the multi-bundle Fluent registry.
 */
val LocalFluentBundles = staticCompositionLocalOf<FluentBundleRegistry> {
    error(
        "No FluentBundleRegistry provided. Wrap your UI in ProvideFluentFromAssets " +
            "or ProvideFluentBundles.",
    )
}

@Composable
fun ProvideFluentBundles(
    registry: FluentBundleRegistry,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalFluentBundles provides registry, content = content)
}

/**
 * Loads Fluent bundles from app assets for the current [LocalConfiguration] locale
 * and rebuilds when the configuration locale changes.
 */
@Composable
fun ProvideFluentFromAssets(
    resourceIdsByBundle: Map<String, List<ResourceId>>,
    fallbackLocale: LanguageIdentifier,
    basePath: String = "i18n",
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val localeTag = configuration.locales[0]?.toLanguageTag()
        ?: Locale.getDefault().toLanguageTag()
    val locale = remember(localeTag) { LanguageIdentifier.parse(localeTag) }
    val registry = remember(localeTag, resourceIdsByBundle, fallbackLocale, basePath, context) {
        AssetResourceManager(context.assets, basePath)
            .loadRegistry(locale, resourceIdsByBundle, fallbackLocale)
    }
    ProvideFluentBundles(registry, content)
}

/**
 * Escape hatch for dynamic message ids. Prefer codegen `rememberAppMessages()`.
 */
@Composable
fun fluentString(
    bundle: String,
    id: String,
    args: FluentArgs? = null,
): String {
    val fluentBundle = LocalFluentBundles.current.get(bundle)
    return fluentBundle.formatMessage(id, args) ?: id
}
