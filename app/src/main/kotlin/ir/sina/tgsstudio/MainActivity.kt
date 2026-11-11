package ir.sina.tgsstudio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import ir.sina.tgsstudio.ui.OpenFileViewModel
import ir.sina.tgsstudio.ui.StudioTheme
import ir.sina.tgsstudio.ui.OpenFileScreen

class MainActivity : ComponentActivity() {
    private val viewModel: OpenFileViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            StudioTheme {
                OpenFileScreen(viewModel)
            }
        }
    }
}
