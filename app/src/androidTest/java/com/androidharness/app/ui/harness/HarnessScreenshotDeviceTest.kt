package com.androidharness.app.ui.harness

import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.androidharness.app.ui.harness.components.Hairline
import com.androidharness.app.ui.harness.components.HarnessCard
import com.androidharness.app.ui.harness.components.ListRow
import com.androidharness.app.ui.harness.components.Pill
import com.androidharness.app.ui.harness.components.PrimaryButton
import com.androidharness.app.ui.harness.components.QuietButton
import com.androidharness.app.ui.harness.components.SectionLabel
import com.androidharness.app.ui.harness.theme.HarnessCodeTheme
import com.androidharness.app.ui.harness.theme.HarnessMono
import com.androidharness.app.ui.harness.theme.HarnessTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Screenshot sheet for the harness design system.
 *
 * Runs on a device or emulator only (the `ui-tests` CI job) and writes PNGs to
 * `Download/HarnessCodeScreens`, so the workflow can pull them off the emulator
 * and attach them as an artifact. It renders the shared components rather than a
 * whole screen because the screens need a live ChatViewModel and a provider key.
 */
@RunWith(AndroidJUnit4::class)
class HarnessScreenshotDeviceTest {

    @get:Rule
    val compose = createComposeRule()

    @Test fun lightSheet() = capture("harness-components-light", dark = false, fontScale = 1f)

    @Test fun darkSheet() = capture("harness-components-dark", dark = true, fontScale = 1f)

    @Test fun darkLargeFontSheet() = capture("harness-components-dark-large-font", dark = true, fontScale = 1.3f)

    private fun capture(name: String, dark: Boolean, fontScale: Float) {
        compose.setContent {
            HarnessCodeTheme(dark = dark) {
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.ui.platform.LocalDensity provides
                        androidx.compose.ui.unit.Density(compose.density.density, fontScale),
                ) {
                    ComponentSheet()
                }
            }
        }
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        saveToDownloads(name, bitmap)
    }

    private fun saveToDownloads(name: String, bitmap: Bitmap) {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "$name.png")
            put(MediaStore.Downloads.MIME_TYPE, "image/png")
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/HarnessCodeScreens")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = requireNotNull(context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
        context.contentResolver.openOutputStream(uri)?.use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
        }
    }
}

@androidx.compose.runtime.Composable
private fun ComponentSheet() {
    val palette = HarnessTheme.palette
    Column(
        Modifier
            .fillMaxSize()
            .background(palette.paper)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Harness Code", style = MaterialTheme.typography.displaySmall, color = palette.text)
        Text(
            "Components at ${palette.isDark.let { if (it) "night" else "day" }} · ${palette.paper}",
            style = MaterialTheme.typography.bodySmall,
            color = palette.muted,
        )
        SectionLabel("Buttons")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton("Approve", onClick = {})
            QuietButton("Reject", onClick = {})
        }
        SectionLabel("Chips")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Ask", selected = true)
            Pill("harness/main", monospace = true)
            Pill("3 queued")
        }
        SectionLabel("Tool card")
        HarnessCard(Modifier.fillMaxWidth()) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.fillMaxWidth()) {
                    Text("Edit 2 files", style = MaterialTheme.typography.titleSmall, color = palette.text)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "WorktreeManager.kt · HarnessPanels.kt",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = HarnessMono),
                        color = palette.muted,
                    )
                }
            }
        }
        SectionLabel("Rows")
        HarnessCard(Modifier.fillMaxWidth()) {
            ListRow(
                title = "Worktree isolation",
                subtitle = "harness-code · running",
                leading = {
                    androidx.compose.material3.Icon(
                        Icons.Outlined.Description,
                        contentDescription = null,
                        tint = palette.accentInk,
                    )
                },
            )
            Hairline()
            ListRow(title = "Pinned session", subtitle = "pinned", selected = true)
        }
        SectionLabel("Code")
        HarnessCard(Modifier.fillMaxWidth(), fill = palette.codeBlock) {
            Text(
                "+ root.mkdirs()",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = HarnessMono),
                color = palette.success,
            )
            Text(
                "- File(root).mkdirs()",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = HarnessMono),
                color = palette.danger,
            )
        }
        Spacer(Modifier.height(2.dp))
        Text("Type scale 38 · 30 · 24 · 20 · 17 · 15 · 13 · 11 sp", fontSize = 11.sp, color = palette.faint)
    }
}
