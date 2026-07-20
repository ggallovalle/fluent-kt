package dev.kbroom.fluent.examples.androidcompose.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.kbroom.fluent.examples.androidcompose.i18n.rememberAppMessages

@Composable
fun ProfileScreen(onBack: () -> Unit) {
    val app = rememberAppMessages()

    Column(Modifier.padding(16.dp)) {
        Text(app.profileSectionLabel(), style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = onBack) {
            Text(app.cancel())
        }
    }
}
