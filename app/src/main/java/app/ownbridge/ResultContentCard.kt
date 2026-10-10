package app.ownbridge

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PersistableBundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Read-only, selectable delivery text. Actions always operate on the complete value. */
@Composable
internal fun ResultContentCard(
    title: String,
    content: String,
    downloadFileName: String? = null,
    downloadMimeType: String = "text/plain",
    helpTopic: ResultHelpTopic? = null,
) {
    val context = LocalContext.current
    val showNotice = LocalTransientNotice.current
    val bodyStyle = MaterialTheme.typography.bodyMedium
    val cardColor = MaterialTheme.colorScheme.surface
    val shareTitle = stringResource(R.string.result_share_content, title)
    val enabledIconColor = MaterialTheme.colorScheme.primary
    val disabledIconColor = MaterialTheme.colorScheme.onSurfaceVariant
    val iconSize = with(LocalDensity.current) {
        MaterialTheme.typography.bodyLarge.fontSize.toDp() * 1.25f
    }
    val collapsedLines = with(LocalDensity.current) {
        val lineHeight = bodyStyle.lineHeight.toDp().value
        maxOf(1, (100.dp.value / lineHeight).toInt())
    }
    var expanded by remember(content) { mutableStateOf(false) }
    var canExpand by remember(content) { mutableStateOf(false) }
    var saveResult by remember(content) { mutableStateOf<Int?>(null) }
    var showHelp by remember(helpTopic) { mutableStateOf(false) }
    val saveDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(downloadMimeType),
    ) { uri ->
        if (uri != null) {
            val saved = runCatching {
                val output = requireNotNull(context.contentResolver.openOutputStream(uri, "wt"))
                output.use { it.write(content.toByteArray(Charsets.UTF_8)) }
            }.isSuccess
            if (saved) {
                saveResult = null
                showNotice(R.string.notice_save_success)
            } else saveResult = R.string.result_save_failed
        }
    }

    ContentCard {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, modifier = if (helpTopic != null) Modifier.weight(1f, fill = false)
                    else Modifier, style = MaterialTheme.typography.containerTitle)
                if (helpTopic != null) InlineHelpIcon(
                    stringResource(R.string.help_open),
                    MaterialTheme.typography.containerTitle,
                ) { showHelp = true }
            }
            Box {
                SelectionContainer {
                    Text(
                        content,
                        style = bodyStyle,
                        fontFamily = FontFamily.Monospace,
                        maxLines = if (expanded) Int.MAX_VALUE else collapsedLines,
                        overflow = TextOverflow.Clip,
                        onTextLayout = { if (!expanded) canExpand = it.hasVisualOverflow },
                    )
                }
                if (canExpand && !expanded) {
                    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(28.dp)
                        .background(Brush.verticalGradient(listOf(cardColor.copy(alpha = 0f), cardColor))))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText(title, content)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        clip.description.extras = PersistableBundle().apply {
                            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                        }
                    }
                    clipboard.setPrimaryClip(clip)
                    showNotice(R.string.notice_copy_success)
                }, enabled = content.isNotEmpty()) {
                    ProjectIcon(IconAsset.Copy,
                        contentDescription = stringResource(R.string.result_copy_content, title),
                        modifier = Modifier.size(iconSize),
                        tint = if (content.isNotEmpty()) enabledIconColor else disabledIconColor)
                }
                IconButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, content)
                    }
                    context.startActivity(Intent.createChooser(send, shareTitle))
                }, enabled = content.isNotEmpty()) {
                    ProjectIcon(IconAsset.Share,
                        contentDescription = stringResource(R.string.result_share_content, title),
                        modifier = Modifier.size(iconSize),
                        tint = if (content.isNotEmpty()) enabledIconColor else disabledIconColor)
                }
                IconButton(onClick = {
                    if (downloadFileName != null) {
                        saveResult = null
                        saveDocument.launch(downloadFileName)
                    }
                }, enabled = downloadFileName != null) {
                    ProjectIcon(IconAsset.Download,
                        contentDescription = stringResource(R.string.result_save_content, title),
                        modifier = Modifier.size(iconSize),
                        tint = if (downloadFileName != null) enabledIconColor else disabledIconColor)
                }
                IconButton(onClick = { expanded = !expanded }, enabled = canExpand) {
                    ProjectIcon(if (expanded) IconAsset.Collapse else IconAsset.Expand,
                        contentDescription = stringResource(if (expanded) R.string.result_collapse_content
                            else R.string.result_expand_content),
                        modifier = Modifier.size(iconSize),
                        tint = if (canExpand) enabledIconColor else disabledIconColor)
                }
            }
            saveResult?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall) }
        }
    }
    if (showHelp && helpTopic != null) {
        ResultHelpDialog(helpTopic) { showHelp = false }
    }
}
