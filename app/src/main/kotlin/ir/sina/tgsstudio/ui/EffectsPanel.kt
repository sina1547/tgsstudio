package ir.sina.tgsstudio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
    val tick = state.tick
    val selection = remember(tick) { state.session.selection }

    DisposableEffect(Unit) { onDispose { if (state.fxPreviewing) discardPreview(state) } }   // closing the panel drops an unkept preview

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Ic.Effects, StudioColors.Accent, 20.dp)
            Text(tr("Effects", "افکت‌ها"), color = StudioColors.TextPrimary, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 8.dp).weight(1f))
            Text(if (selection.isEmpty()) tr("Select a layer", "یک لایه انتخاب کنید") else tr("${selection.map { it.layer }.distinct().size} layer(s)", "${selection.map { it.layer }.distinct().size} لایه"), color = if (selection.isEmpty()) StudioColors.Warning else StudioColors.TextMuted, fontSize = 12.sp)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            EffectKind.entries.forEach { k -> Chip(kindLabel(k), k == kind, k.supported) { discardPreview(state); kindId = k.id; params = k.defaults(fps, state.frame) } }
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
                    SmallButton(tr("Preview", "پیش‌نمایش"), { startPreview(state, kind, params) }, ic = Ic.Play, enabled = selection.isNotEmpty())
                    SmallButton(tr("Apply", "اعمال"), {
                        val r = state.act { applyEffect(kind, params) }
                        state.message = noteText(state, r) ?: (if (state.isFa) "افکت اعمال شد" else "Effect applied")
                        if (r.applied > 0) { state.seek(params.startFrame); state.playing = true }
                    }, primary = true, enabled = selection.isNotEmpty())
                    SmallButton(tr("Save preset", "ذخیره به‌عنوان پیش‌تنظیم"), { naming = true })
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
