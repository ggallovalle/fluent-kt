package dev.kbroom.fluent.examples.androidcompose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.core.os.LocaleListCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.kbroom.fluent.compose.ProvideFluentFromAssets
import dev.kbroom.fluent.examples.androidcompose.i18n.AppResources
import dev.kbroom.fluent.examples.androidcompose.i18n.ErrorsResources
import dev.kbroom.fluent.examples.androidcompose.ui.HomeScreen
import dev.kbroom.fluent.examples.androidcompose.ui.NotFoundScreen
import dev.kbroom.fluent.examples.androidcompose.ui.ProfileScreen
import dev.kbroom.fluent.intl.LanguageIdentifier

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    ProvideFluentFromAssets(
                        resourceIdsByBundle = mapOf(
                            "app" to AppResources.All,
                            "errors" to ErrorsResources.All,
                        ),
                        fallbackLocale = LanguageIdentifier.parse("en-US"),
                        basePath = "i18n",
                    ) {
                        ExampleApp(
                            onSwitchToSpanish = {
                                AppCompatDelegate.setApplicationLocales(
                                    LocaleListCompat.forLanguageTags("es-MX"),
                                )
                            },
                            onSwitchToEnglish = {
                                AppCompatDelegate.setApplicationLocales(
                                    LocaleListCompat.forLanguageTags("en-US"),
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ExampleApp(
    onSwitchToSpanish: () -> Unit,
    onSwitchToEnglish: () -> Unit,
) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                userName = "Ada",
                onOpenProfile = { navController.navigate("profile") },
                onOpenMissing = { navController.navigate("missing") },
                onSwitchToSpanish = onSwitchToSpanish,
                onSwitchToEnglish = onSwitchToEnglish,
            )
        }
        composable("profile") {
            ProfileScreen(onBack = { navController.popBackStack() })
        }
        composable("missing") {
            NotFoundScreen(
                onHome = {
                    navController.popBackStack("home", inclusive = false)
                },
            )
        }
    }
}
