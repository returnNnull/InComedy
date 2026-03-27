package com.bam.incomedy.feature.notifications.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bam.incomedy.domain.event.EventStatus
import com.bam.incomedy.domain.event.EventVisibility
import com.bam.incomedy.domain.event.OrganizerEvent
import com.bam.incomedy.domain.notifications.EventAnnouncement
import com.bam.incomedy.domain.notifications.EventAnnouncementAuthorRole
import com.bam.incomedy.feature.notifications.NotificationsState

internal data class NotificationsTabBindings(
    val state: NotificationsState = NotificationsState(),
    val organizerEvents: List<OrganizerEvent> = emptyList(),
    val onLoadAnnouncements: (String) -> Unit = {},
    val onCreateAnnouncement: (String, String) -> Unit = { _, _ -> },
    val onClearError: () -> Unit = {},
)

/**
 * Вкладка organizer announcements/event feed внутри авторизованного Android shell.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AnnouncementFeedTab(
    notificationsBindings: NotificationsTabBindings,
    modifier: Modifier = Modifier,
) {
    var selectedEventId by rememberSaveable { mutableStateOf("") }
    var draftMessage by rememberSaveable { mutableStateOf("") }

    val notificationsState = notificationsBindings.state
    val eligibleEvents = remember(notificationsBindings.organizerEvents) {
        notificationsBindings.organizerEvents.filter(OrganizerEvent::isAnnouncementFeedEligible)
    }
    val eligibleEventIds = remember(eligibleEvents) {
        eligibleEvents.map(OrganizerEvent::id)
    }

    LaunchedEffect(eligibleEventIds) {
        selectedEventId = when {
            eligibleEventIds.isEmpty() -> ""
            selectedEventId.isBlank() || selectedEventId !in eligibleEventIds -> eligibleEventIds.first()
            else -> selectedEventId
        }
    }

    LaunchedEffect(selectedEventId) {
        if (selectedEventId.isNotBlank()) {
            notificationsBindings.onLoadAnnouncements(selectedEventId)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(AnnouncementScreenTags.ROOT),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Анонсы и feed",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = "Provider-agnostic organizer announcement surface поверх уже delivered backend/shared foundation без push activation",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        NotificationBanner(
            message = notificationsState.errorMessage,
            onDismiss = notificationsBindings.onClearError,
        )

        if (notificationsState.isLoading || notificationsState.isSubmitting) {
            CircularProgressIndicator(
                modifier = Modifier.testTag(AnnouncementScreenTags.LOADING),
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Анонсов: ${notificationsState.announcements.size}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.testTag(AnnouncementScreenTags.COUNT),
            )
            OutlinedButton(
                onClick = {
                    if (selectedEventId.isNotBlank()) {
                        notificationsBindings.onLoadAnnouncements(selectedEventId)
                    }
                },
                enabled = selectedEventId.isNotBlank() &&
                    !notificationsState.isLoading &&
                    !notificationsState.isSubmitting,
                modifier = Modifier.testTag(AnnouncementScreenTags.REFRESH_BUTTON),
            ) {
                Text("Обновить")
            }
        }

        if (eligibleEvents.isEmpty()) {
            Surface(
                tonalElevation = 1.dp,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(AnnouncementScreenTags.EVENT_EMPTY_STATE),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Нужен опубликованный public event",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "Announcement feed доступен только для published public событий. Подготовьте событие на вкладке событий и вернитесь сюда.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        } else {
            EventFeedSelectorSection(
                events = eligibleEvents,
                selectedEventId = selectedEventId,
                isBusy = notificationsState.isLoading || notificationsState.isSubmitting,
                onSelectEvent = { selectedEventId = it },
            )

            PublishAnnouncementSection(
                selectedEvent = eligibleEvents.firstOrNull { it.id == selectedEventId },
                draftMessage = draftMessage,
                isBusy = notificationsState.isSubmitting,
                onMessageChange = { draftMessage = it },
                onPublish = {
                    notificationsBindings.onCreateAnnouncement(selectedEventId, draftMessage)
                },
            )

            HorizontalDivider()

            AnnouncementListSection(
                announcements = notificationsState.announcements,
                emptyTag = AnnouncementScreenTags.FEED_EMPTY_STATE,
            )
        }
    }
}

@Composable
private fun NotificationBanner(
    message: String?,
    onDismiss: () -> Unit,
) {
    if (message == null) return

    Surface(
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(AnnouncementScreenTags.ERROR_BANNER),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(onClick = onDismiss) {
                Text("Скрыть")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EventFeedSelectorSection(
    events: List<OrganizerEvent>,
    selectedEventId: String,
    isBusy: Boolean,
    onSelectEvent: (String) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = "Событие для feed-а",
            style = MaterialTheme.typography.titleMedium,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            events.forEach { event ->
                OutlinedButton(
                    onClick = { onSelectEvent(event.id) },
                    enabled = !isBusy,
                    modifier = Modifier.testTag("${AnnouncementScreenTags.EVENT_SELECTOR_PREFIX}${event.id}"),
                ) {
                    Text(
                        text = if (selectedEventId == event.id) "• ${event.title}" else event.title,
                    )
                }
            }
        }
    }
}

@Composable
private fun PublishAnnouncementSection(
    selectedEvent: OrganizerEvent?,
    draftMessage: String,
    isBusy: Boolean,
    onMessageChange: (String) -> Unit,
    onPublish: () -> Unit,
) {
    val normalizedMessage = draftMessage.trim()
    Surface(
        tonalElevation = 1.dp,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(AnnouncementScreenTags.PUBLISH_SECTION),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Опубликовать announcement",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = selectedEvent?.let {
                    "Feed будет опубликован в audience-visible историю события «${it.title}». Публикация требует organizer/host access."
                } ?: "Сначала выберите published public event.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                value = draftMessage,
                onValueChange = onMessageChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(AnnouncementScreenTags.PUBLISH_MESSAGE_INPUT),
                enabled = !isBusy,
                minLines = 3,
                maxLines = 5,
                label = { Text("Текст announcement-а") },
                placeholder = { Text("Например: Начинаем через 10 минут") },
            )
            Text(
                text = "${normalizedMessage.length}/1000",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(AnnouncementScreenTags.PUBLISH_COUNTER),
            )
            Button(
                onClick = onPublish,
                enabled = selectedEvent != null &&
                    normalizedMessage.isNotBlank() &&
                    normalizedMessage.length <= 1000 &&
                    !isBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(AnnouncementScreenTags.PUBLISH_BUTTON),
            ) {
                Text("Опубликовать")
            }
        }
    }
}

@Composable
private fun AnnouncementListSection(
    announcements: List<EventAnnouncement>,
    emptyTag: String,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = "История анонсов",
            style = MaterialTheme.typography.titleMedium,
        )
        if (announcements.isEmpty()) {
            Surface(
                tonalElevation = 1.dp,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(emptyTag),
            ) {
                Text(
                    text = "Пока нет анонсов для этого события",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
        } else {
            announcements.forEach { announcement ->
                Surface(
                    tonalElevation = 1.dp,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("${AnnouncementScreenTags.ANNOUNCEMENT_CARD_PREFIX}${announcement.id}"),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = announcement.message,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = "${announcementAuthorTitle(announcement.authorRole)} · ${announcement.createdAtIso}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun announcementAuthorTitle(authorRole: EventAnnouncementAuthorRole): String {
    return when (authorRole) {
        EventAnnouncementAuthorRole.ORGANIZER -> "Организатор"
        EventAnnouncementAuthorRole.HOST -> "Ведущий"
        EventAnnouncementAuthorRole.SYSTEM -> "Система"
    }
}

private fun OrganizerEvent.isAnnouncementFeedEligible(): Boolean {
    return status == EventStatus.PUBLISHED && visibility == EventVisibility.PUBLIC
}

object AnnouncementScreenTags {
    const val ROOT = "notifications.root"
    const val LOADING = "notifications.loading"
    const val ERROR_BANNER = "notifications.error"
    const val COUNT = "notifications.count"
    const val REFRESH_BUTTON = "notifications.refresh"
    const val EVENT_EMPTY_STATE = "notifications.event.empty"
    const val EVENT_SELECTOR_PREFIX = "notifications.event."
    const val PUBLISH_SECTION = "notifications.publish.section"
    const val PUBLISH_MESSAGE_INPUT = "notifications.publish.message"
    const val PUBLISH_COUNTER = "notifications.publish.counter"
    const val PUBLISH_BUTTON = "notifications.publish.submit"
    const val FEED_EMPTY_STATE = "notifications.feed.empty"
    const val ANNOUNCEMENT_CARD_PREFIX = "notifications.card."
}
