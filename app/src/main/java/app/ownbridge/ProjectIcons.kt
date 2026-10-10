package app.ownbridge

import androidx.annotation.DrawableRes
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource

/** Local vector assets converted from the SVGs in OB.txt; see THIRD_PARTY_NOTICES.md. */
internal enum class IconAsset(@DrawableRes val drawableRes: Int) {
    SuccessFilled(R.drawable.ob_success_filled),
    Error(R.drawable.ob_error),
    Check(R.drawable.ob_check),
    ArrowBack(R.drawable.ob_arrow_back),
    ArrowForward(R.drawable.ob_arrow_forward),
    ChevronRight(R.drawable.ob_chevron_right),
    Retry(R.drawable.ob_retry),
    Settings(R.drawable.ob_settings),
    Download(R.drawable.ob_download),
    Bolt(R.drawable.ob_bolt),
    Key(R.drawable.ob_key),
    KeyOff(R.drawable.ob_key_off),
    Lightbulb(R.drawable.ob_lightbulb),
    Theme(R.drawable.ob_theme),
    BackToTop(R.drawable.ob_back_to_top),
    Address(R.drawable.ob_address),
    SshUser(R.drawable.ob_ssh_user),
    Copy(R.drawable.ob_copy),
    QrCode(R.drawable.ob_qr_code),
    Share(R.drawable.ob_share),
    Web(R.drawable.ob_web),
    Digit0(R.drawable.ob_digit_0),
    Digit1(R.drawable.ob_digit_1),
    Digit2(R.drawable.ob_digit_2),
    Digit3(R.drawable.ob_digit_3),
    Digit4(R.drawable.ob_digit_4),
    Digit5(R.drawable.ob_digit_5),
    Digit6(R.drawable.ob_digit_6),
    Digit7(R.drawable.ob_digit_7),
    Digit8(R.drawable.ob_digit_8),
    Digit9(R.drawable.ob_digit_9),
    GitHub(R.drawable.ob_github),
    Info(R.drawable.ob_info),
    Help(R.drawable.ob_help),
    Ping(R.drawable.ob_ping),
    PingCancel(R.drawable.ob_ping_cancel),
    Visibility(R.drawable.ob_visibility),
    VisibilityOff(R.drawable.ob_visibility_off),
    Warning(R.drawable.ob_warning),
    Mihomo(R.drawable.ob_mihomo),
    History(R.drawable.ob_history),
    Delete(R.drawable.ob_delete),
    ReadOnly(R.drawable.ob_read_only),
    Write(R.drawable.ob_write),
    Notebook(R.drawable.ob_notebook),
    ImportClash(R.drawable.ob_import_clash),
    Link(R.drawable.ob_link),
    Rocket(R.drawable.ob_rocket),
    Save(R.drawable.ob_save),
    Expand(R.drawable.ob_expand),
    Collapse(R.drawable.ob_collapse),
}

@Composable
internal fun ProjectIcon(
    asset: IconAsset,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color? = null,
) {
    Icon(
        painter = painterResource(asset.drawableRes),
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint ?: LocalContentColor.current,
    )
}
