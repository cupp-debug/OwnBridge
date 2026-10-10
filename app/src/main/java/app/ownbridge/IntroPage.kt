package app.ownbridge

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun IntroPage() {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(
            text = stringResource(R.string.intro_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )

        listOf(
            R.string.intro_overview,
            R.string.intro_components,
            R.string.intro_open_source,
            R.string.intro_clients,
            R.string.intro_benefits,
            R.string.intro_privacy,
        ).forEach { paragraph ->
            IntroParagraph(paragraph)
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.intro_scope_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            IntroParagraph(R.string.intro_scope)
        }

        listOf(
            R.string.intro_addresses,
            R.string.intro_requirements,
            R.string.intro_aftercare,
            R.string.intro_recommendations,
        ).forEach { paragraph ->
            IntroParagraph(paragraph)
        }
    }
}

@Composable
private fun IntroParagraph(@StringRes textRes: Int) {
    Text(
        text = stringResource(textRes),
        style = MaterialTheme.typography.bodyLarge,
    )
}
