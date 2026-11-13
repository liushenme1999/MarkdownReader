package space.liushenme.markdownreader

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import space.liushenme.markdownreader.data.local.AppDatabase
import space.liushenme.markdownreader.data.local.entity.GitProjectEntity
import space.liushenme.markdownreader.git.GitProjectStorage

@RunWith(AndroidJUnit4::class)
class SeedCodePreviewFixtureTest {

    @Test
    fun seedFixtureProject() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        runBlocking(Dispatchers.IO) {
            val database = AppDatabase.getDatabase(context)
            database.gitProjectDao().deleteById(PROJECT_ID)
            val projectDir = GitProjectStorage.projectDir(context, PROJECT_ID)
            projectDir.deleteRecursively()
            File(projectDir, ".git").mkdirs()
            File(projectDir, ".git/HEAD").writeText("ref: refs/heads/main\n")
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
            projectDir.resolve("README.md").writeText(
                """
                # Code block

                ```java
                public class Demo {
                    public static void main(String[] args) {
                        System.out.println("hello");
                    }
                }
                ```
                """.trimIndent(),
            )
            database.gitProjectDao().insert(
                GitProjectEntity(
                    id = PROJECT_ID,
                    title = PROJECT_NAME,
                    remoteUrl = "https://github.com/liushenme/md-reader-code-preview-fixture",
                    localPath = projectDir.absolutePath,
                    addTime = Date(),
                ),
            )
        }
    }

    private companion object {
        const val PROJECT_ID = 9101L
        const val PROJECT_NAME = "md-reader-code-preview-fixture"
    }
}
