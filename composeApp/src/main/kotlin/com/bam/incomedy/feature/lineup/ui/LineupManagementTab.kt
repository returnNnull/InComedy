package com.bam.incomedy.feature.lineup.ui

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bam.incomedy.domain.event.OrganizerEvent
import com.bam.incomedy.domain.lineup.ComedianApplication
import com.bam.incomedy.domain.lineup.ComedianApplicationStatus
import com.bam.incomedy.domain.lineup.LineupEntry
import com.bam.incomedy.feature.lineup.LineupState

/**
 * Набор данных и команд, который main shell передает во вкладку comedian applications и lineup.
 *
 * Binding отделяет session shell от shared lineup feature и позволяет тестировать вкладку без
 * прямой зависимости от Android `ViewModel`.
 */
internal data class LineupTabBindings(
    /** Актуальное состояние lineup feature. */
    val state: LineupState = LineupState(),
    /** Organizer events, из которых можно выбрать контекст для review/reorder. */
    val organizerEvents: List<OrganizerEvent> = emptyList(),
    /** Команда загрузки organizer applications и lineup для выбранного события. */
    val onLoadOrganizerContext: (String) -> Unit = {},
    /** Команда comedian submit flow по идентификатору события. */
    val onSubmitApplication: (String, String?) -> Unit = { _, _ -> },
    /** Команда смены organizer review-статуса заявки. */
    val onUpdateApplicationStatus: (String, String, ComedianApplicationStatus) -> Unit = { _, _, _ -> },
    /** Команда перестановки полного lineup списка. */
    val onReorderLineup: (String, List<String>) -> Unit = { _, _ -> },
    /** Команда очистки верхнеуровневой lineup-ошибки. */
    val onClearError: () -> Unit = {},
)

/**
 * Вкладка comedian applications и organizer lineup внутри авторизованного Android shell.
 *
 * Вкладка использует уже доставленный shared lineup bounded context и ограничивается безопасным
 * UI wiring: выбор organizer event, review/reorder controls и простой comedian submit form по
 * `eventId` без добавления нового public discovery slice-а.
 *
 * @property lineupBindings Lineup-specific state и callbacks.
 * @property modifier Внешний модификатор контейнера.
 */
@Composable
internal fun LineupManagementTab(
    lineupBindings: LineupTabBindings,
    modifier: Modifier = Modifier,
) {
    /** Хранит выбранное organizer event для review/reorder context. */
    var selectedOrganizerEventId by rememberSaveable { mutableStateOf("") }
    /** Хранит event id для comedian submit формы. */
    var submitEventId by rememberSaveable { mutableStateOf("") }
    /** Хранит заметку для comedian submit формы. */
    var applicationNote by rememberSaveable { mutableStateOf("") }

    val lineupState = lineupBindings.state
    val organizerEvents = lineupBindings.organizerEvents
    val eventTitleById = remember(organizerEvents) {
        organizerEvents.associate { event -> event.id to event.title }
    }

    LaunchedEffect(organizerEvents) {
        val firstEventId = organizerEvents.firstOrNull()?.id ?: return@LaunchedEffect
        if (selectedOrganizerEventId.isBlank()) {
            selectedOrganizerEventId = firstEventId
        }
        if (submitEventId.isBlank()) {
            submitEventId = firstEventId
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(LineupScreenTags.ROOT),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Лайнап и заявки",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = "Organizer review/reorder и простой comedian submit form поверх общего KMP foundation",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        LineupBanner(
            message = lineupState.errorMessage,
            actionLabel = "Скрыть",
            onAction = lineupBindings.onClearError,
        )

        if (lineupState.isLoading || lineupState.isSubmitting) {
            CircularProgressIndicator(
                modifier = Modifier.testTag(LineupScreenTags.LOADING),
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Заявок: ${lineupState.applications.size}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.testTag(LineupScreenTags.APPLICATION_COUNT),
            )
            Text(
                text = "Лайнап: ${lineupState.lineup.size}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.testTag(LineupScreenTags.LINEUP_COUNT),
            )
        }

        OrganizerContextSection(
            organizerEvents = organizerEvents,
            selectedOrganizerEventId = selectedOrganizerEventId,
            isBusy = lineupState.isLoading || lineupState.isSubmitting,
            onSelectEvent = { eventId ->
                selectedOrganizerEventId = eventId
                if (submitEventId.isBlank()) {
                    submitEventId = eventId
                }
            },
            onLoadContext = {
                lineupBindings.onLoadOrganizerContext(selectedOrganizerEventId)
            },
        )

        HorizontalDivider()

        SubmitApplicationSection(
            submitEventId = submitEventId,
            note = applicationNote,
            isBusy = lineupState.isSubmitting,
            onEventIdChange = { submitEventId = it },
            onNoteChange = { applicationNote = it },
            onSubmit = {
                lineupBindings.onSubmitApplication(
                    submitEventId,
                    applicationNote.ifBlank { null },
                )
            },
        )

        HorizontalDivider()

        ApplicationListSection(
            applications = lineupState.applications,
            eventTitleById = eventTitleById,
            isBusy = lineupState.isSubmitting,
            onUpdateStatus = { applicationId, eventId, status ->
                lineupBindings.onUpdateApplicationStatus(eventId, applicationId, status)
            },
        )

        HorizontalDivider()

        LineupListSection(
            entries = lineupState.lineup,
            eventTitleById = eventTitleById,
            selectedEventId = lineupState.selectedEventId ?: selectedOrganizerEventId,
            isBusy = lineupState.isSubmitting,
            onMoveEntry = { entryId, delta ->
                val eventId = lineupState.selectedEventId ?: selectedOrganizerEventId
                if (eventId.isBlank()) return@LineupListSection
                val reorderedIds = reorderEntryIds(
                    entries = lineupState.lineup,
                    entryId = entryId,
                    delta = delta,
                ) ?: return@LineupListSection
                lineupBindings.onReorderLineup(eventId, reorderedIds)
            },
        )
    }
}

/**
 * Секция выбора organizer event и загрузки связанного applications/lineup context.
 *
 * @property organizerEvents Список доступных organizer events.
 * @property selectedOrganizerEventId Текущий выбранный organizer event.
 * @property isBusy Признак активной загрузки или мутации.
 * @property onSelectEvent Колбэк выбора organizer event.
 * @property onLoadContext Колбэк явной загрузки organizer context.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OrganizerContextSection(
    organizerEvents: List<OrganizerEvent>,
    selectedOrganizerEventId: String,
    isBusy: Boolean,
    onSelectEvent: (String) -> Unit,
    onLoadContext: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(LineupScreenTags.ORGANIZER_SECTION),
    ) {
        Text(
            text = "Organizer context",
            style = MaterialTheme.typography.titleMedium,
        )
        if (organizerEvents.isEmpty()) {
            Text(
                text = "Organizer events пока не загружены. Используйте submit form ниже, если нужен только comedian apply flow.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(LineupScreenTags.ORGANIZER_EMPTY),
            )
            return
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            organizerEvents.forEach { event ->
                SelectionButton(
                    title = event.title,
                    isSelected = selectedOrganizerEventId == event.id,
                    onClick = { onSelectEvent(event.id) },
                    tag = "${LineupScreenTags.EVENT_SELECTOR_PREFIX}${event.id}",
                )
            }
        }
        Button(
            onClick = onLoadContext,
            enabled = selectedOrganizerEventId.isNotBlank() && !isBusy,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(LineupScreenTags.LOAD_BUTTON),
        ) {
            Text("Загрузить заявки и лайнап")
        }
    }
}

/**
 * Секция comedian submit form, работающая по явному `eventId`.
 *
 * @property submitEventId Текущий event id формы.
 * @property note Текст заметки к заявке.
 * @property isBusy Признак активной submit-операции.
 * @property onEventIdChange Колбэк обновления event id.
 * @property onNoteChange Колбэк обновления заметки.
 * @property onSubmit Колбэк отправки формы.
 */
@Composable
private fun SubmitApplicationSection(
    submitEventId: String,
    note: String,
    isBusy: Boolean,
    onEventIdChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(LineupScreenTags.SUBMIT_SECTION),
    ) {
        Text(
            text = "Подать заявку",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = "Форма принимает явный `eventId`, пока public discovery slice еще не добавлен в мобильный клиент",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = submitEventId,
            onValueChange = onEventIdChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(LineupScreenTags.SUBMIT_EVENT_ID_INPUT),
            label = { Text("Event ID") },
            singleLine = true,
            enabled = !isBusy,
        )
        OutlinedTextField(
            value = note,
            onValueChange = onNoteChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(LineupScreenTags.SUBMIT_NOTE_INPUT),
            label = { Text("Заметка к заявке") },
            minLines = 2,
            enabled = !isBusy,
        )
        Button(
            onClick = onSubmit,
            enabled = submitEventId.trim().isNotEmpty() && !isBusy,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(LineupScreenTags.SUBMIT_BUTTON),
        ) {
            Text("Отправить заявку")
        }
    }
}

/**
 * Секция organizer review списка заявок.
 *
 * @property applications Текущий список заявок.
 * @property eventTitleById Карта названий событий по id.
 * @property isBusy Признак активной мутации.
 * @property onUpdateStatus Колбэк изменения review-статуса.
 */
@Composable
private fun ApplicationListSection(
    applications: List<ComedianApplication>,
    eventTitleById: Map<String, String>,
    isBusy: Boolean,
    onUpdateStatus: (String, String, ComedianApplicationStatus) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(LineupScreenTags.APPLICATION_SECTION),
    ) {
        Text(
            text = "Заявки комиков",
            style = MaterialTheme.typography.titleMedium,
        )
        if (applications.isEmpty()) {
            Text(
                text = "После загрузки organizer context здесь появятся comedian applications.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(LineupScreenTags.APPLICATION_EMPTY),
            )
            return
        }
        applications.forEach { application ->
            ApplicationCard(
                application = application,
                eventTitle = eventTitleById[application.eventId] ?: application.eventId,
                isBusy = isBusy,
                onUpdateStatus = onUpdateStatus,
            )
        }
    }
}

/**
 * Карточка одной comedian application с review-action кнопками.
 *
 * @property application Данные заявки.
 * @property eventTitle Название связанного события.
 * @property isBusy Признак активной мутации.
 * @property onUpdateStatus Колбэк смены статуса.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ApplicationCard(
    application: ComedianApplication,
    eventTitle: String,
    isBusy: Boolean,
    onUpdateStatus: (String, String, ComedianApplicationStatus) -> Unit,
) {
    Surface(
        tonalElevation = 1.dp,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("${LineupScreenTags.APPLICATION_CARD_PREFIX}${application.id}"),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = application.comedianDisplayName,
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = "Событие: $eventTitle",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "Статус: ${applicationStatusTitle(application.status)}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("${LineupScreenTags.APPLICATION_STATUS_PREFIX}${application.id}"),
            )
            application.note?.takeIf(String::isNotBlank)?.let { note ->
                Text(
                    text = "Заметка: $note",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                REVIEW_ACTIONS.forEach { action ->
                    SelectionButton(
                        title = action.title,
                        isSelected = application.status == action.status,
                        onClick = {
                            onUpdateStatus(
                                application.id,
                                application.eventId,
                                action.status,
                            )
                        },
                        enabled = !isBusy,
                        tag = "${LineupScreenTags.APPLICATION_ACTION_PREFIX}${application.id}.${action.status.wireName}",
                    )
                }
            }
        }
    }
}

/**
 * Секция organizer lineup с кнопками локальной перестановки вверх/вниз.
 *
 * @property entries Текущий lineup в явном порядке.
 * @property eventTitleById Карта названий событий по id.
 * @property selectedEventId Идентификатор события, к которому относится reorder.
 * @property isBusy Признак активной мутации.
 * @property onMoveEntry Колбэк смещения entry на одну позицию.
 */
@Composable
private fun LineupListSection(
    entries: List<LineupEntry>,
    eventTitleById: Map<String, String>,
    selectedEventId: String,
    isBusy: Boolean,
    onMoveEntry: (String, Int) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(LineupScreenTags.LINEUP_SECTION),
    ) {
        Text(
            text = "Текущий лайнап",
            style = MaterialTheme.typography.titleMedium,
        )
        if (entries.isEmpty()) {
            Text(
                text = if (selectedEventId.isBlank()) {
                    "Сначала выберите organizer event и загрузите контекст."
                } else {
                    "Approved заявки пока не материализовали lineup для этого события."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(LineupScreenTags.LINEUP_EMPTY),
            )
            return
        }
        entries.forEachIndexed { index, entry ->
            LineupEntryCard(
                entry = entry,
                eventTitle = eventTitleById[entry.eventId] ?: entry.eventId,
                canMoveUp = index > 0,
                canMoveDown = index < entries.lastIndex,
                isBusy = isBusy,
                onMoveUp = { onMoveEntry(entry.id, -1) },
                onMoveDown = { onMoveEntry(entry.id, +1) },
            )
        }
    }
}

/**
 * Карточка одного lineup entry с кнопками смещения по explicit order.
 *
 * @property entry Данные lineup entry.
 * @property eventTitle Название связанного события.
 * @property canMoveUp Показывает, можно ли сместить entry выше.
 * @property canMoveDown Показывает, можно ли сместить entry ниже.
 * @property isBusy Признак активной мутации.
 * @property onMoveUp Колбэк перемещения вверх.
 * @property onMoveDown Колбэк перемещения вниз.
 */
@Composable
private fun LineupEntryCard(
    entry: LineupEntry,
    eventTitle: String,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    isBusy: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    Surface(
        tonalElevation = 1.dp,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("${LineupScreenTags.LINEUP_CARD_PREFIX}${entry.id}"),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "${entry.orderIndex}. ${entry.comedianDisplayName}",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = "Событие: $eventTitle",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "Статус: ${lineupStatusTitle(entry.status.wireName)}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("${LineupScreenTags.LINEUP_STATUS_PREFIX}${entry.id}"),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onMoveUp,
                    enabled = canMoveUp && !isBusy,
                    modifier = Modifier.testTag("${LineupScreenTags.LINEUP_MOVE_UP_PREFIX}${entry.id}"),
                ) {
                    Text("Выше")
                }
                OutlinedButton(
                    onClick = onMoveDown,
                    enabled = canMoveDown && !isBusy,
                    modifier = Modifier.testTag("${LineupScreenTags.LINEUP_MOVE_DOWN_PREFIX}${entry.id}"),
                ) {
                    Text("Ниже")
                }
            }
        }
    }
}

/**
 * Универсальная компактная кнопка выбора для event/status списков.
 *
 * @property title Подпись кнопки.
 * @property isSelected Показывает активное состояние выбора.
 * @property onClick Колбэк нажатия.
 * @property enabled Признак доступности кнопки.
 * @property tag UI-тег для тестов.
 */
@Composable
private fun SelectionButton(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
    tag: String,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        border = BorderStroke(
            width = 1.dp,
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline
            },
        ),
        modifier = Modifier.testTag(tag),
    ) {
        Text(title)
    }
}

/**
 * Баннер верхнеуровневой lineup-ошибки.
 *
 * @property message Текст ошибки.
 * @property actionLabel Подпись кнопки скрытия.
 * @property onAction Колбэк очистки ошибки.
 */
@Composable
private fun LineupBanner(
    message: String?,
    actionLabel: String,
    onAction: () -> Unit,
) {
    if (message == null) return

    Surface(
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(LineupScreenTags.ERROR_BANNER),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(
                onClick = onAction,
            ) {
                Text(actionLabel)
            }
        }
    }
}

/**
 * Переставляет один entry на соседнюю позицию и возвращает новый полный порядок id.
 *
 * @param entries Текущий lineup.
 * @param entryId Идентификатор entry, который нужно сместить.
 * @param delta Смещение вверх `-1` или вниз `+1`.
 * @return Новый полный порядок id либо `null`, если смещение невозможно.
 */
private fun reorderEntryIds(
    entries: List<LineupEntry>,
    entryId: String,
    delta: Int,
): List<String>? {
    val sourceIndex = entries.indexOfFirst { entry -> entry.id == entryId }
    if (sourceIndex == -1) return null
    val targetIndex = sourceIndex + delta
    if (targetIndex !in entries.indices) return null

    return entries
        .map(LineupEntry::id)
        .toMutableList()
        .also { orderedIds ->
            val movedId = orderedIds.removeAt(sourceIndex)
            orderedIds.add(targetIndex, movedId)
        }
}

/**
 * Возвращает человекочитаемую подпись review-статуса.
 *
 * @param status Текущий enum review-статуса.
 * @return Текстовая подпись для UI.
 */
private fun applicationStatusTitle(status: ComedianApplicationStatus): String {
    return when (status) {
        ComedianApplicationStatus.SUBMITTED -> "На ревью"
        ComedianApplicationStatus.SHORTLISTED -> "Шортлист"
        ComedianApplicationStatus.APPROVED -> "Одобрена"
        ComedianApplicationStatus.WAITLISTED -> "Лист ожидания"
        ComedianApplicationStatus.REJECTED -> "Отклонена"
        ComedianApplicationStatus.WITHDRAWN -> "Снята"
    }
}

/**
 * Возвращает подпись текущего MVP-статуса lineup entry.
 *
 * @param statusKey Wire-ключ статуса entry.
 * @return Текстовая подпись для UI.
 */
private fun lineupStatusTitle(statusKey: String): String {
    return when (statusKey) {
        "draft" -> "Draft"
        else -> statusKey
    }
}

/**
 * Описание review-action кнопки organizer-а.
 *
 * @property status Целевой статус.
 * @property title Подпись кнопки.
 */
private data class ReviewAction(
    val status: ComedianApplicationStatus,
    val title: String,
)

/**
 * Список доступных organizer review-мутаций для текущего MVP slice-а.
 */
private val REVIEW_ACTIONS = listOf(
    ReviewAction(
        status = ComedianApplicationStatus.SHORTLISTED,
        title = "Шортлист",
    ),
    ReviewAction(
        status = ComedianApplicationStatus.APPROVED,
        title = "Approve",
    ),
    ReviewAction(
        status = ComedianApplicationStatus.WAITLISTED,
        title = "Waitlist",
    ),
    ReviewAction(
        status = ComedianApplicationStatus.REJECTED,
        title = "Reject",
    ),
)

/**
 * Набор тегов, по которым UI-тесты находят ключевые элементы lineup surface.
 */
object LineupScreenTags {
    /** Тег корневого контейнера вкладки. */
    const val ROOT = "lineup.root"

    /** Тег верхнеуровневого баннера ошибки. */
    const val ERROR_BANNER = "lineup.error"

    /** Тег индикатора загрузки или мутации. */
    const val LOADING = "lineup.loading"

    /** Тег счетчика заявок. */
    const val APPLICATION_COUNT = "lineup.count.applications"

    /** Тег счетчика lineup entries. */
    const val LINEUP_COUNT = "lineup.count.entries"

    /** Тег organizer context секции. */
    const val ORGANIZER_SECTION = "lineup.organizer"

    /** Тег empty state organizer секции. */
    const val ORGANIZER_EMPTY = "lineup.organizer.empty"

    /** Префикс тегов выбора organizer event. */
    const val EVENT_SELECTOR_PREFIX = "lineup.event."

    /** Тег кнопки загрузки organizer context. */
    const val LOAD_BUTTON = "lineup.organizer.load"

    /** Тег submit секции. */
    const val SUBMIT_SECTION = "lineup.submit"

    /** Тег поля event id submit формы. */
    const val SUBMIT_EVENT_ID_INPUT = "lineup.submit.eventId"

    /** Тег поля заметки submit формы. */
    const val SUBMIT_NOTE_INPUT = "lineup.submit.note"

    /** Тег кнопки submit формы. */
    const val SUBMIT_BUTTON = "lineup.submit.button"

    /** Тег секции списка заявок. */
    const val APPLICATION_SECTION = "lineup.applications"

    /** Тег empty state списка заявок. */
    const val APPLICATION_EMPTY = "lineup.applications.empty"

    /** Префикс тегов карточек заявок. */
    const val APPLICATION_CARD_PREFIX = "lineup.application.card."

    /** Префикс тегов текста статуса заявки. */
    const val APPLICATION_STATUS_PREFIX = "lineup.application.status."

    /** Префикс тегов review-action кнопок. */
    const val APPLICATION_ACTION_PREFIX = "lineup.application.action."

    /** Тег секции lineup списка. */
    const val LINEUP_SECTION = "lineup.entries"

    /** Тег empty state lineup списка. */
    const val LINEUP_EMPTY = "lineup.entries.empty"

    /** Префикс тегов карточек lineup entry. */
    const val LINEUP_CARD_PREFIX = "lineup.entry.card."

    /** Префикс тегов текста статуса lineup entry. */
    const val LINEUP_STATUS_PREFIX = "lineup.entry.status."

    /** Префикс тегов кнопок смещения вверх. */
    const val LINEUP_MOVE_UP_PREFIX = "lineup.entry.moveUp."

    /** Префикс тегов кнопок смещения вниз. */
    const val LINEUP_MOVE_DOWN_PREFIX = "lineup.entry.moveDown."
}
