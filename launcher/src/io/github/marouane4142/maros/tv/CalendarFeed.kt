package io.github.marouane4142.maros.tv

import java.time.*
import java.time.format.DateTimeFormatter

data class CalendarEntry(val id: String, val title: String, val time: Long)

/** Bounded RFC 5545 reader: folded lines, UTC/local/TZID dates; recurring rules are explicit exclusions. */
object CalendarFeed {
    fun parse(text: String, zone: ZoneId = ZoneId.systemDefault()): List<CalendarEntry> {
        require(text.length <= 2_097_152 && "BEGIN:VCALENDAR" in text) { "Calendrier ICS invalide." }
        val unfolded = text.replace("\r\n", "\n").replace(Regex("\n[ \t]"), "")
        val result = mutableListOf<CalendarEntry>()
        var fields: MutableMap<String, Pair<String, String>>? = null
        for (line in unfolded.lineSequence()) {
            if (line == "BEGIN:VEVENT") { fields = linkedMapOf(); continue }
            if (line == "END:VEVENT") {
                fields?.let { values ->
                    if ("RRULE" !in values && values["STATUS"]?.second != "CANCELLED") {
                        val start = values["DTSTART"]
                        val title = values["SUMMARY"]?.second
                        if (start != null && !title.isNullOrBlank()) runCatching {
                            val raw = start.second
                            val tz = Regex("TZID=([^;:]+)").find(start.first)?.groupValues?.get(1)?.trim('"')?.let(ZoneId::of) ?: zone
                            val instant = when {
                                raw.matches(Regex("\\d{8}")) -> LocalDate.parse(raw, DateTimeFormatter.BASIC_ISO_DATE).atStartOfDay(tz).toInstant()
                                raw.endsWith("Z") -> LocalDateTime.parse(raw.dropLast(1), DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")).toInstant(ZoneOffset.UTC)
                                else -> LocalDateTime.parse(raw, DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")).atZone(tz).toInstant()
                            }
                            val id = values["UID"]?.second ?: "${instant.toEpochMilli()}:$title"
                            result += CalendarEntry(id.take(300), unescape(title).take(120), instant.toEpochMilli())
                        }
                    }
                }
                fields = null
                if (result.size >= 100) break
                continue
            }
            val at = line.indexOf(':')
            if (at > 0) fields?.put(line.substring(0, at).substringBefore(';'), line.substring(0, at) to line.substring(at + 1))
        }
        return result.distinctBy { it.id }.sortedBy { it.time }
    }
    private fun unescape(s: String) = s.replace("\\n", " ").replace("\\N", " ").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\")
}
