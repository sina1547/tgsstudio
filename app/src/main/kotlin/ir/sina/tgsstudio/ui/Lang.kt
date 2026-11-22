package ir.sina.tgsstudio.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

enum class Lang(val code: String) { FA("fa"), EN("en") }

val LocalLang = compositionLocalOf { Lang.EN }

/** Picks the Persian or English text for the current app language. */
@Composable
fun tr(en: String, fa: String): String = if (LocalLang.current == Lang.FA) fa else en

@Composable
fun WithLanguage(lang: Lang, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalLang provides lang,
        LocalLayoutDirection provides if (lang == Lang.FA) LayoutDirection.Rtl else LayoutDirection.Ltr,
        content = content,
    )
}

/** Editor chrome stays left-to-right in both languages (timeline and canvas have a fixed time axis). */
@Composable
fun Ltr(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr, content = content)
}
