package dev.kbroom.fluent.examples.androidcompose.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.kbroom.fluent.examples.androidcompose.i18n.rememberAppMessages

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    userName: String,
    onOpenProfile: () -> Unit,
    onOpenMissing: () -> Unit,
    onSwitchToSpanish: () -> Unit,
    onSwitchToEnglish: () -> Unit,
) {
    val app = rememberAppMessages()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(app.homeTitle()) })
        },
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {
            Text(
                text = app.greeting(name = userName),
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onOpenProfile) {
                    Text(app.submit())
                }
                OutlinedButton(onClick = onOpenMissing) {
                    Text(app.cancel())
                }
            }
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onSwitchToEnglish) {
                    Text("en-US")
                }
                OutlinedButton(onClick = onSwitchToSpanish) {
                    Text("es-MX")
                }
            }
        }
    }
}
