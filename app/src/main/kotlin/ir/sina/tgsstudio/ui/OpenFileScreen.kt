package ir.sina.tgsstudio.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.sina.tgsstudio.R
import ir.sina.tgsstudio.document.Layer
import ir.sina.tgsstudio.document.LayerNode
import ir.sina.tgsstudio.document.LayerType
import ir.sina.tgsstudio.ui.OpenFileViewModel.State

@Composable
fun OpenFileScreen(vm: OpenFileViewModel) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.open(context.contentResolver, uri)
    }
    val openPicker = { picker.launch(arrayOf("*/*")) }

    Box(Modifier.fillMaxSize().background(StudioColors.Background).systemBarsPadding()) {
        when (val s = vm.state) {
            State.Empty -> Welcome(openPicker)
            State.Loading -> Column(
                Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator(color = StudioColors.Accent)
                Text(stringResource(R.string.opening), color = StudioColors.TextMuted, modifier = Modifier.padding(top = 16.dp))
            }
            is State.Failed -> Welcome(openPicker, error = s)
            is State.Loaded -> Loaded(s, openPicker)
        }
    }
}

@Composable
private fun Welcome(onOpen: () -> Unit, error: State.Failed? = null) {
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.app_name), color = StudioColors.Accent, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text(
            stringResource(R.string.tagline),
            color = StudioColors.TextMuted, fontSize = 16.sp, lineHeight = 24.sp,
            modifier = Modifier.padding(top = 12.dp, bottom = 32.dp),
        )
        if (error != null) {
            Column(
                Modifier.fillMaxWidth().padding(bottom = 24.dp).clip(RoundedCornerShape(12.dp))
                    .background(StudioColors.PanelRaised).padding(16.dp),
            ) {
                Text(stringResource(R.string.error_title), color = StudioColors.Danger, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(
                        when (error.reason) {
                            OpenFileViewModel.Failure.UNREADABLE -> R.string.error_unreadable
                            OpenFileViewModel.Failure.INVALID -> R.string.error_invalid
                            OpenFileViewModel.Failure.TOO_LARGE -> R.string.error_too_large
                        },
                    ),
                    color = StudioColors.TextPrimary, modifier = Modifier.padding(top = 6.dp),
                )
                error.detail?.let { Text(it, color = StudioColors.TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
            }
        }
        Button(
            onClick = onOpen,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            shape = RoundedCornerShape(14.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = StudioColors.Accent, contentColor = StudioColors.Background),
        ) { Text(stringResource(R.string.open_tgs), fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
private fun Loaded(s: State.Loaded, onOpenAnother: () -> Unit) {
    val d = s.document
    val rows = remember(s.tree) { flatten(s.tree) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item {
            Text(s.fileName, color = StudioColors.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Column(
                Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 10.dp).clip(RoundedCornerShape(14.dp))
                    .background(StudioColors.Panel).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                InfoRow(R.string.info_size, stringResource(R.string.value_canvas, d.width, d.height))
                InfoRow(
                    R.string.info_duration,
                    stringResource(R.string.value_duration, "%.2f".format(d.durationSeconds), "%.0f".format(d.frameRate), "%.0f".format(d.durationFrames)),
                )
                InfoRow(R.string.info_file_size, stringResource(R.string.value_file_size, "%.1f".format(s.fileSizeBytes / 1024.0)))
                InfoRow(
                    R.string.info_contents,
                    stringResource(R.string.value_contents, s.stats.layers, s.stats.shapeItems, s.stats.keyframes, s.stats.precomps),
                )
            }
            OutlinedButton(onClick = onOpenAnother, shape = RoundedCornerShape(12.dp)) {
                Text(stringResource(R.string.open_another), color = StudioColors.Accent)
            }
            Text(
                stringResource(R.string.layers_heading), color = StudioColors.TextMuted, fontSize = 14.sp,
                modifier = Modifier.padding(top = 18.dp, bottom = 4.dp),
            )
        }
        items(rows) { (depth, layer) -> LayerRow(depth, layer) }
    }
}

@Composable
private fun InfoRow(label: Int, value: String) {
    Column {
        Text(stringResource(label), color = StudioColors.TextMuted, fontSize = 12.sp)
        Text(value, color = StudioColors.TextPrimary, fontSize = 15.sp)
    }
}

@Composable
private fun LayerRow(depth: Int, layer: Layer) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(StudioColors.Panel)
            .padding(start = (12 + depth * 18).dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            typeLabel(layer), color = StudioColors.Accent, fontSize = 12.sp,
            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(StudioColors.PanelRaised).padding(horizontal = 8.dp, vertical = 3.dp),
        )
        Text(
            layer.name.ifBlank { stringResource(R.string.untitled) },
            color = if (layer.hidden) StudioColors.TextMuted else StudioColors.TextPrimary,
            modifier = Modifier.weight(1f), maxLines = 1,
        )
        if (layer.hidden) Text(stringResource(R.string.hidden), color = StudioColors.TextMuted, fontSize = 12.sp)
    }
}

@Composable
private fun typeLabel(layer: Layer): String = when (layer.type) {
    LayerType.SHAPE -> stringResource(R.string.type_shape)
    LayerType.NULL -> stringResource(R.string.type_null)
    LayerType.PRECOMP -> stringResource(R.string.type_precomp)
    LayerType.TEXT -> stringResource(R.string.type_text)
    LayerType.SOLID -> stringResource(R.string.type_solid)
    LayerType.IMAGE -> stringResource(R.string.type_image)
    else -> stringResource(R.string.type_other, layer.typeCode ?: -1)
}

private fun flatten(nodes: List<LayerNode>, depth: Int = 0): List<Pair<Int, Layer>> =
    nodes.flatMap { listOf(depth to it.layer) + flatten(it.children, depth + 1) }
