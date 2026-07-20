package dev.kbroom.fluent.examples.androidcompose.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.kbroom.fluent.examples.androidcompose.i18n.rememberAppMessages
import dev.kbroom.fluent.examples.androidcompose.i18n.rememberErrorsMessages

@Composable
fun NotFoundScreen(onHome: () -> Unit) {
    val app = rememberAppMessages()
    val errors = rememberErrorsMessages()

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(errors.notFound(), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onHome) {
            Text(app.submit())
        }
    }
}
