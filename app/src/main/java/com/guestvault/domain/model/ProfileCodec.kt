package com.guestvault.domain.model

/**
 * Line-oriented codec for saved profiles.
 *
 * One profile per line, six fields separated by `|`:
 *
 *     id|name|pkg1;pkg2|durationMinutes|lockTask|createdAt
 *
 * Deliberately not JSON: `org.json` is an Android stub under JVM unit tests, so
 * a JSON codec could only ever be tested on a device. This one is pure Kotlin,
 * so the round-trip is covered by a fast unit test, and a corrupt line is
 * skipped instead of throwing away every profile.
 *
 * The name is percent-escaped because it is the one free-text field: a `|`, a
 * `;` or a newline typed into a profile name must not shift the fields after it.
 */
object ProfileCodec {

    private const val FIELD = '|'
    private const val PACKAGE = ';'

    fun encode(profiles: List<GuestProfile>): String = profiles.joinToString("\n") { profile ->
        listOf(
            profile.id,
            escape(profile.name),
            profile.packages.sorted().joinToString(PACKAGE.toString()),
            profile.durationMinutes.toString(),
            if (profile.lockTaskEnabled) "1" else "0",
            profile.createdAt.toString()
        ).joinToString(FIELD.toString())
    }

    fun decode(raw: String): List<GuestProfile> =
        raw.lineSequence().mapNotNull(::decodeLine).toList()

    private fun decodeLine(line: String): GuestProfile? {
        if (line.isBlank()) return null
        val fields = line.split(FIELD)
        if (fields.size != 6) return null
        val id = fields[0].trim()
        if (id.isEmpty()) return null
        return GuestProfile(
            id = id,
            name = unescape(fields[1]).ifBlank { "Guest profile" },
            packages = fields[2].split(PACKAGE).filter { it.isNotBlank() }.toSet(),
            durationMinutes = fields[3].toIntOrNull()?.coerceAtLeast(0) ?: 0,
            lockTaskEnabled = fields[4] == "1",
            createdAt = fields[5].toLongOrNull() ?: 0L
        )
    }

    private fun escape(value: String): String = buildString {
        value.forEach { char ->
            when (char) {
                '%' -> append("%25")
                '|' -> append("%7C")
                ';' -> append("%3B")
                '\n' -> append("%0A")
                '\r' -> append("%0D")
                else -> append(char)
            }
        }
    }

    private fun unescape(value: String): String {
        val out = StringBuilder(value.length)
        var index = 0
        while (index < value.length) {
            val char = value[index]
            if (char == '%' && index + 2 < value.length) {
                when (value.substring(index + 1, index + 3).uppercase()) {
                    "25" -> { out.append('%'); index += 3; continue }
                    "7C" -> { out.append('|'); index += 3; continue }
                    "3B" -> { out.append(';'); index += 3; continue }
                    "0A" -> { out.append('\n'); index += 3; continue }
                    "0D" -> { out.append('\r'); index += 3; continue }
                }
            }
            out.append(char)
            index++
        }
        return out.toString()
    }
}
