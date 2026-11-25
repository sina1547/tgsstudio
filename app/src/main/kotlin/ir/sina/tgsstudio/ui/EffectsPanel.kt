package ir.sina.tgsstudio.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.sina.tgsstudio.data.PresetStore
import ir.sina.tgsstudio.editor.EffectKind
import ir.sina.tgsstudio.editor.EffectNote
import ir.sina.tgsstudio.editor.EffectParams
import ir.sina.tgsstudio.editor.EffectPreset
import kotlin.math.abs
import kotlin.math.sin
import ir.sina.tgsstudio.editor.EffectResult

@Composable
fun kindLabel(k: EffectKind): String = when (k) {
    EffectKind.FADE_IN -> tr("Fade In", "ظاهر شدن تدریجی")
    EffectKind.FADE_OUT -> tr("Fade Out", "محو شدن")
    EffectKind.ZOOM_IN -> tr("Zoom In", "بزرگ‌نمایی ورودی")
    EffectKind.ZOOM_OUT -> tr("Zoom Out", "کوچک شدن")
    EffectKind.SHAKE -> tr("Shake", "لرزش")
    EffectKind.BOUNCE -> tr("Bounce", "جهش")
    EffectKind.PULSE -> tr("Pulse", "ضربان")
    EffectKind.ROTATE -> tr("Rotation", "چرخش")
    EffectKind.SCALE -> tr("Scale", "تغییر اندازه")
    EffectKind.SLIDE_IN -> tr("Slide In", "ورود با لغزش")
    EffectKind.SLIDE_OUT -> tr("Slide Out", "خروج با لغزش")
    EffectKind.OPACITY -> tr("Opacity", "تغییر شفافیت")
    EffectKind.FLOAT -> tr("Float (loop)", "شناور (حلقه)")
    EffectKind.DRAW_ON -> tr("Draw On", "رسم تدریجی")
    EffectKind.DRAW_OFF -> tr("Draw Off", "جمع شدن مسیر")
    EffectKind.GLOW -> tr("Glow", "درخشش")
    EffectKind.BLUR -> tr("Blur", "محو‌کردن (بلور)")
}


private data class EffectCategoryUi(val id: String, val en: String, val fa: String, val kinds: List<EffectKind>)

private fun effectCategories() = listOf(
    EffectCategoryUi("draw", "Drawing & Edge", "طراحی و لبه", listOf(EffectKind.DRAW_ON, EffectKind.DRAW_OFF)),
    EffectCategoryUi("color", "Color & Light", "رنگ و نور", listOf(EffectKind.GLOW)),
    EffectCategoryUi("warp", "Distortion / Warp", "اعوجاج و تغییر شکل", listOf(EffectKind.SHAKE, EffectKind.BOUNCE)),
    EffectCategoryUi("blur", "Blur", "محو و بلور", listOf(EffectKind.BLUR)),
    EffectCategoryUi("move", "Move / Transform", "حرکت و تبدیل", listOf(EffectKind.ZOOM_IN, EffectKind.ZOOM_OUT, EffectKind.SCALE, EffectKind.ROTATE, EffectKind.SLIDE_IN, EffectKind.SLIDE_OUT)),
    EffectCategoryUi("opacity", "Opacity / Visibility", "شفافیت و نمایش", listOf(EffectKind.FADE_IN, EffectKind.FADE_OUT, EffectKind.OPACITY)),
    EffectCategoryUi("repeat", "Procedural / Repeat", "رویه‌ای و تکرار", listOf(EffectKind.PULSE, EffectKind.FLOAT)),
)

private fun effectCategoryLabel(c: EffectCategoryUi) = tr(c.en, c.fa)

private fun noteText(state: EditorState, r: EffectResult): String? {
    val fa = state.isFa
    val parts = r.notes.mapNotNull { n ->
        when (n) {
            EffectNote.BLUR_UNSUPPORTED -> if (fa) "بلور در استیکر تلگرام مجاز نیست (افکت لایه در TGS ممنوع است)." else "Blur isn't allowed in Telegram stickers (TGS forbids layer effects)."
            EffectNote.SHORTENED_TO_TIMELINE -> if (fa) "مدت افکت کوتاه شد تا در خط زمان جا شود." else "The effect was shortened to fit the timeline."
            EffectNote.NOT_ENOUGH_TIME -> if (fa) "در خط زمان جایی برای این افکت نمانده است." else "There isn't enough room on the timeline for this effect."
            EffectNote.NO_STROKE -> if (fa) "این شکل خط دور ندارد؛ برش مسیر روی خط دور دیده می‌شود." else "This shape has no stroke; Trim Paths is most visible on strokes."
            EffectNote.NOT_A_SHAPE_LAYER -> if (fa) "این افکت فقط روی لایهٔ شکل کار می‌کند." else "This effect only works on shape layers."
            EffectNote.GLOW_ADDS_LAYERS -> if (fa) "درخشش با دو لایهٔ اضافه ساخته شد (حجم فایل کمی بیشتر می‌شود)." else "Glow was built with two extra layers (the file gets a little bigger)."
            EffectNote.NO_TARGET -> if (fa) "اول یک لایه را انتخاب کنید." else "Select a layer first."
        }
    }
    return parts.joinToString(" ").ifEmpty { null }
}

private fun startPreview(state: EditorState, kind: EffectKind, p: EffectParams) {
    discardPreview(state)
    val sel = state.session.selection.toList()
    if (sel.isEmpty()) { state.message = if (state.isFa) "اول یک لایه را انتخاب کنید." else "Select a layer first."; return }
    state.fxSelectionBefore = sel
    state.session.beginGesture("Effect")            // everything the effect does stays undoable as one step until it is kept
    val r = state.session.applyEffect(kind, p)
    if (r.applied == 0) { state.session.cancelGesture(); state.session.select(sel); state.message = noteText(state, r); state.changed(); return }
    state.fxPreviewing = true
    state.message = noteText(state, r)
    state.seek(p.startFrame); state.loop = true; state.playing = true
    state.changed()
}

private fun keepPreview(state: EditorState) { state.session.endGesture(); state.fxPreviewing = false; state.playing = false; state.changed() }

private fun discardPreview(state: EditorState) {
    if (!state.fxPreviewing) return
    state.session.cancelGesture(); state.session.select(state.fxSelectionBefore)
    state.fxPreviewing = false; state.playing = false; state.changed()
}

@Composable
private fun Chip(text: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Text(
        text, color = if (!enabled) StudioColors.TextMuted else if (selected) androidx.compose.ui.graphics.Color.White else StudioColors.TextPrimary, fontSize = 13.sp, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(if (selected) StudioColors.Accent else StudioColors.PanelHigh).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 9.dp),
    )
}

@Composable
private fun EffectMotionPreview(effect: EffectKind) {
    val transition = rememberInfiniteTransition(label = "effect-" + effect.id)
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(950, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
        label = "effect-preview-phase",
    )
    Canvas(Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(8.dp)).background(StudioColors.PanelHigh)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val wave = sin(phase * 6.28318f)
        var scale = 1f
        var alpha = 1f
        var dx = 0f
        var dy = 0f
        var angle = 0f
        when (effect) {
            EffectKind.FADE_IN -> alpha = phase
            EffectKind.FADE_OUT -> alpha = 1f - phase
            EffectKind.ZOOM_IN -> scale = 0.55f + phase * 0.55f
            EffectKind.ZOOM_OUT -> scale = 1.1f - phase * 0.55f
            EffectKind.SHAKE -> dx = sin(phase * 25.13f) * size.width * 0.055f
            EffectKind.BOUNCE -> dy = -abs(sin(phase * 3.14159f)) * size.height * 0.18f
            EffectKind.PULSE -> scale = 0.78f + abs(wave) * 0.32f
            EffectKind.ROTATE -> angle = phase * 360f
            EffectKind.SCALE -> scale = 0.65f + phase * 0.65f
            EffectKind.SLIDE_IN -> dx = (phase - 1f) * size.width * 0.22f
            EffectKind.SLIDE_OUT -> dx = phase * size.width * 0.22f
            EffectKind.OPACITY -> alpha = 0.18f + phase * 0.82f
            EffectKind.FLOAT -> dy = wave * size.height * 0.12f
            EffectKind.DRAW_ON, EffectKind.DRAW_OFF, EffectKind.GLOW, EffectKind.BLUR -> Unit
        }
        val w = 30f * scale
        val h = 19f * scale
        val left = cx - w / 2f + dx
        val top = cy - h / 2f + dy
        if (effect == EffectKind.GLOW) {
            drawCircle(StudioColors.Accent.copy(alpha = 0.12f + phase * 0.22f), radius = 25f + phase * 7f, center = Offset(cx, cy))
        }
        if (effect == EffectKind.BLUR) {
            for (i in 1..3) {
                drawRoundRect(
                    color = StudioColors.Accent.copy(alpha = 0.10f * (4 - i)),
                    topLeft = Offset(left + (i - 2) * 4f, top),
                    size = Size(w, h),
                    cornerRadius = CornerRadius(6f, 6f),
                )
            }
        }
        rotate(angle, pivot = Offset(cx, cy)) {
            drawRoundRect(
                color = StudioColors.Accent,
                topLeft = Offset(left, top),
                size = Size(w, h),
                cornerRadius = CornerRadius(6f, 6f),
                alpha = alpha.coerceIn(0.08f, 1f),
            )
        }
        if (effect == EffectKind.DRAW_ON || effect == EffectKind.DRAW_OFF) {
            val sweep = if (effect == EffectKind.DRAW_ON) 360f * phase else 360f * (1f - phase)
            drawArc(
                color = StudioColors.TextPrimary.copy(alpha = 0.85f),
                startAngle = -90f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(cx - 21f, cy - 21f),
                size = Size(42f, 42f),
                style = Stroke(width = 2.5f),
            )
        }
    }
}
/** Ready-made effects: pick, tune, preview on the canvas, then keep. Stackable; custom presets can be saved. */
@Composable
fun EffectsPanel(state: EditorState) {
    val context = LocalContext.current
    val store = remember { PresetStore(context) }
    var presets by remember { mutableStateOf(store.load()) }
    var kindId by rememberSaveable { mutableStateOf(EffectKind.FADE_IN.id) }
    val kind = EffectKind.byId(kindId) ?: EffectKind.FADE_IN
    val fps = state.session.doc.frameRate
    var params by remember { mutableStateOf(kind.defaults(fps, state.frame)) }
    var naming by remember { mutableStateOf(false) }
    var showEffectSettings by rememberSaveable { mutableStateOf(false) }
    var browseMode by rememberSaveable { mutableStateOf("categories") }
    var categoryId by rememberSaveable { mutableStateOf("draw") }
    val catalogPrefs = remember { context.getSharedPreferences("effect_catalog", 0) }
    var favorites by remember { mutableStateOf(catalogPrefs.getStringSet("favorites", emptySet())?.toSet() ?: emptySet()) }
    var recent by remember { mutableStateOf(catalogPrefs.getString("recent", "").orEmpty().split("|").filter { it.isNotBlank() }) }
    val tick = state.tick
    val selection = remember(tick) { state.session.selection }

    fun rememberRecent(effect: EffectKind) {
        recent = (listOf(effect.id) + recent.filterNot { it == effect.id }).take(8)
        catalogPrefs.edit().putString("recent", recent.joinToString("|")).apply()
    }
    fun toggleFavorite(effect: EffectKind) {
        favorites = if (effect.id in favorites) favorites - effect.id else favorites + effect.id
        catalogPrefs.edit().putStringSet("favorites", favorites.toSet()).apply()
    }
    fun chooseEffect(effect: EffectKind) {
        discardPreview(state)
        kindId = effect.id
        params = effect.defaults(fps, state.frame)
        showEffectSettings = true
    }

    DisposableEffect(Unit) { onDispose { if (state.fxPreviewing) discardPreview(state) } }   // closing the panel drops an unkept preview

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Ic.Effects, StudioColors.Accent, 20.dp)
            Text(tr("Effects", "افکت‌ها"), color = StudioColors.TextPrimary, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 8.dp).weight(1f))
            Text(if (selection.isEmpty()) tr("Select a layer", "یک لایه انتخاب کنید") else tr("${selection.map { it.layer }.distinct().size} layer(s)", "${selection.map { it.layer }.distinct().size} لایه"), color = if (selection.isEmpty()) StudioColors.Warning else StudioColors.TextMuted, fontSize = 12.sp)
        }
        if (!showEffectSettings) {
            Text(tr("Browse effects", "مرور افکت‌ها"), color = StudioColors.TextMuted, fontSize = 12.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip(tr("Categories", "دسته‌بندی‌ها"), browseMode == "categories") { browseMode = "categories" }
                Chip(tr("Often used", "پراستفاده"), browseMode == "popular") { browseMode = "popular" }
                Chip(tr("Favorites", "علاقه‌مندی‌ها"), browseMode == "favorites") { browseMode = "favorites" }
                Chip(tr("Last added", "اخیر"), browseMode == "recent") { browseMode = "recent" }
            }
            if (browseMode == "categories") {
                effectCategories().chunked(2).forEach { rowCategories ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowCategories.forEach { category ->
                            Column(
                                Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                                    .background(if (category.id == categoryId) StudioColors.AccentSoft else StudioColors.PanelRaised)
                                    .clickable { categoryId = category.id; browseMode = "category" }
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(7.dp),
                            ) {
                                Icon(Ic.Effects, StudioColors.Accent, 22.dp)
                                Text(effectCategoryLabel(category), color = StudioColors.TextPrimary, fontSize = 12.sp, maxLines = 2)
                                Text("${category.kinds.size} ${tr("effects", "افکت")}", color = StudioColors.TextMuted, fontSize = 10.sp)
                            }
                        }
                        if (rowCategories.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            } else {
                if (browseMode == "category") {
                    val category = effectCategories().firstOrNull { it.id == categoryId }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBtn(Ic.Back, { browseMode = "categories" }, size = 36.dp, iconSize = 18.dp)
                        Text(category?.let(::effectCategoryLabel) ?: tr("Effects", "افکت‌ها"), color = StudioColors.TextPrimary, fontSize = 14.sp)
                    }
                }
                val visibleKinds = when (browseMode) {
                    "popular" -> listOf(EffectKind.FADE_IN, EffectKind.GLOW, EffectKind.SHAKE, EffectKind.SCALE, EffectKind.DRAW_ON, EffectKind.PULSE)
                    "favorites" -> EffectKind.entries.filter { it.id in favorites }
                    "recent" -> recent.mapNotNull { EffectKind.byId(it) }
                    else -> effectCategories().firstOrNull { it.id == categoryId }?.kinds.orEmpty()
                }
                if (visibleKinds.isEmpty()) {
                    Text(
                        if (browseMode == "favorites") tr("No favorites yet. Tap ☆ on an effect to save it here.", "هنوز افکتی ذخیره نشده؛ با ☆ کنار افکت آن را اضافه کنید.")
                        else if (browseMode == "recent") tr("Effects you open will appear here.", "افکت‌هایی که باز می‌کنید اینجا نمایش داده می‌شوند.")
                        else tr("No effects in this category yet.", "هنوز افکتی در این دسته وجود ندارد."),
                        color = StudioColors.TextMuted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 18.dp),
                    )
                } else {
                    visibleKinds.distinct().chunked(2).forEach { rowKinds ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowKinds.forEach { effect ->
                                Column(
                                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                                        .background(if (effect == kind) StudioColors.AccentSoft else StudioColors.PanelRaised)
                                        .clickable { chooseEffect(effect) }
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(7.dp),
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Ic.Effects, if (effect.supported) StudioColors.Accent else StudioColors.TextMuted, 21.dp)
                                        Spacer(Modifier.weight(1f))
                                        Text(
                                            if (effect.id in favorites) "★" else "☆",
                                            color = if (effect.id in favorites) StudioColors.Warning else StudioColors.TextMuted,
                                            modifier = Modifier.clickable { toggleFavorite(effect) }.padding(horizontal = 5.dp),
                                            fontSize = 18.sp,
                                        )
                                    }
                                    EffectMotionPreview(effect)
                                    Text(kindLabel(effect), color = if (effect.supported) StudioColors.TextPrimary else StudioColors.TextMuted, fontSize = 12.sp, maxLines = 2)
                                    if (!effect.supported) Text(tr("Not supported by TGS", "در TGS پشتیبانی نمی‌شود"), color = StudioColors.Warning, fontSize = 9.sp)
                                }
                            }
                            if (rowKinds.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconBtn(Ic.Back, { discardPreview(state); showEffectSettings = false }, size = 38.dp, iconSize = 20.dp)
                Text(kindLabel(kind), color = StudioColors.TextPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
                SmallButton(tr("All effects", "همه افکت‌ها"), { discardPreview(state); showEffectSettings = false })
            }
            if (!kind.supported) {
                Text(tr("Blur is a layer effect, and Telegram's TGS format forbids layer effects, so it can't be added. Glow gives a similar soft halo.", "بلور یک افکت لایه است و فرمت TGS تلگرام افکت لایه را ممنوع کرده؛ پس قابل افزودن نیست. «درخشش» حالت هالهٔ نرم مشابهی می‌دهد."), color = StudioColors.Warning, fontSize = 13.sp)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) { Labeled(tr("Start frame", "فریم شروع")) { NumberField(params.startFrame, { params = params.copy(startFrame = it.coerceAtLeast(0.0)) }, decimals = 0) } }
                    Column(Modifier.weight(1f)) { Labeled(tr("Duration (frames)", "مدت (فریم)")) { NumberField(params.durationFrames, { params = params.copy(durationFrames = it.coerceAtLeast(2.0)) }, decimals = 0) } }
                    SmallButton(tr("Playhead", "پخش‌کننده"), { params = params.copy(startFrame = Math.rint(state.frame)) }, modifier = Modifier.padding(top = 18.dp))
                }
                Text("${tr("One cycle", "یک دور")} = ${fmt(params.durationFrames / fps, 2)} ${tr("s", "ثانیه")}", color = StudioColors.TextMuted, fontSize = 12.sp)
                Labeled("${tr("Strength", "شدت")}  ${fmt(params.strength, 0)}%") {
                    GestureSlider(params.strength.toFloat(), 10f..300f, {}, { params = params.copy(strength = it.toDouble()) }, {})
                }
                if (kind.periodic) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Repeat until the end", "تکرار تا پایان"), color = StudioColors.TextPrimary, modifier = Modifier.weight(1f))
                    Switch(params.loop, { params = params.copy(loop = it) }, colors = SwitchDefaults.colors(checkedTrackColor = StudioColors.Accent))
                }
                if (kind.directional) {
                    val labels = if (kind == EffectKind.ROTATE) listOf(tr("Anticlockwise", "پادساعتگرد"), tr("Clockwise", "ساعتگرد")) else listOf(tr("From left", "از چپ"), tr("From right", "از راست"), tr("From top", "از بالا"), tr("From bottom", "از پایین"))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { labels.forEachIndexed { i, l -> Chip(l, params.direction == i) { params = params.copy(direction = i) } } }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.fxPreviewing) {
                        SmallButton(tr("Keep", "نگه‌دار"), { keepPreview(state) }, primary = true, ic = Ic.Check)
                        SmallButton(tr("Discard", "دور بینداز"), { discardPreview(state) })
                        SmallButton(tr("Replay", "پخش دوباره"), { startPreview(state, kind, params) })
                    } else {
                        SmallButton(tr("Preview", "پیش‌نمایش"), { rememberRecent(kind); startPreview(state, kind, params) }, ic = Ic.Play, enabled = selection.isNotEmpty())
                        SmallButton(tr("Apply", "اعمال"), {
                            val r = state.act { applyEffect(kind, params) }
                            if (r.applied > 0) rememberRecent(kind)
                            state.message = noteText(state, r) ?: (if (state.isFa) "افکت اعمال شد" else "Effect applied")
                            if (r.applied > 0) { state.seek(params.startFrame); state.playing = true }
                        }, primary = true, enabled = selection.isNotEmpty())
                        SmallButton(tr("Save preset", "ذخیره به‌عنوان پیش‌تنظیم"), { naming = true })
                    }
                }
            }
            if (state.fxPreviewing) Text(tr("Previewing: Keep it, or Discard to undo. Closing this panel discards it.", "در حال پیش‌نمایش: نگه دارید یا دور بیندازید. بستن پنل آن را دور می‌اندازد."), color = StudioColors.TextMuted, fontSize = 12.sp)
        }
        if (presets.isNotEmpty()) {
            Text(tr("My presets", "پیش‌تنظیم‌های من"), color = StudioColors.TextMuted, fontSize = 13.sp)
            presets.forEach { p ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(StudioColors.PanelRaised)) {
                    Text("${p.name}  ·  ${kindLabel(p.kind)}", color = StudioColors.TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f).clickable { discardPreview(state); kindId = p.kind.id; params = p.params.copy(startFrame = Math.rint(state.frame)) }.padding(12.dp))
                    IconBtn(Ic.Trash, { presets = presets - p; store.save(presets) }, size = 40.dp, iconSize = 18.dp, tint = StudioColors.Danger)
                }
            }
        }
    }
    if (naming) NameDialog(tr("Preset name", "نام پیش‌تنظیم"), kindLabel(kind), { n -> presets = presets + EffectPreset(n, kind, params.copy(startFrame = 0.0)); store.save(presets); naming = false }, { naming = false })
}
