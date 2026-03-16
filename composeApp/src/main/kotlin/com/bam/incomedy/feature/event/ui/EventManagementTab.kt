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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bam.incomedy.domain.event.EventSalesStatus
import com.bam.incomedy.domain.event.EventStatus
import com.bam.incomedy.domain.event.OrganizerEvent
import com.bam.incomedy.domain.session.OrganizerWorkspace
import com.bam.incomedy.feature.event.EventOverrideEditorCodec
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
    /** Команда обновления organizer event details и override-ов. */
    val onUpdateEvent: (EventUpdateForm) -> Unit = {},
    /** Команда публикации существующего draft-события. */
    val onPublishEvent: (String) -> Unit = {},
    /** Команда открытия продаж опубликованного события. */
    val onOpenEventSales: (String) -> Unit = {},
    /** Команда паузы активных продаж события. */
    val onPauseEventSales: (String) -> Unit = {},
    /** Команда отмены события. */
    val onCancelEvent: (String) -> Unit = {},
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
 * Platform-friendly форма обновления organizer event details и event-local override-ов.
 *
 * @property eventId Идентификатор редактируемого события.
 * @property title Название события.
 * @property description Необязательное описание.
 * @property startsAtIso Время начала в ISO формате.
 * @property doorsOpenAtIso Время открытия дверей в ISO формате.
 * @property endsAtIso Время завершения в ISO формате.
 * @property currency Валюта события.
 * @property visibilityKey Wire-ключ публичности события.
 * @property priceZonesText Текст event-local price zones.
 * @property pricingAssignmentsText Текст назначений ценовых зон на snapshot targets.
 * @property availabilityOverridesText Текст availability override-ов.
 */
internal data class EventUpdateForm(
    val eventId: String,
    val title: String,
    val description: String,
    val startsAtIso: String,
    val doorsOpenAtIso: String,
    val endsAtIso: String,
    val currency: String,
    val visibilityKey: String,
    val priceZonesText: String,
    val pricingAssignmentsText: String,
    val availabilityOverridesText: String,
)

/**
 * Вкладка organizer event management внутри авторизованного Android shell.
 *
 * Вкладка покрывает bounded slices `create/list/publish/sales controls` и `event-local
 * overrides` поверх frozen `EventHallSnapshot`, не заходя в ticketing inventory semantics.
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
    /** Хранит название события для create form. */
    var eventTitle by rememberSaveable { mutableStateOf("") }
    /** Хранит описание события для create form. */
    var eventDescription by rememberSaveable { mutableStateOf("") }
    /** Хранит ISO timestamp начала события для create form. */
    var eventStartsAt by rememberSaveable { mutableStateOf("2026-04-01T19:00:00+03:00") }
    /** Хранит ISO timestamp открытия дверей для create form. */
    var eventDoorsOpenAt by rememberSaveable { mutableStateOf("2026-04-01T18:30:00+03:00") }
    /** Хранит ISO timestamp окончания события для create form. */
    var eventEndsAt by rememberSaveable { mutableStateOf("2026-04-01T21:00:00+03:00") }
    /** Хранит валюту события для create form. */
    var eventCurrency by rememberSaveable { mutableStateOf("RUB") }
    /** Хранит visibility события для create form. */
    var eventVisibilityKey by rememberSaveable { mutableStateOf("public") }

    /** Хранит выбранное событие для editor surface. */
    var selectedEditableEventId by rememberSaveable { mutableStateOf("") }
    /** Хранит редактируемое название события. */
    var editorTitle by rememberSaveable { mutableStateOf("") }
    /** Хранит редактируемое описание события. */
    var editorDescription by rememberSaveable { mutableStateOf("") }
    /** Хранит редактируемое время начала события. */
    var editorStartsAt by rememberSaveable { mutableStateOf("") }
    /** Хранит редактируемое время открытия дверей. */
    var editorDoorsOpenAt by rememberSaveable { mutableStateOf("") }
    /** Хранит редактируемое время окончания события. */
    var editorEndsAt by rememberSaveable { mutableStateOf("") }
    /** Хранит редактируемую валюту. */
    var editorCurrency by rememberSaveable { mutableStateOf("RUB") }
    /** Хранит редактируемую visibility. */
    var editorVisibilityKey by rememberSaveable { mutableStateOf("public") }
    /** Хранит текст event-local price zones. */
    var editorPriceZonesText by rememberSaveable { mutableStateOf("") }
    /** Хранит текст pricing assignments. */
    var editorPricingAssignmentsText by rememberSaveable { mutableStateOf("") }
    /** Хранит текст availability overrides. */
    var editorAvailabilityOverridesText by rememberSaveable { mutableStateOf("") }

    val eventState = eventBindings.state
    val filteredVenues = eventState.venues.filter { venue -> venue.workspaceId == selectedWorkspaceId }
    val selectedVenue = filteredVenues.firstOrNull { venue -> venue.id == selectedVenueId } ?: filteredVenues.firstOrNull()
    val selectedTemplate = selectedVenue
        ?.hallTemplates
        ?.firstOrNull { template -> template.id == selectedTemplateId }
        ?: selectedVenue?.hallTemplates?.firstOrNull()
    val selectedEditableEvent = eventState.events.firstOrNull { event -> event.id == selectedEditableEventId }
        ?: eventState.events.firstOrNull()

    val loadEventIntoEditor = remember {
        { event: OrganizerEvent ->
            val input = EventOverrideEditorCodec.fromEvent(event)
            selectedEditableEventId = event.id
            editorTitle = event.title
            editorDescription = event.description.orEmpty()
            editorStartsAt = event.startsAtIso
            editorDoorsOpenAt = event.doorsOpenAtIso.orEmpty()
            editorEndsAt = event.endsAtIso.orEmpty()
            editorCurrency = event.currency
            editorVisibilityKey = event.visibility.wireName
            editorPriceZonesText = input.priceZonesText
            editorPricingAssignmentsText = input.pricingAssignmentsText
            editorAvailabilityOverridesText = input.availabilityOverridesText
        }
    }
    val resetEditor = remember {
        {
            selectedEditableEventId = ""
            editorTitle = ""
            editorDescription = ""
            editorStartsAt = ""
            editorDoorsOpenAt = ""
            editorEndsAt = ""
            editorCurrency = "RUB"
            editorVisibilityKey = "public"
            editorPriceZonesText = ""
            editorPricingAssignmentsText = ""
            editorAvailabilityOverridesText = ""
        }
    }

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
    LaunchedEffect(eventState.events) {
        when {
            eventState.events.isEmpty() -> resetEditor()
            selectedEditableEventId.isBlank() -> loadEventIntoEditor(eventState.events.first())
            eventState.events.none { event -> event.id == selectedEditableEventId } -> loadEventIntoEditor(eventState.events.first())
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
            text = "Organizer slice для create/list/publish, sales controls и event-local pricing/availability overrides",
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
                    onEditEvent = loadEventIntoEditor,
                    onPublishEvent = eventBindings.onPublishEvent,
                    onOpenEventSales = eventBindings.onOpenEventSales,
                    onPauseEventSales = eventBindings.onPauseEventSales,
                    onCancelEvent = eventBindings.onCancelEvent,
                )
            }
        }

        HorizontalDivider()

        Text(
            text = "Редактор override-ов события",
            style = MaterialTheme.typography.titleMedium,
        )
        if (selectedEditableEvent == null) {
            Text(
                text = "Выберите или создайте событие, чтобы настроить event-local price zones и availability.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.testTag(EventScreenTags.EDITOR_EMPTY),
            )
        } else {
            Text(
                text = "Площадка: ${selectedEditableEvent.venueName} · snapshot: ${selectedEditableEvent.sourceTemplateName}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Доступные target refs: ${EventOverrideEditorCodec.targetHint(selectedEditableEvent)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
            Text(
                text = "Текущий override state: ${EventOverrideEditorCodec.summary(selectedEditableEvent)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
            if (!selectedEditableEvent.isEditable()) {
                Text(
                    text = "Отмененное событие доступно только для чтения. Для правок нужен новый draft.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            OutlinedTextField(
                value = editorTitle,
                onValueChange = { editorTitle = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(EventScreenTags.UPDATE_TITLE_INPUT),
                label = { Text("Название события") },
                singleLine = true,
            )
            OutlinedTextField(
                value = editorDescription,
                onValueChange = { editorDescription = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(EventScreenTags.UPDATE_DESCRIPTION_INPUT),
                label = { Text("Описание") },
            )
            OutlinedTextField(
                value = editorStartsAt,
                onValueChange = { editorStartsAt = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(EventScreenTags.UPDATE_STARTS_AT_INPUT),
                label = { Text("Начало (ISO)") },
                singleLine = true,
            )
            OutlinedTextField(
                value = editorDoorsOpenAt,
                onValueChange = { editorDoorsOpenAt = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(EventScreenTags.UPDATE_DOORS_OPEN_AT_INPUT),
                label = { Text("Открытие дверей (ISO)") },
                singleLine = true,
            )
            OutlinedTextField(
                value = editorEndsAt,
                onValueChange = { editorEndsAt = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(EventScreenTags.UPDATE_ENDS_AT_INPUT),
                label = { Text("Окончание (ISO)") },
                singleLine = true,
            )
            OutlinedTextField(
                value = editorCurrency,
                onValueChange = { editorCurrency = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(EventScreenTags.UPDATE_CURRENCY_INPUT),
                label = { Text("Валюта") },
                singleLine = true,
            )
            EventSelectorRow(
                items = listOf(
                    "public" to "Публичное",
                    "private" to "Приватное",
                ),
                selectedKey = editorVisibilityKey,
                onSelect = { editorVisibilityKey = it },
                tagPrefix = EventScreenTags.UPDATE_VISIBILITY_SELECTOR_PREFIX,
            )
            OutlinedTextField(
                value = editorPriceZonesText,
                onValueChange = { editorPriceZonesText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(EventScreenTags.UPDATE_PRICE_ZONES_INPUT),
                label = { Text("Event price zones") },
                supportingText = {
                    Text("Формат: id|name|priceMinor|currency|salesStartAt|salesEndAt|sourceTemplatePriceZoneId")
                },
                minLines = 3,
            )
            OutlinedTextField(
                value = editorPricingAssignmentsText,
                onValueChange = { editorPricingAssignmentsText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(EventScreenTags.UPDATE_PRICING_ASSIGNMENTS_INPUT),
                label = { Text("Pricing assignments") },
                supportingText = { Text("Формат: targetType|targetRef|eventPriceZoneId") },
                minLines = 3,
            )
            OutlinedTextField(
                value = editorAvailabilityOverridesText,
                onValueChange = { editorAvailabilityOverridesText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(EventScreenTags.UPDATE_AVAILABILITY_OVERRIDES_INPUT),
                label = { Text("Availability overrides") },
                supportingText = { Text("Формат: targetType|targetRef|availabilityStatus") },
                minLines = 3,
            )
            Button(
                onClick = {
                    eventBindings.onUpdateEvent(
                        EventUpdateForm(
                            eventId = selectedEditableEvent.id,
                            title = editorTitle,
                            description = editorDescription,
                            startsAtIso = editorStartsAt,
                            doorsOpenAtIso = editorDoorsOpenAt,
                            endsAtIso = editorEndsAt,
                            currency = editorCurrency,
                            visibilityKey = editorVisibilityKey,
                            priceZonesText = editorPriceZonesText,
                            pricingAssignmentsText = editorPricingAssignmentsText,
                            availabilityOverridesText = editorAvailabilityOverridesText,
                        ),
                    )
                },
                enabled = editorTitle.trim().length >= 3 &&
                    !eventState.isSubmitting &&
                    selectedEditableEvent.isEditable(),
                modifier = Modifier.testTag(EventScreenTags.UPDATE_SAVE_BUTTON),
            ) {
                Text("Сохранить override-ы")
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
 * @property onEditEvent Команда загрузки события в editor surface.
 * @property onPublishEvent Команда публикации draft-события.
 * @property onOpenEventSales Команда открытия продаж опубликованного события.
 * @property onPauseEventSales Команда паузы активных продаж.
 * @property onCancelEvent Команда отмены события.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OrganizerEventCard(
    event: OrganizerEvent,
    isSubmitting: Boolean,
    onEditEvent: (OrganizerEvent) -> Unit,
    onPublishEvent: (String) -> Unit,
    onOpenEventSales: (String) -> Unit,
    onPauseEventSales: (String) -> Unit,
    onCancelEvent: (String) -> Unit,
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
            Text(
                text = EventOverrideEditorCodec.summary(event),
                style = MaterialTheme.typography.bodySmall,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { onEditEvent(event) },
                    enabled = !isSubmitting && event.isEditable(),
                    modifier = Modifier.testTag("${EventScreenTags.EDIT_BUTTON_PREFIX}${event.id}"),
                ) {
                    Text("Редактировать")
                }
                if (event.status == EventStatus.DRAFT) {
                    Button(
                        onClick = { onPublishEvent(event.id) },
                        enabled = !isSubmitting,
                        modifier = Modifier.testTag("${EventScreenTags.PUBLISH_BUTTON_PREFIX}${event.id}"),
                    ) {
                        Text("Опубликовать")
                    }
                }
                if (event.canOpenSales()) {
                    Button(
                        onClick = { onOpenEventSales(event.id) },
                        enabled = !isSubmitting,
                        modifier = Modifier.testTag("${EventScreenTags.OPEN_SALES_BUTTON_PREFIX}${event.id}"),
                    ) {
                        Text(if (event.salesStatus == EventSalesStatus.PAUSED) "Возобновить продажи" else "Открыть продажи")
                    }
                }
                if (event.canPauseSales()) {
                    OutlinedButton(
                        onClick = { onPauseEventSales(event.id) },
                        enabled = !isSubmitting,
                        modifier = Modifier.testTag("${EventScreenTags.PAUSE_SALES_BUTTON_PREFIX}${event.id}"),
                    ) {
                        Text("Пауза продаж")
                    }
                }
                if (event.canCancel()) {
                    OutlinedButton(
                        onClick = { onCancelEvent(event.id) },
                        enabled = !isSubmitting,
                        modifier = Modifier.testTag("${EventScreenTags.CANCEL_BUTTON_PREFIX}${event.id}"),
                    ) {
                        Text("Отменить")
                    }
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

/** Возвращает `true`, если событие еще допускает organizer-side editing. */
private fun OrganizerEvent.isEditable(): Boolean = status != EventStatus.CANCELED

/** Возвращает `true`, если для события можно открыть или возобновить продажи. */
private fun OrganizerEvent.canOpenSales(): Boolean {
    return status == EventStatus.PUBLISHED &&
        (salesStatus == EventSalesStatus.CLOSED || salesStatus == EventSalesStatus.PAUSED)
}

/** Возвращает `true`, если активные продажи можно поставить на паузу. */
private fun OrganizerEvent.canPauseSales(): Boolean {
    return status == EventStatus.PUBLISHED && salesStatus == EventSalesStatus.OPEN
}

/** Возвращает `true`, если событие можно отменить в текущем organizer lifecycle state. */
private fun OrganizerEvent.canCancel(): Boolean {
    return status == EventStatus.DRAFT ||
        (status == EventStatus.PUBLISHED &&
            (salesStatus == EventSalesStatus.CLOSED ||
                salesStatus == EventSalesStatus.OPEN ||
                salesStatus == EventSalesStatus.PAUSED))
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

    /** Тег поля названия события для create form. */
    const val TITLE_INPUT = "event.form.title"

    /** Тег поля описания события для create form. */
    const val DESCRIPTION_INPUT = "event.form.description"

    /** Тег поля времени старта для create form. */
    const val STARTS_AT_INPUT = "event.form.startsAt"

    /** Тег поля времени открытия дверей для create form. */
    const val DOORS_OPEN_AT_INPUT = "event.form.doorsOpenAt"

    /** Тег поля времени окончания для create form. */
    const val ENDS_AT_INPUT = "event.form.endsAt"

    /** Тег поля валюты для create form. */
    const val CURRENCY_INPUT = "event.form.currency"

    /** Тег кнопки создания события. */
    const val CREATE_BUTTON = "event.form.create"

    /** Тег пустого состояния списка событий. */
    const val EVENTS_EMPTY = "event.list.empty"

    /** Тег пустого состояния editor-а. */
    const val EDITOR_EMPTY = "event.editor.empty"

    /** Тег поля названия события для update form. */
    const val UPDATE_TITLE_INPUT = "event.update.title"

    /** Тег поля описания события для update form. */
    const val UPDATE_DESCRIPTION_INPUT = "event.update.description"

    /** Тег поля времени старта для update form. */
    const val UPDATE_STARTS_AT_INPUT = "event.update.startsAt"

    /** Тег поля времени открытия дверей для update form. */
    const val UPDATE_DOORS_OPEN_AT_INPUT = "event.update.doorsOpenAt"

    /** Тег поля времени окончания для update form. */
    const val UPDATE_ENDS_AT_INPUT = "event.update.endsAt"

    /** Тег поля валюты для update form. */
    const val UPDATE_CURRENCY_INPUT = "event.update.currency"

    /** Тег поля event-local price zones. */
    const val UPDATE_PRICE_ZONES_INPUT = "event.update.priceZones"

    /** Тег поля pricing assignments. */
    const val UPDATE_PRICING_ASSIGNMENTS_INPUT = "event.update.pricingAssignments"

    /** Тег поля availability overrides. */
    const val UPDATE_AVAILABILITY_OVERRIDES_INPUT = "event.update.availabilityOverrides"

    /** Тег кнопки сохранения update form. */
    const val UPDATE_SAVE_BUTTON = "event.update.save"

    /** Префикс тегов selector-а workspace. */
    const val WORKSPACE_SELECTOR_PREFIX = "event.workspace."

    /** Префикс тегов selector-а venue. */
    const val VENUE_SELECTOR_PREFIX = "event.venue."

    /** Префикс тегов selector-а template. */
    const val TEMPLATE_SELECTOR_PREFIX = "event.template."

    /** Префикс тегов selector-а visibility для create form. */
    const val VISIBILITY_SELECTOR_PREFIX = "event.visibility."

    /** Префикс тегов selector-а visibility для update form. */
    const val UPDATE_VISIBILITY_SELECTOR_PREFIX = "event.update.visibility."

    /** Префикс тегов edit-кнопок event list-а. */
    const val EDIT_BUTTON_PREFIX = "event.edit."

    /** Префикс тегов publish-кнопок event list-а. */
    const val PUBLISH_BUTTON_PREFIX = "event.publish."

    /** Префикс тегов кнопок открытия продаж event list-а. */
    const val OPEN_SALES_BUTTON_PREFIX = "event.sales.open."

    /** Префикс тегов кнопок паузы продаж event list-а. */
    const val PAUSE_SALES_BUTTON_PREFIX = "event.sales.pause."

    /** Префикс тегов кнопок отмены события event list-а. */
    const val CANCEL_BUTTON_PREFIX = "event.cancel."
}
