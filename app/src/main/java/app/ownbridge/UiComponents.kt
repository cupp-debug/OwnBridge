package app.ownbridge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em

@Composable
internal fun ContentCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        content = content,
    )
}

@Composable
internal fun SummaryCard(content: @Composable ColumnScope.() -> Unit) {
    ContentCard {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp), content = content)
    }
}

@Composable
internal fun SummaryDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 8.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

@Composable
internal fun SectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.containerTitle)
}

/** Help affordances follow their surrounding text, without inflating the row height. */
@Composable
internal fun InlineHelpIcon(
    description: String,
    textStyle: TextStyle,
    onClick: () -> Unit,
) {
    val size = with(LocalDensity.current) { textStyle.fontSize.toDp() }
    ProjectIcon(
        IconAsset.Help,
        contentDescription = description,
        modifier = Modifier.size(size).clickable(role = Role.Button, onClick = onClick),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Treat a vector icon as the first glyph, so wrapped lines return to the text's left edge. */
@Composable
internal fun InlineIconText(
    text: String,
    icon: IconAsset,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    iconTint: Color? = null,
) {
    val line = buildAnnotatedString {
        appendInlineContent("leading-icon", " ")
        append(" ")
        append(text)
    }
    Text(
        text = line,
        modifier = modifier,
        style = style,
        inlineContent = mapOf("leading-icon" to InlineTextContent(
            Placeholder(1.em, 1.em, PlaceholderVerticalAlign.TextCenter),
        ) {
            ProjectIcon(icon, contentDescription = null,
                modifier = Modifier.fillMaxSize(), tint = iconTint)
        }),
    )
}

@Composable
internal fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(16.dp))
        Text(
            value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
internal fun PrimaryAction(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    icon: IconAsset? = null,
    warning: Boolean = false,
    loading: Boolean = false,
) {
    val iconSize = if (warning) with(LocalDensity.current) {
        MaterialTheme.typography.labelLarge.fontSize.toDp()
    } else 20.dp
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        colors = when {
            warning -> ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            loading -> ButtonDefaults.buttonColors(
                disabledContainerColor = MaterialTheme.colorScheme.primary,
                disabledContentColor = MaterialTheme.colorScheme.onPrimary,
            )
            else -> ButtonDefaults.buttonColors()
        },
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(iconSize), strokeWidth = 2.dp,
                color = LocalContentColor.current,
            )
            Spacer(Modifier.width(8.dp))
        } else if (icon != null) {
            ProjectIcon(icon, contentDescription = null, modifier = Modifier.size(iconSize))
            Spacer(Modifier.width(8.dp))
        }
        Text(text)
    }
}
