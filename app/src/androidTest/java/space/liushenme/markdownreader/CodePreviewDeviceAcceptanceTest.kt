package space.liushenme.markdownreader

import android.graphics.Bitmap
import android.graphics.Color
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Date
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.math.abs
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import space.liushenme.markdownreader.data.local.AppDatabase
import space.liushenme.markdownreader.data.local.entity.GitProjectEntity
import space.liushenme.markdownreader.git.GitProjectStorage

@RunWith(AndroidJUnit4::class)
class CodePreviewDeviceAcceptanceTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun openFixtureAndCaptureCodeReader() {
        seedFixtureProject()
        launchMainActivity()

        waitForText(PROJECT_NAME)
        composeRule
            .onAllNodes(
                hasAnyDescendant(hasText(PROJECT_NAME)) and hasClickAction(),
                useUnmergedTree = true,
            )
            .onFirst()
            .performClick()

        waitForText("LargeService.java")
        composeRule
            .onAllNodes(
                hasAnyDescendant(hasText("LargeService.java")) and hasClickAction(),
                useUnmergedTree = true,
            )
            .onFirst()
            .performClick()

        waitForText("public final class LargeService {")

        captureAndAssertCodeBody("code_reader_initial")
        repeat(8) {
            composeRule.onRoot().performTouchInput { swipeUp() }
            composeRule.waitForIdle()
            Thread.sleep(300)
        }
        // Full-screen source is host-drawn without semantics, so scrolling cannot be verified by
        // querying source text. The screenshot assertion below proves that the visible viewport
        // was repainted with source instead of exposing a blank code surface.
        captureAndAssertCodeBody("code_reader_scrolled")

        composeRule.onRoot().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("阅读设置").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("VS Light+").performScrollTo().assertExists()
        capture("code_theme_picker")

        listOf("VS Dark+", "Monokai", "GitHub Light").forEachIndexed { index, theme ->
            composeRule.onNodeWithText(theme).performScrollTo().performClick()
            composeRule.waitForIdle()
            Thread.sleep(500)
            capture("code_theme_${index + 1}")
        }
    }

    @Test
    fun measureLargeCodeScroll() {
        seedFixtureProject()
        launchMainActivity()

        waitForText(PROJECT_NAME)
        composeRule
            .onAllNodes(
                hasAnyDescendant(hasText(PROJECT_NAME)) and hasClickAction(),
                useUnmergedTree = true,
            )
            .onFirst()
            .performClick()

        waitForText("LargeService.java")
        composeRule
            .onAllNodes(
                hasAnyDescendant(hasText("LargeService.java")) and hasClickAction(),
                useUnmergedTree = true,
            )
            .onFirst()
            .performClick()
        Thread.sleep(3_000)

        runShell("dumpsys gfxinfo ${targetPackage()} reset")
        repeat(20) {
            composeRule.onRoot().performTouchInput {
                swipeUp(durationMillis = 420L)
            }
            composeRule.waitForIdle()
            Thread.sleep(250)
        }
        Thread.sleep(800)
        writeAcceptanceFile(
            "large_code_gfxinfo.txt",
            runShell("dumpsys gfxinfo ${targetPackage()} framestats"),
        )
    }

    private fun writeAcceptanceFile(name: String, content: String) {
        val output = File(
            InstrumentationRegistry.getInstrumentation()
                .targetContext.getExternalFilesDir("acceptance"),
            name,
        )
        output.parentFile?.mkdirs()
        output.writeText(content)
    }

    private fun seedFixtureProject() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        runBlocking(Dispatchers.IO) {
            val database = AppDatabase.getDatabase(context)
            database.gitProjectDao().deleteById(FIXTURE_PROJECT_ID)
            database.bookDao()
                .getBooksByGitProjectId(FIXTURE_PROJECT_ID)
                .forEach { database.bookDao().deleteBook(it) }
            val projectDir = GitProjectStorage.projectDir(context, FIXTURE_PROJECT_ID)
            projectDir.deleteRecursively()
            File(projectDir, ".git").mkdirs()
            File(projectDir, ".git/HEAD").writeText("ref: refs/heads/main\n")
            projectDir.resolve("LargeService.java").writeText(
                buildString {
                    appendLine("public final class LargeService {")
                    repeat(650) { index ->
                        appendLine("    public static final class Worker$index {")
                        appendLine("        private final String name = \"worker-$index\";")
                        appendLine("        public int process$index(int input) {")
                        appendLine("            int normalized = input + $index;")
                        appendLine("            int doubled = normalized * 2;")
                        appendLine("            return doubled - $index;")
                        appendLine("        }")
                        appendLine("    }")
                    }
                    appendLine("}")
                },
            )
            projectDir.resolve("Sample.java").writeText(
                """
                public final class VehicleOilElecConvertUtil {
                    // Sticky-scroll acceptance fixture.
                    public static class Converter {
                        public static int convertToLiters(int value) {
                            int grids = value / 2;
                            int first = grids + 10;
                            int second = first * 2;
                            int third = second / 3;
                            int fourth = third + 40;
                            int fifth = fourth / 5;
                            int sixth = fifth + 60;
                            int seventh = sixth / 7;
                            int eighth = seventh + 80;
                            int ninth = eighth / 9;
                            int tenth = ninth + 100;
                            int eleventh = tenth / 11;
                            int twelfth = eleventh + 120;
                            int thirteenth = twelfth / 13;
                            return (int) Math.round(thirteenth / (double) OIL_GRID_TOTAL);
                        }
                    }

                    private static final int OIL_GRID_TOTAL = 100;
                }
                """.trimIndent(),
            )
            projectDir.resolve("sample.py").writeText(
                """
                class VehicleConverter:
                    def convert(self, value):
                        normalized = value / 2
                        return round(normalized)
                """.trimIndent(),
            )
            projectDir.resolve("App.vue").writeText(
                """
                <template>
                  <main>{{ count }}</main>
                </template>
                <script setup>
                function increment(step) {
                  count += step
                }
                </script>
                """.trimIndent(),
            )
            projectDir.resolve("data.json").writeText(
                """
                {
                  "enabled": true,
                  "count": 42
                }
                """.trimIndent(),
            )
            projectDir.resolve("sample.ts").writeText(
                """
                export function greet(name: string): string {
                  return `Hello, ${'$'}{name}`
                }
                """.trimIndent(),
            )
            database.gitProjectDao().insert(
                GitProjectEntity(
                    id = FIXTURE_PROJECT_ID,
                    title = PROJECT_NAME,
                    remoteUrl = "https://github.com/liushenme/md-reader-code-preview-fixture",
                    localPath = projectDir.absolutePath,
                    addTime = Date(),
                ),
            )
        }
    }

    private fun runShell(command: String): String {
        val descriptor = InstrumentationRegistry.getInstrumentation()
            .uiAutomation
            .executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use {
            it.readText()
        }
    }

    /**
     * MIUI aborts `ActivityScenario.launch` when the instrumentation process is still considered
     * background. Launching through UiAutomation uses the shell identity, which is allowed to
     * bring the target activity forward before Compose starts querying the semantics tree.
     */
    private fun launchMainActivity() {
        val output = runShell(
            "am start -W -n ${targetPackage()}/.MainActivity",
        )
        check(output.contains("Status: ok")) {
            "Failed to launch MainActivity:\n$output"
        }
    }

    private fun targetPackage(): String =
        InstrumentationRegistry.getInstrumentation().targetContext.packageName

    private fun waitForText(text: String, substring: Boolean = false) {
        waitUntil(20_000) {
            composeRule
                .onAllNodes(hasText(text, substring = substring), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private fun waitUntil(timeoutMillis: Long, condition: () -> Boolean) {
        composeRule.waitUntil(timeoutMillis, condition)
    }

    private fun capture(name: String) {
        val bitmap = takeScreenshot(name)
        bitmap.recycle()
    }

    private fun captureAndAssertCodeBody(name: String) {
        val bitmap = takeScreenshot(name)
        try {
            assertCodeBodyPainted(bitmap, name)
        } finally {
            bitmap.recycle()
        }
    }

    private fun takeScreenshot(name: String): Bitmap {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        val dir = instrumentation.targetContext.getExternalFilesDir("acceptance")
            ?: error("No external files directory")
        dir.mkdirs()
        FileOutputStream(File(dir, "$name.png")).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
        return bitmap
    }

    /**
     * The full-screen source body is host-drawn and intentionally has no semantics. A screenshot
     * is therefore the only reliable way to prove that scrolling painted the visible viewport
     * instead of leaving the reader surface blank.
     */
    private fun assertCodeBodyPainted(bitmap: Bitmap, name: String) {
        val left = (bitmap.width * 0.04f).toInt()
        val right = (bitmap.width * 0.96f).toInt()
        val top = (bitmap.height * 0.30f).toInt()
        val bottom = (bitmap.height * 0.88f).toInt()
        val counts = HashMap<Int, Int>()
        var sampled = 0
        for (y in top until bottom step 4) {
            for (x in left until right step 4) {
                val pixel = bitmap.getPixel(x, y)
                counts[pixel] = (counts[pixel] ?: 0) + 1
                sampled++
            }
        }
        val background = counts.maxByOrNull { it.value }?.key
            ?: error("$name: screenshot has no pixels")
        val backgroundCount = counts[background] ?: 0
        check(sampled > 0 && backgroundCount > sampled * 0.65) {
            "$name: expected a mostly uniform code surface, background=$backgroundCount sampled=$sampled"
        }

        var sourcePixels = 0
        for (y in top until bottom step 4) {
            for (x in left until right step 4) {
                if (colorDistance(bitmap.getPixel(x, y), background) > 28) {
                    sourcePixels++
                }
            }
        }
        check(sourcePixels > sampled * 0.005) {
            "$name: visible code body is blank, sourcePixels=$sourcePixels sampled=$sampled"
        }

        val bandHeight = (bottom - top) / 12
        var paintedBands = 0
        repeat(12) { band ->
            val bandTop = top + band * bandHeight
            val bandBottom = if (band == 11) bottom else bandTop + bandHeight
            var bandPixels = 0
            for (y in bandTop until bandBottom step 4) {
                for (x in left until right step 4) {
                    if (colorDistance(bitmap.getPixel(x, y), background) > 28) {
                        bandPixels++
                        break
                    }
                }
            }
            if (bandPixels > 0) paintedBands++
        }
        check(paintedBands >= 9) {
            "$name: code body only painted $paintedBands of 12 vertical bands"
        }
    }

    private fun colorDistance(first: Int, second: Int): Int =
        abs(Color.red(first) - Color.red(second)) +
            abs(Color.green(first) - Color.green(second)) +
            abs(Color.blue(first) - Color.blue(second))

    private companion object {
        const val FIXTURE_PROJECT_ID = 9101L
        const val PROJECT_NAME = "md-reader-code-preview-fixture"
    }
}
