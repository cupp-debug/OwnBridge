package app.ownbridge

import android.os.Bundle
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {
    private lateinit var languagePreference: AppLanguagePreference
    private var language by mutableStateOf(AppLanguage.SYSTEM)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        languagePreference = AppLanguagePreference(this)
        language = languagePreference.current()
        enableEdgeToEdge()
        setContent {
            LocalizedContent(language) {
                OwnBridgeTheme {
                    OwnBridgeApp(language) { choice ->
                        if (languagePreference.select(choice)) {
                            language = choice
                            true
                        } else false
                    }
                }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        language = languagePreference.current()
    }
}
