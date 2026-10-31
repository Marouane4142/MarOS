package io.github.marouane4142.maros.tv

import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId
import java.time.Instant

class HomePolicyTest {
    @Test fun layoutRejectsUnknownAndPreservesEverySection() {
        assertEquals(listOf("recent", "favorites", "continue", "library", "shortcuts"), HomeLayout.normalize(listOf("recent", "unknown", "recent")))
        assertEquals(HomeLayout.rows, HomeLayout.move(HomeLayout.rows, "favorites", -1))
        assertEquals(listOf("continue", "favorites", "recent", "library", "shortcuts"), HomeLayout.move(HomeLayout.rows, "favorites", 1))
    }
    @Test fun calendarReadsUtcTimezoneAndFoldedEscapes() {
        val feed = "BEGIN:VCALENDAR\r\nBEGIN:VEVENT\r\nUID:one\r\nSUMMARY:Une soirée\\, au\r\n  cinéma\r\nDTSTART:20261030T190000Z\r\nEND:VEVENT\r\nBEGIN:VEVENT\r\nUID:two\r\nSUMMARY:Paris\r\nDTSTART;TZID=Europe/Paris:20261030T200000\r\nEND:VEVENT\r\nEND:VCALENDAR"
        val events = CalendarFeed.parse(feed, ZoneId.of("UTC"))
        assertEquals(2, events.size)
        assertEquals("Une soirée, au cinéma", events[0].title)
        assertEquals(Instant.parse("2026-10-30T19:00:00Z").toEpochMilli(), events[0].time)
        assertEquals(events[0].time, events[1].time)
    }
    @Test fun recurringCancelledAndMalformedEventsDoNotBecomeFalseAppointments() {
        val feed = "BEGIN:VCALENDAR\nBEGIN:VEVENT\nSUMMARY:Recurring\nRRULE:FREQ=DAILY\nDTSTART:20261030T190000Z\nEND:VEVENT\nBEGIN:VEVENT\nSUMMARY:Bad date\nDTSTART:invalid\nEND:VEVENT\nEND:VCALENDAR"
        assertTrue(CalendarFeed.parse(feed).isEmpty())
    }
}
