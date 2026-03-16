package com.bam.incomedy.feature.event.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bam.incomedy.domain.event.EventStatus
import com.bam.incomedy.domain.event.OrganizerEvent
import com.bam.incomedy.domain.session.OrganizerWorkspace
import com.bam.incomedy.domain.venue.HallTemplate
import com.bam.incomedy.domain.venue.OrganizerVenue
import com.bam.incomedy.feature.event.EventState

/**
 * Набор данных и команд, который main shell передает во вкладку organizer event management.
 *
 * Binding отделяет session shell от самой event-операции и позволяет тестировать вкладку без
 * прямой зависимости от Android `ViewModel`.
 */
internal data class EventTabBindings(
    /** Актуальное состояние organizer event feature. */
    val state: EventState = EventState(),
    /** Команда ручной перезагрузки event context. */
    val onRefreshContext: () -> Unit = {},
    /** Команда создания organizer event draft. */
    val onCreateEvent: (EventCreateForm) -> Unit = {},
    /** Команда публикации существующего draft-события. */
    val onPublishEvent: (String) -> Unit = {},
    /** Команда очистки верхнеуровневой event-ошибки. */
    val onClearError: () -> Unit = {},
)

/**
 * Platform-friendly форма создания organizer event.
 *
 * @property workspaceId Идентификатор выбранного workspace.
 * @property venueId Идентификатор выбранной площадки.
 * @property hallTemplateId Идентификатор выбранного hall template.
 * @property title Название события.
 * @property description Необязательное описание события.
 * @property startsAtIso Время начала в ISO формате.
 * @property doorsOpenAtIso Время открытия дверей в ISO формате.
 * @property endsAtIso Время завершения в ISO формате.
 * @property currency Валюта события.
 * @property visibilityKey Wire-ключ публичности события.
 */
internal data class EventCreateForm(
    val workspaceId: String,
    val venueId: String,
    val hallTemplateId: String,
    val title: String,
    val description: String,
    val startsAtIso: String,
    val doorsOpenAtIso: String,
    val endsAtIso: String,
    val currency: String,
    val visibilityKey: String,
)

/**
 * Вкладка organizer event management внутри авторизованного Android shell.
 *
 * Вкладка покрывает bounded slice `create/list/publish` и работает поверх reference venues из
 * уже существующего venue bounded context-а.
 *
 * @property workspaces Рабочие пространства, доступные текущему организатору.
 * @property eventBindings Event-specific state и callbacks.
 * @property modifier Внешний модификатор контейнера.
 */
@Composable
internal fun EventManagementTab(
    workspaces: List<OrganizerWorkspace>,
    eventBindings: EventTabBindings,
    modifier: Modifier = Modifier,
) {
    /** Хранит выбранный workspace для формы события. */
    var selectedWorkspaceId by rememberSaveable { mutableStateOf("") }
    /** Хранит выбранную площадку для формы события. */
    var selectedVenueId by rememberSaveable { mutableStateOf("") }
    /** Хранит выбранный hall template для формы события. */
    var selectedTemplateId by rememberSaveable { mutableStateOf("") }
    /** Хранит название события. */
    var eventTitle by rememberSaveable { mutableStateOf("") }
    /** Хранит описание события. */
    var eventDescription by rememberSaveable { mutableStateOf("") }
    /** Хранит ISO timestamp начала события. */
    var eventStartsAt by rememberSaveable { mutableStateOf("2026-04-01T19:00:00+03:00") }
    /** Хранит ISO timestamp открытия дверей. */
    var eventDoorsOpenAt by rememberSaveable { mutableStateOf("2026-04-01T18:30:00+03:00") }
    /** Хранит ISO timestamp окончания события. */
    var eventEndsAt by rememberSaveable { mutableStateOf("2026-04-01T21:00:00+03:00") }
    /** Хранит валюту события. */
    var eventCurrency by rememberSaveable { mutableStateOf("RUB") }
    /** Хранит visibility события. */
    var eventVisibilityKey by rememberSaveable { mutableStateOf("public") }

    val eventState = eventBindings.state
    val filteredVenues = eventState.venues.filter { venue -> venue.workspaceId == selectedWorkspaceId }
    val selectedVenue = filteredVenues.firstOrNull { venue -> venue.id == selectedVenueId } ?: filteredVenues.firstOrNull()
    val selectedTemplate = selectedVenue
        ?.hallTemplates
        ?.firstOrNull { template -> template.id == selectedTemplateId }
        ?: selectedVenue?.hallTemplates?.firstOrNull()

    LaunchedEffect(workspaces) {
        if (selectedWorkspaceId.isBlank() || workspaces.none { workspace -> workspace.id == selectedWorkspaceId }) {
            selectedWorkspaceId = workspaces.firstOrNull()?.id.orEmpty()
        }
    }
    LaunchedEffect(selectedWorkspaceId, eventState.venues) {
        if (selectedVenueId.isBlank() || filteredVenues.none { venue -> venue.id == selectedVenueId }) {
            selectedVenueId = filteredVenues.firstOrNull()?.id.orEmpty()
        }
    }
    LaunchedEffect(selectedVenue?.id, selectedVenue?.hallTemplates) {
        if (selectedTemplateId.isBlank() || selectedVenue?.hallTemplates?.none { template -> template.id == selectedTemplateId } != false) {
            selectedTemplateId = selectedVenue?.hallTemplates?.firstOrNull()?.id.orEmpty()
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(EventScreenTags.ROOT),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "События и snapshot схемы",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = "Organizer slice для create/list/publish и frozen EventHallSnapshot",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
        )

        eventState.errorMessage?.let { errorMessage ->
            EventErrorBanner(
                errorMessage = errorMessage,
                onClearError = eventBindings.onClearError,
            )
        }

        if (eventState.isLoading || eventState.isSubmitting) {
            CircularProgressIndicator(
                modifier = Modifier.testTag(EventScreenTags.LOADING),
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Событий: ${eventState.events.size}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag(EventScreenTags.COUNT),
            )
            OutlinedButton(
                onClick = eventBindings.onRefreshContext,
                enabled = !eventState.isLoading && !eventState.isSubmitting,
                modifier = Modifier.testTag(EventScreenTags.REFRESH_BUTTON),
            ) {
                Text("Обновить")
            }
        }

        HorizontalDivider()

        Text(
            text = "Создать событие",
            style = MaterialTheme.typography.titleMedium,
        )
        if (workspaces.isEmpty()) {
            Text(
                text = "Сначала создайте organizer workspace, затем площадку и шаблон зала.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.testTag(EventScreenTags.WORKSPACE_EMPTY),
            )
        } else {
            EventSelectorRow(
                items = workspaces.map { workspace -> workspace.id to workspace.name },
                selectedKey = selectedWorkspaceId,
                onSelect = { selectedWorkspaceId = it },
                tagPrefix = EventScreenTags.WORKSPACE_SELECTOR_PREFIX,
            )
        }

        if (filteredVenues.isEmpty()) {
            Text(
                text = "В выбранном workspace пока нет площадок. Сначала создайте venue и hall template.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.testTag(EventScreenTags.VENUE_EMPTY),
            )
        } else {
            EventSelectorRow(
                items = filteredVenues.map { venue -> venue.id to venue.name },
                selectedKey = selectedVenue?.id.orEmpty(),
                onSelect = { selectedVenueId = it },
                tagPrefix = EventScreenTags.VENUE_SELECTOR_PREFIX,
            )
        }

        if (selectedVenue != null && selectedVenue.hallTemplates.isEmpty()) {
            Text(
                text = "Для выбранной площадки еще нет шаблонов зала.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.testTag(EventScreenTags.TEMPLATE_EMPTY),
            )
        } else if (selectedVenue != null) {
            EventSelectorRow(
                items = selectedVenue.hallTemplates.map { template ->
                    template.id to "${template.name} · v${template.version}"
                },
                selectedKey = selectedTemplate?.id.orEmpty(),
                onSelect = { selectedTemplateId = it },
                tagPrefix = EventScreenTags.TEMPLATE_SELECTOR_PREFIX,
            )
        }

        OutlinedTextField(
            value = eventTitle,
            onValueChange = { eventTitle = it },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(EventScreenTags.TITLE_INPUT),
            label = { Text("Название события") },
            singleLine = true,
        )
        OutlinedTextField(
            value = eventDescription,
            onValueChange = { eventDescription = it },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(EventScreenTags.DESCRIPTION_INPUT),
            label = { Text("Описание") },
        )
        OutlinedTextField(
            value = eventStartsAt,
            onValueChange = { eventStartsAt = it },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(EventScreenTags.STARTS_AT_INPUT),
            label = { Text("Начало (ISO)") },
            singleLine = true,
        )
        OutlinedTextField(
            value = eventDoorsOpenAt,
            onValueChange = { eventDoorsOpenAt = it },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(EventScreenTags.DOORS_OPEN_AT_INPUT),
            label = { Text("Открытие дверей (ISO)") },
            singleLine = true,
        )
        OutlinedTextField(
            value = eventEndsAt,
            onValueChange = { eventEndsAt = it },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(EventScreenTags.ENDS_AT_INPUT),
            label = { Text("Окончание (ISO)") },
            singleLine = true,
        )
        OutlinedTextField(
            value = eventCurrency,
            onValueChange = { eventCurrency = it },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(EventScreenTags.CURRENCY_INPUT),
            label = { Text("Валюта") },
            singleLine = true,
        )
        EventSelectorRow(
            items = listOf(
                "public" to "Публичное",
                "private" to "Приватное",
            ),
            selectedKey = eventVisibilityKey,
            onSelect = { eventVisibilityKey = it },
            tagPrefix = EventScreenTags.VISIBILITY_SELECTOR_PREFIX,
        )
        Button(
            onClick = {
                eventBindings.onCreateEvent(
                    EventCreateForm(
                        workspaceId = selectedWorkspaceId,
                        venueId = selectedVenue?.id.orEmpty(),
                        hallTemplateId = selectedTemplate?.id.orEmpty(),
                        title = eventTitle,
                        description = eventDescription,
                        startsAtIso = eventStartsAt,
                        doorsOpenAtIso = eventDoorsOpenAt,
                        endsAtIso = eventEndsAt,
                        currency = eventCurrency,
                        visibilityKey = eventVisibilityKey,
                    ),
                )
            },
            enabled = selectedWorkspaceId.isNotBlank() &&
                selectedVenue != null &&
                selectedTemplate != null &&
                eventTitle.trim().length >= 3 &&
                !eventState.isSubmitting,
            modifier = Modifier.testTag(EventScreenTags.CREATE_BUTTON),
        ) {
            Text("Сохранить событие")
        }

        HorizontalDivider()

        Text(
            text = "Список событий",
            style = MaterialTheme.typography.titleMedium,
        )
        if (eventState.events.isEmpty()) {
            Text(
                text = "Пока нет событий. Создайте первый draft и затем опубликуйте его.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.testTag(EventScreenTags.EVENTS_EMPTY),
            )
        } else {
            eventState.events.forEach { event ->
                OrganizerEventCard(
                    event = event,
                    isSubmitting = eventState.isSubmitting,
                    onPublishEvent = eventBindings.onPublishEvent,
                )
            }
        }
    }
}

/**
 * Баннер ошибки organizer event management.
 *
 * @property errorMessage Текущая ошибка event feature.
 * @property onClearError Колбэк очистки ошибки.
 */
@Composable
private fun EventErrorBanner(
    errorMessage: String,
    onClearError: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(EventScreenTags.ERROR_BANNER),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(onClick = onClearError) {
                Text("Скрыть")
            }
        }
    }
}

/**
 * Карточка organizer event в списке событий.
 *
 * @property event Доменная модель события.
 * @property isSubmitting Показывает активную event mutation.
 * @property onPublishEvent Команда публикации draft-события.
 */
@Composable
private fun OrganizerEventCard(
    event: OrganizerEvent,
    isSubmitting: Boolean,
    onPublishEvent: (String) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = event.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "${event.venueName} · ${event.sourceTemplateName}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "Старт: ${event.startsAtIso}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
            Text(
                text = "Статус: ${eventStatusTitle(event.status.wireName)} · Продажи: ${salesStatusTitle(event.salesStatus.wireName)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
            Text(
                text = "Snapshot: rows=${event.hallSnapshot.layout.rows.size}, zones=${event.hallSnapshot.layout.zones.size}, tables=${event.hallSnapshot.layout.tables.size}",
                style = MaterialTheme.typography.bodySmall,
            )
            if (event.status == EventStatus.DRAFT) {
                Button(
                    onClick = { onPublishEvent(event.id) },
                    enabled = !isSubmitting,
                    modifier = Modifier.testTag("${EventScreenTags.PUBLISH_BUTTON_PREFIX}${event.id}"),
                ) {
                    Text("Опубликовать")
                }
            }
        }
    }
}

/**
 * Компактный selector row для workspace/venue/template и visibility.
 *
 * @property items Пары id/title для выбора.
 * @property selectedKey Текущий выбранный ключ.
 * @property onSelect Колбэк выбора.
 * @property tagPrefix Префикс testTag для каждой кнопки.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EventSelectorRow(
    items: List<Pair<String, String>>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    tagPrefix: String,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { (key, title) ->
            val isSelected = selectedKey == key
            OutlinedButton(
                onClick = { onSelect(key) },
                border = BorderStroke(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                ),
                modifier = Modifier.testTag("$tagPrefix$key"),
            ) {
                Text(title)
            }
        }
    }
}

/** Преобразует event status wire-name в человекочитаемый заголовок. */
private fun eventStatusTitle(statusKey: String): String {
    return when (statusKey) {
        "draft" -> "Черновик"
        "published" -> "Опубликовано"
        "canceled" -> "Отменено"
        else -> statusKey
    }
}

/** Преобразует sales status wire-name в человекочитаемый заголовок. */
private fun salesStatusTitle(statusKey: String): String {
    return when (statusKey) {
        "closed" -> "Закрыты"
        "open" -> "Открыты"
        "paused" -> "На паузе"
        "sold_out" -> "Sold out"
        else -> statusKey
    }
}

/**
 * Теги, по которым UI-тесты находят ключевые элементы organizer event screen.
 */
internal object EventScreenTags {
    /** Тег корневого контейнера organizer event tab. */
    const val ROOT = "event.root"

    /** Тег счетчика событий. */
    const val COUNT = "event.count"

    /** Тег индикатора загрузки event context. */
    const val LOADING = "event.loading"

    /** Тег кнопки ручного refresh event context. */
    const val REFRESH_BUTTON = "event.refresh"

    /** Тег баннера event-ошибки. */
    const val ERROR_BANNER = "event.error"

    /** Тег пустого состояния workspaces. */
    const val WORKSPACE_EMPTY = "event.workspace.empty"

    /** Тег пустого состояния venues. */
    const val VENUE_EMPTY = "event.venue.empty"

    /** Тег пустого состояния templates. */
    const val TEMPLATE_EMPTY = "event.template.empty"

    /** Тег поля названия события. */
    const val TITLE_INPUT = "event.form.title"

    /** Тег поля описания события. */
    const val DESCRIPTION_INPUT = "event.form.description"

    /** Тег поля времени старта. */
    const val STARTS_AT_INPUT = "event.form.startsAt"

    /** Тег поля времени открытия дверей. */
    const val DOORS_OPEN_AT_INPUT = "event.form.doorsOpenAt"

    /** Тег поля времени окончания. */
    const val ENDS_AT_INPUT = "event.form.endsAt"

    /** Тег поля валюты. */
    const val CURRENCY_INPUT = "event.form.currency"

    /** Тег кнопки создания события. */
    const val CREATE_BUTTON = "event.form.create"

    /** Тег пустого состояния списка событий. */
    const val EVENTS_EMPTY = "event.list.empty"

    /** Префикс тегов selector-а workspace. */
    const val WORKSPACE_SELECTOR_PREFIX = "event.workspace."

    /** Префикс тегов selector-а venue. */
    const val VENUE_SELECTOR_PREFIX = "event.venue."

    /** Префикс тегов selector-а template. */
    const val TEMPLATE_SELECTOR_PREFIX = "event.template."

    /** Префикс тегов selector-а visibility. */
    const val VISIBILITY_SELECTOR_PREFIX = "event.visibility."

    /** Префикс тегов publish-кнопок event list-а. */
    const val PUBLISH_BUTTON_PREFIX = "event.publish."
}
