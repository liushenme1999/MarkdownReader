package space.liushenme.markdownreader.document

import java.io.File
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject
import space.liushenme.markdownreader.importing.ParsedBookStorage
import space.liushenme.markdownreader.importing.PdfReaderContent

data class DocumentArtifactManifest(
    val schemaVersion: Int,
    val bodyChecksum: String,
    val bodyBytes: Long,
    val artifacts: List<DocumentArtifact>,
) {
    enum class Validation { Valid, MissingManifest, MissingBody, BodyMismatch, MissingArtifact, ArtifactMismatch }

    fun validate(bundleDir: File): Validation {
        val body = File(bundleDir, ParsedBookStorage.BODY_FILE)
        if (!body.isFile) return Validation.MissingBody
        if (body.length() != bodyBytes || sha256(body) != bodyChecksum) return Validation.BodyMismatch
        artifacts.forEach { artifact ->
            val file = File(bundleDir, artifact.relativePath)
            if (!file.isFile) return Validation.MissingArtifact
            if (file.length() != artifact.byteSize) return Validation.ArtifactMismatch
            val verifyChecksum = artifact.kind !in setOf(
                DocumentArtifact.Kind.Image,
                DocumentArtifact.Kind.PdfPage,
                DocumentArtifact.Kind.Body,
            )
            if (verifyChecksum && artifact.checksum.isNotEmpty() && sha256(file) != artifact.checksum) {
                return Validation.ArtifactMismatch
            }
        }
        return Validation.Valid
    }

    fun toJson(): String = JSONObject().apply {
        put("schemaVersion", schemaVersion)
        put("bodyChecksum", bodyChecksum)
        put("bodyBytes", bodyBytes)
        put("artifacts", JSONArray().apply {
            artifacts.forEach { artifact ->
                put(JSONObject().apply {
                    put("relativePath", artifact.relativePath)
                    put("kind", artifact.kind.name)
                    put("byteSize", artifact.byteSize)
                    put("checksum", artifact.checksum)
                })
            }
        })
    }.toString()

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        const val FILE_NAME = "artifact_manifest.json"
        const val BLOCK_INDEX_FILE = "block_index.json"

        fun create(bundleDir: File): DocumentArtifactManifest? {
            val body = File(bundleDir, ParsedBookStorage.BODY_FILE)
            if (!body.isFile || body.length() <= 0L) return null
            val rawBody = runCatching { body.readText() }.getOrNull() ?: return null
            val artifacts = buildList {
                add(artifact(bundleDir, body, DocumentArtifact.Kind.Body))
                addIfFile(bundleDir, ParsedBookStorage.TOC_FILE, DocumentArtifact.Kind.Toc)
                addIfFile(bundleDir, ParsedBookStorage.COVER_JPG, DocumentArtifact.Kind.Cover)
                addIfFile(bundleDir, ParsedBookStorage.COVER_PNG, DocumentArtifact.Kind.Cover)
                addIfFile(bundleDir, ParsedBookStorage.DIAGRAM_PAYLOADS_FILE, DocumentArtifact.Kind.DiagramPayload)
                addIfFile(bundleDir, BLOCK_INDEX_FILE, DocumentArtifact.Kind.BlockIndex)
                val pdfNames = PdfReaderContent.referencedPageAssetNames(rawBody).toSet()
                File(bundleDir, ParsedBookStorage.ASSETS_DIR).listFiles()
                    ?.filter { it.isFile }
                    ?.sortedBy { it.name }
                    ?.forEach { file ->
                        val kind = if (file.name in pdfNames) {
                            DocumentArtifact.Kind.PdfPage
                        } else {
                            DocumentArtifact.Kind.Image
                        }
                        add(artifact(bundleDir, file, kind))
                    }
            }
            return DocumentArtifactManifest(
                schemaVersion = CURRENT_SCHEMA_VERSION,
                bodyChecksum = sha256(body),
                bodyBytes = body.length(),
                artifacts = artifacts,
            )
        }

        fun read(bundleDir: File): DocumentArtifactManifest? {
            val file = File(bundleDir, FILE_NAME)
            if (!file.isFile) return null
            return runCatching {
                val root = JSONObject(file.readText())
                val array = root.optJSONArray("artifacts") ?: JSONArray()
                val artifacts = buildList {
                    for (index in 0 until array.length()) {
                        val item = array.optJSONObject(index) ?: continue
                        val path = item.optString("relativePath")
                        val kind = runCatching {
                            DocumentArtifact.Kind.valueOf(item.optString("kind"))
                        }.getOrNull() ?: continue
                        if (path.isBlank() || path.startsWith('/') || ".." in path) continue
                        add(
                            DocumentArtifact(
                                relativePath = path,
                                kind = kind,
                                byteSize = item.optLong("byteSize", -1L).coerceAtLeast(0L),
                                checksum = item.optString("checksum"),
                            ),
                        )
                    }
                }
                DocumentArtifactManifest(
                    schemaVersion = root.optInt("schemaVersion", 0),
                    bodyChecksum = root.optString("bodyChecksum"),
                    bodyBytes = root.optLong("bodyBytes", -1L),
                    artifacts = artifacts,
                )
            }.getOrNull()
        }

        fun write(bundleDir: File): DocumentArtifactManifest? {
            val manifest = create(bundleDir) ?: return null
            val target = File(bundleDir, FILE_NAME)
            val temporary = File(bundleDir, "$FILE_NAME.tmp")
            temporary.writeText(manifest.toJson())
            if (target.exists()) target.delete()
            if (!temporary.renameTo(target)) {
                temporary.copyTo(target, overwrite = true)
                temporary.delete()
            }
            return manifest
        }

        fun validation(bundleDir: File): Validation =
            read(bundleDir)?.validate(bundleDir) ?: Validation.MissingManifest

        private fun MutableList<DocumentArtifact>.addIfFile(
            root: File,
            relativePath: String,
            kind: DocumentArtifact.Kind,
        ) {
            val file = File(root, relativePath)
            if (file.isFile) add(artifact(root, file, kind))
        }

        private fun artifact(root: File, file: File, kind: DocumentArtifact.Kind): DocumentArtifact =
            DocumentArtifact(
                relativePath = file.relativeTo(root).invariantSeparatorsPath,
                kind = kind,
                byteSize = file.length(),
                checksum = if (kind == DocumentArtifact.Kind.Image || kind == DocumentArtifact.Kind.PdfPage) {
                    ""
                } else {
                    sha256(file)
                },
            )

        private fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { input ->
                val buffer = ByteArray(32 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count <= 0) break
                    digest.update(buffer, 0, count)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
