package com.bam.incomedy.data.lineup.backend

import com.bam.incomedy.core.backend.backendJson
import com.bam.incomedy.domain.lineup.LineupEntryStatus
import com.bam.incomedy.domain.lineup.LineupLiveUpdateType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Unit-тесты public live-event subscription contract-а в data-слое. */
@OptIn(ExperimentalCoroutinesApi::class)
class LineupBackendApiLiveUpdatesTest {
    /** Проверяет, что backend payload маппится в доменную realtime-модель. */
    @Test
    fun observeEventLiveUpdatesMapsAudienceSafeEnvelope() = runTest {
        val api = LineupBackendApi.createForTesting(
            baseUrl = "https://incomedy.ru",
            parser = backendJson,
            liveUpdatesTransport = RecordingTransport(
                payloads = listOf(
                    """
                    {
                      "type": "stage.current_changed",
                      "event_id": "event-1",
                      "occurred_at": "2026-03-25T16:55:00+03:00",
                      "reason": "live_state_changed",
                      "summary": {
                        "current_performer": {
                          "id": "entry-2",
                          "comedian_display_name": "Мария Сетова",
                          "order_index": 2,
                          "status": "on_stage"
                        },
                        "next_up": {
                          "id": "entry-3",
                          "comedian_display_name": "Иван Смехов",
                          "order_index": 3,
                          "status": "up_next"
                        },
                        "lineup": [
                          {
                            "id": "entry-2",
                            "comedian_display_name": "Мария Сетова",
                            "order_index": 2,
                            "status": "on_stage"
                          },
                          {
                            "id": "entry-3",
                            "comedian_display_name": "Иван Смехов",
                            "order_index": 3,
                            "status": "up_next"
                          }
                        ]
                      }
                    }
                    """.trimIndent(),
                ),
            ),
        )

        val update = api.observeEventLiveUpdates("event-1").single()

        assertEquals(LineupLiveUpdateType.STAGE_CURRENT_CHANGED, update.type)
        assertEquals("event-1", update.eventId)
        assertEquals("live_state_changed", update.reason)
        assertEquals("entry-2", update.summary.currentPerformer?.id)
        assertEquals(LineupEntryStatus.ON_STAGE, update.summary.currentPerformer?.status)
        assertEquals("entry-3", update.summary.nextUp?.id)
        assertEquals(LineupEntryStatus.UP_NEXT, update.summary.nextUp?.status)
        assertEquals(listOf("entry-2", "entry-3"), update.summary.lineup.map { it.id })
    }

    /** Проверяет, что realtime transport получает `wss` URL с ожидаемым route path. */
    @Test
    fun observeEventLiveUpdatesBuildsSecureWebSocketUrl() = runTest {
        val transport = RecordingTransport(
            payloads = listOf(
                """
                {
                  "type": "lineup.changed",
                  "event_id": "event-42",
                  "occurred_at": "2026-03-25T17:00:00+03:00",
                  "reason": "initial_snapshot",
                  "summary": {
                    "lineup": []
                  }
                }
                """.trimIndent(),
            ),
        )
        val api = LineupBackendApi.createForTesting(
            baseUrl = "https://incomedy.ru/mobile",
            parser = backendJson,
            liveUpdatesTransport = transport,
        )

        api.observeEventLiveUpdates("event-42").single()

        assertEquals("wss://incomedy.ru/mobile/ws/events/event-42", transport.observedUrls.single())
    }

    /** Проверяет, что неизвестный live-event тип пропускается без падения существующего lineup consumer-а. */
    @Test
    fun observeEventLiveUpdatesIgnoresUnknownEventTypes() = runTest {
        val api = LineupBackendApi.createForTesting(
            baseUrl = "https://incomedy.ru",
            parser = backendJson,
            liveUpdatesTransport = RecordingTransport(
                payloads = listOf(
                    """
                    {
                      "type": "announcement.created",
                      "event_id": "event-1",
                      "occurred_at": "2026-03-26T18:20:00+03:00",
                      "reason": "organizer_announcement_created",
                      "announcement": {
                        "id": "announcement-1",
                        "message": "Начинаем через 10 минут",
                        "author_role": "organizer",
                        "created_at": "2026-03-26T18:20:00+03:00"
                      }
                    }
                    """.trimIndent(),
                    """
                    {
                      "type": "lineup.changed",
                      "event_id": "event-1",
                      "occurred_at": "2026-03-26T18:21:00+03:00",
                      "reason": "initial_snapshot",
                      "summary": {
                        "lineup": []
                      }
                    }
                    """.trimIndent(),
                ),
            ),
        )

        val update = api.observeEventLiveUpdates("event-1").single()

        assertEquals(LineupLiveUpdateType.LINEUP_CHANGED, update.type)
        assertEquals("initial_snapshot", update.reason)
    }
}

/** Простой transport double, который возвращает заранее заданные payload-ы и запоминает URL. */
private class RecordingTransport(
    private val payloads: List<String>,
) : LineupLiveUpdatesTransport {
    val observedUrls = mutableListOf<String>()

    override fun observe(url: String) = flowOf(*payloads.toTypedArray()).also {
        observedUrls += url
    }
}
