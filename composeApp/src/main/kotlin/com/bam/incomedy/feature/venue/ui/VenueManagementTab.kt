package com.bam.incomedy.feature.venue.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import com.bam.incomedy.domain.session.OrganizerWorkspace
import com.bam.incomedy.domain.venue.HallTemplate
import com.bam.incomedy.domain.venue.HallTemplateStatus
import com.bam.incomedy.domain.venue.OrganizerVenue
import com.bam.incomedy.feature.venue.HallLayoutEditorCodec
import com.bam.incomedy.feature.venue.VenueState

/**
 * Набор данных и команд, который main shell передает во вкладку organizer venue management.
 *
 * Binding отделяет session shell от самих venue-операций и позволяет тестировать вкладку без
 * прямой зависимости от Android `ViewModel`.
 */
internal data class VenueTabBindings(
    /** Актуальное состояние organizer venue feature. */
    val state: VenueState = VenueState(),
    /** Команда ручной перезагрузки списка площадок. */
    val onRefreshVenues: () -> Unit = {},
    /** Команда создания новой площадки. */
    val onCreateVenue: (VenueCreateForm) -> Unit = {},
    /** Команда создания или обновления hall template. */
    val onSaveHallTemplate: (HallTemplateEditorForm) -> Unit = {},
    /** Команда клонирования существующего hall template. */
    val onCloneHallTemplate: (String, String?) -> Unit = { _, _ -> },
    /** Команда очистки верхнеуровневой venue-ошибки. */
    val onClearError: () -> Unit = {},
)

/**
 * Platform-friendly форма создания площадки.
 *
 * @property workspaceId Идентификатор выбранного organizer workspace.
 * @property name Название площадки.
 * @property city Город площадки.
 * @property address Адрес площадки.
 * @property timezone IANA timezone площадки.
 * @property capacityText Вместимость в текстовом виде.
 * @property description Необязательное описание площадки.
 * @property contactsText Контакты `label|value` по одной строке.
 */
internal data class VenueCreateForm(
    val workspaceId: String,
    val name: String,
    val city: String,
    val address: String,
    val timezone: String,
    val capacityText: String,
    val description: String,
    val contactsText: String,
)

/**
 * Platform-friendly форма редактора hall template builder v1.
 *
 * @property venueId Идентификатор площадки-владельца.
 * @property templateId Необязательный id редактируемого шаблона.
 * @property name Название шаблона.
 * @property statusKey Wire-ключ статуса шаблона.
 * @property stageLabel Название сцены.
 * @property priceZonesText Текст ценовых зон.
 * @property zonesText Текст standing/sector зон.
 * @property rowsText Текст рядов.
 * @property tablesText Текст столов.
 * @property serviceAreasText Текст служебных зон.
 * @property blockedSeatRefsText Текст blocked seat refs.
 */
internal data class HallTemplateEditorForm(
    val venueId: String,
    val templateId: String?,
    val name: String,
    val statusKey: String,
    val stageLabel: String,
    val priceZonesText: String,
    val zonesText: String,
    val rowsText: String,
    val tablesText: String,
    val serviceAreasText: String,
    val blockedSeatRefsText: String,
)

/**
 * Вкладка organizer venue management внутри авторизованного Android shell.
 *
 * Вкладка покрывает первый delivery slice: `Venue CRUD + HallTemplate create/update/clone`.
 *
 * @property workspaces Рабочие пространства, доступные текущему организатору.
 * @property venueBindings Venue-specific state и callbacks.
 * @property modifier Внешний модификатор контейнера.
 */
@Composable
internal fun VenueManagementTab(
    workspaces: List<OrganizerWorkspace>,
    venueBindings: VenueTabBindings,
    modifier: Modifier = Modifier,
) {
    /** Хранит выбранный workspace для создания площадки. */
    var selectedWorkspaceId by rememberSaveable { mutableStateOf("") }
    /** Хранит имя создаваемой площадки. */
    var venueName by rememberSaveable { mutableStateOf("") }
    /** Хранит город площадки. */
    var venueCity by rememberSaveable { mutableStateOf("") }
    /** Хранит адрес площадки. */
    var venueAddress by rememberSaveable { mutableStateOf("") }
    /** Хранит IANA timezone площадки. */
    var venueTimezone by rememberSaveable { mutableStateOf("Europe/Moscow") }
    /** Хранит текстовую вместимость площадки. */
    var venueCapacity by rememberSaveable { mutableStateOf("120") }
    /** Хранит описание площадки. */
    var venueDescription by rememberSaveable { mutableStateOf("") }
    /** Хранит контакты площадки в формате `label|value`. */
    var venueContacts by rememberSaveable { mutableStateOf("Telegram|@venue") }
    /** Хранит выбранную площадку для builder-секции. */
    var selectedVenueId by rememberSaveable { mutableStateOf("") }
    /** Хранит id шаблона, который сейчас редактируется. */
    var selectedTemplateId by rememberSaveable { mutableStateOf<String?>(null) }
    /** Хранит название редактируемого шаблона. */
    var templateName by rememberSaveable { mutableStateOf("") }
    /** Хранит lifecycle-статус шаблона. */
    var templateStatusKey by rememberSaveable { mutableStateOf(HallTemplateStatus.DRAFT.wireName) }
    /** Хранит подпись сцены. */
    var templateStageLabel by rememberSaveable { mutableStateOf("") }
    /** Хранит текст ценовых зон. */
    var templatePriceZones by rememberSaveable { mutableStateOf("") }
    /** Хранит текст standing/sector зон. */
    var templateZones by rememberSaveable { mutableStateOf("") }
    /** Хранит текст рядов. */
    var templateRows by rememberSaveable { mutableStateOf("row-a|A|10|") }
    /** Хранит текст столов. */
    var templateTables by rememberSaveable { mutableStateOf("") }
    /** Хранит текст служебных зон. */
    var templateServiceAreas by rememberSaveable { mutableStateOf("") }
    /** Хранит blocked seat refs. */
    var templateBlockedSeats by rememberSaveable { mutableStateOf("") }

    val venueState = venueBindings.state
    val selectedVenue = venueState.venues.firstOrNull { venue -> venue.id == selectedVenueId }
        ?: venueState.venues.firstOrNull()

    LaunchedEffect(workspaces) {
        if (selectedWorkspaceId.isBlank() || workspaces.none { workspace -> workspace.id == selectedWorkspaceId }) {
            selectedWorkspaceId = workspaces.firstOrNull()?.id.orEmpty()
        }
    }
    LaunchedEffect(venueState.venues) {
        if (selectedVenueId.isBlank() || venueState.venues.none { venue -> venue.id == selectedVenueId }) {
            selectedVenueId = venueState.venues.firstOrNull()?.id.orEmpty()
        }
    }
    LaunchedEffect(selectedVenue?.id, selectedVenue?.hallTemplates) {
        val templateStillExists = selectedVenue
            ?.hallTemplates
            ?.any { template -> template.id == selectedTemplateId }
            ?: false
        if (!templateStillExists) {
            selectedTemplateId = null
            templateName = ""
            templateStatusKey = HallTemplateStatus.DRAFT.wireName
            templateStageLabel = ""
            templatePriceZones = ""
            templateZones = ""
            templateRows = ""
            templateTables = ""
            templateServiceAreas = ""
            templateBlockedSeats = ""
        }
    }

    val loadTemplateIntoEditor = remember {
        { template: HallTemplate ->
            val input = HallLayoutEditorCodec.fromTemplate(template)
            selectedTemplateId = template.id
            templateName = template.name
            templateStatusKey = template.status.wireName
            templateStageLabel = input.stageLabel
            templatePriceZones = input.priceZonesText
            templateZones = input.zonesText
            templateRows = input.rowsText
            templateTables = input.tablesText
            templateServiceAreas = input.serviceAreasText
            templateBlockedSeats = input.blockedSeatRefsText
        }
    }
    val resetTemplateEditor = remember {
        {
            selectedTemplateId = null
            templateName = ""
            templateStatusKey = HallTemplateStatus.DRAFT.wireName
            templateStageLabel = ""
            templatePriceZones = ""
            templateZones = ""
            templateRows = ""
            templateTables = ""
            templateServiceAreas = ""
            templateBlockedSeats = ""
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(VenueScreenTags.ROOT),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Площадки и схемы зала",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = "Organizer slice для площадок, hall templates и базового 2D builder v1",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
        )

        val errorMessage = venueState.errorMessage
        if (errorMessage != null) {
            VenueErrorBanner(
                errorMessage = errorMessage,
                onClearError = venueBindings.onClearError,
            )
        }

        if (venueState.isLoading || venueState.isSubmitting) {
            CircularProgressIndicator(
                modifier = Modifier.testTag(VenueScreenTags.LOADING),
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Площадок: ${venueState.venues.size}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag(VenueScreenTags.COUNT),
            )
            OutlinedButton(
                onClick = venueBindings.onRefreshVenues,
                enabled = !venueState.isLoading && !venueState.isSubmitting,
                modifier = Modifier.testTag(VenueScreenTags.REFRESH_BUTTON),
            ) {
                Text("Обновить")
            }
        }

        HorizontalDivider()

        Text(
            text = "Создать площадку",
            style = MaterialTheme.typography.titleMedium,
        )
        if (workspaces.isEmpty()) {
            Text(
                text = "Сначала создайте organizer workspace, затем можно будет завести площадку.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.testTag(VenueScreenTags.WORKSPACE_EMPTY),
            )
        } else {
            SelectorRow(
                items = workspaces.map { workspace -> workspace.id to workspace.name },
                selectedKey = selectedWorkspaceId,
                onSelect = { selectedWorkspaceId = it },
                tagPrefix = VenueScreenTags.WORKSPACE_SELECTOR_PREFIX,
            )
        }
        OutlinedTextField(
            value = venueName,
            onValueChange = { venueName = it },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(VenueScreenTags.VENUE_NAME_INPUT),
            label = { Text("Название площадки") },
            singleLine = true,
        )
        OutlinedTextField(
            value = venueCity,
            onValueChange = { venueCity = it },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(VenueScreenTags.VENUE_CITY_INPUT),
            label = { Text("Город") },
            singleLine = true,
        )
        OutlinedTextField(
            value = venueAddress,
            onValueChange = { venueAddress = it },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(VenueScreenTags.VENUE_ADDRESS_INPUT),
            label = { Text("Адрес") },
            singleLine = true,
        )
        OutlinedTextField(
            value = venueTimezone,
            onValueChange = { venueTimezone = it },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(VenueScreenTags.VENUE_TIMEZONE_INPUT),
            label = { Text("Timezone") },
            singleLine = true,
        )
        OutlinedTextField(
            value = venueCapacity,
            onValueChange = { venueCapacity = it },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(VenueScreenTags.VENUE_CAPACITY_INPUT),
            label = { Text("Вместимость") },
            singleLine = true,
        )
        OutlinedTextField(
            value = venueDescription,
            onValueChange = { venueDescription = it },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(VenueScreenTags.VENUE_DESCRIPTION_INPUT),
            label = { Text("Описание") },
            minLines = 2,
        )
        OutlinedTextField(
            value = venueContacts,
            onValueChange = { venueContacts = it },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(VenueScreenTags.VENUE_CONTACTS_INPUT),
            label = { Text("Контакты") },
            supportingText = { Text("Формат: label|value, по одной записи на строку") },
            minLines = 2,
        )
        Button(
            onClick = {
                venueBindings.onCreateVenue(
                    VenueCreateForm(
                        workspaceId = selectedWorkspaceId,
                        name = venueName,
                        city = venueCity,
                        address = venueAddress,
                        timezone = venueTimezone,
                        capacityText = venueCapacity,
                        description = venueDescription,
                        contactsText = venueContacts,
                    ),
                )
            },
            enabled = selectedWorkspaceId.isNotBlank() &&
                venueName.trim().length in 3..120 &&
                venueCity.isNotBlank() &&
                venueAddress.isNotBlank() &&
                venueTimezone.isNotBlank() &&
                venueCapacity.isNotBlank() &&
                !venueState.isSubmitting,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(VenueScreenTags.CREATE_BUTTON),
        ) {
            Text("Сохранить площадку")
        }

        HorizontalDivider()

        if (venueState.venues.isEmpty()) {
            Text(
                text = "Пока нет ни одной площадки. Сохраните первую площадку и затем соберите шаблон зала.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.testTag(VenueScreenTags.VENUE_EMPTY),
            )
        } else {
            Text(
                text = "Выбрать площадку для builder",
                style = MaterialTheme.typography.titleMedium,
            )
            SelectorRow(
                items = venueState.venues.map { venue -> venue.id to venue.name },
                selectedKey = selectedVenue?.id.orEmpty(),
                onSelect = { selectedVenueId = it },
                tagPrefix = VenueScreenTags.VENUE_SELECTOR_PREFIX,
            )
            selectedVenue?.let { venue ->
                VenueCard(
                    venue = venue,
                    onEditTemplate = loadTemplateIntoEditor,
                    onCloneTemplate = { template ->
                        venueBindings.onCloneHallTemplate(template.id, "${template.name} копия")
                    },
                )
            }
        }

        HorizontalDivider()

        Text(
            text = "Конструктор шаблона зала",
            style = MaterialTheme.typography.titleMedium,
        )
        if (selectedVenue == null) {
            Text(
                text = "Выберите площадку, чтобы создать или отредактировать hall template.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.testTag(VenueScreenTags.TEMPLATE_EMPTY),
            )
        } else {
            Text(
                text = "Площадка: ${selectedVenue.name}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (selectedVenue.hallTemplates.isNotEmpty()) {
                Text(
                    text = "Шаблоны этой площадки можно редактировать или клонировать ниже.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = resetTemplateEditor,
                    modifier = Modifier.testTag(VenueScreenTags.TEMPLATE_RESET_BUTTON),
                ) {
                    Text("Новый шаблон")
                }
                if (selectedTemplateId != null) {
                    OutlinedButton(
                        onClick = {
                            venueBindings.onCloneHallTemplate(
                                selectedTemplateId.orEmpty(),
                                templateName.trim().takeIf(String::isNotBlank)?.let { "$it копия" },
                            )
                        },
                        enabled = !venueState.isSubmitting,
                        modifier = Modifier.testTag(VenueScreenTags.TEMPLATE_CLONE_BUTTON),
                    ) {
                        Text("Клонировать")
                    }
                }
            }
            OutlinedTextField(
                value = templateName,
                onValueChange = { templateName = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(VenueScreenTags.TEMPLATE_NAME_INPUT),
                label = { Text("Название шаблона") },
                singleLine = true,
            )
            SelectorRow(
                items = listOf(
                    HallTemplateStatus.DRAFT.wireName to "Черновик",
                    HallTemplateStatus.PUBLISHED.wireName to "Опубликован",
                ),
                selectedKey = templateStatusKey,
                onSelect = { templateStatusKey = it },
                tagPrefix = VenueScreenTags.TEMPLATE_STATUS_PREFIX,
            )
            OutlinedTextField(
                value = templateStageLabel,
                onValueChange = { templateStageLabel = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(VenueScreenTags.TEMPLATE_STAGE_INPUT),
                label = { Text("Сцена") },
                supportingText = { Text("Например: Main Stage") },
                singleLine = true,
            )
            OutlinedTextField(
                value = templatePriceZones,
                onValueChange = { templatePriceZones = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(VenueScreenTags.TEMPLATE_PRICE_ZONES_INPUT),
                label = { Text("Ценовые зоны") },
                supportingText = { Text("Формат: id|name|defaultPriceMinor") },
                minLines = 2,
            )
            OutlinedTextField(
                value = templateZones,
                onValueChange = { templateZones = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(VenueScreenTags.TEMPLATE_ZONES_INPUT),
                label = { Text("Standing / sector зоны") },
                supportingText = { Text("Формат: id|name|capacity|priceZoneId|kind") },
                minLines = 2,
            )
            OutlinedTextField(
                value = templateRows,
                onValueChange = { templateRows = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(VenueScreenTags.TEMPLATE_ROWS_INPUT),
                label = { Text("Ряды") },
                supportingText = { Text("Формат: rowId|rowLabel|seatCount|priceZoneId") },
                minLines = 2,
            )
            OutlinedTextField(
                value = templateTables,
                onValueChange = { templateTables = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(VenueScreenTags.TEMPLATE_TABLES_INPUT),
                label = { Text("Столы") },
                supportingText = { Text("Формат: tableId|label|seatCount|priceZoneId") },
                minLines = 2,
            )
            OutlinedTextField(
                value = templateServiceAreas,
                onValueChange = { templateServiceAreas = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(VenueScreenTags.TEMPLATE_SERVICE_AREAS_INPUT),
                label = { Text("Служебные зоны") },
                supportingText = { Text("Формат: areaId|name|kind") },
                minLines = 2,
            )
            OutlinedTextField(
                value = templateBlockedSeats,
                onValueChange = { templateBlockedSeats = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(VenueScreenTags.TEMPLATE_BLOCKED_SEATS_INPUT),
                label = { Text("Blocked seats") },
                supportingText = { Text("Список seat ref через запятую или новую строку") },
                minLines = 2,
            )
            Button(
                onClick = {
                    venueBindings.onSaveHallTemplate(
                        HallTemplateEditorForm(
                            venueId = selectedVenue.id,
                            templateId = selectedTemplateId,
                            name = templateName,
                            statusKey = templateStatusKey,
                            stageLabel = templateStageLabel,
                            priceZonesText = templatePriceZones,
                            zonesText = templateZones,
                            rowsText = templateRows,
                            tablesText = templateTables,
                            serviceAreasText = templateServiceAreas,
                            blockedSeatRefsText = templateBlockedSeats,
                        ),
                    )
                },
                enabled = selectedVenue.id.isNotBlank() &&
                    templateName.trim().length in 3..120 &&
                    !venueState.isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(VenueScreenTags.TEMPLATE_SAVE_BUTTON),
            ) {
                Text(if (selectedTemplateId == null) "Создать шаблон" else "Сохранить изменения")
            }
        }
    }
}

/**
 * Баннер ошибки organizer venue surface.
 *
 * @property errorMessage Безопасный текст ошибки.
 * @property onClearError Колбэк очистки ошибки.
 */
@Composable
private fun VenueErrorBanner(
    errorMessage: String,
    onClearError: () -> Unit,
) {
    Surface(
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(VenueScreenTags.ERROR_BANNER),
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
            OutlinedButton(
                onClick = onClearError,
                modifier = Modifier.widthIn(min = 120.dp),
            ) {
                Text("Скрыть")
            }
        }
    }
}

/**
 * Карточка площадки с базовой summary и actions над вложенными шаблонами.
 *
 * @property venue Площадка organizer-а.
 * @property onEditTemplate Колбэк перехода в режим редактирования шаблона.
 * @property onCloneTemplate Колбэк клонирования шаблона.
 */
@Composable
private fun VenueCard(
    venue: OrganizerVenue,
    onEditTemplate: (HallTemplate) -> Unit,
    onCloneTemplate: (HallTemplate) -> Unit,
) {
    Surface(
        tonalElevation = 1.dp,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("${VenueScreenTags.VENUE_CARD_PREFIX}${venue.id}"),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = venue.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "${venue.city} · ${venue.address}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "Вместимость: ${venue.capacity} · ${venue.timezone}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
            venue.description?.takeIf(String::isNotBlank)?.let { description ->
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (venue.hallTemplates.isEmpty()) {
                Text(
                    text = "Шаблонов зала пока нет",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            } else {
                venue.hallTemplates.forEach { template ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = "${template.name} · v${template.version}",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = template.status.wireName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                            Text(
                                text = HallLayoutEditorCodec.summary(template.layout).ifBlank { "Пустой builder layout" },
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedButton(
                                    onClick = { onEditTemplate(template) },
                                    modifier = Modifier.testTag("${VenueScreenTags.TEMPLATE_EDIT_PREFIX}${template.id}"),
                                ) {
                                    Text("Редактировать")
                                }
                                OutlinedButton(
                                    onClick = { onCloneTemplate(template) },
                                    modifier = Modifier.testTag("${VenueScreenTags.TEMPLATE_CLONE_PREFIX}${template.id}"),
                                ) {
                                    Text("Клон")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Универсальная строка выбора значений через компактные outlined chips.
 *
 * @property items Пары `key -> title` для отрисовки кнопок.
 * @property selectedKey Текущее выбранное значение.
 * @property onSelect Колбэк выбора.
 * @property tagPrefix Префикс тестового тега для каждого значения.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectorRow(
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
            val selected = key == selectedKey
            OutlinedButton(
                onClick = { onSelect(key) },
                border = BorderStroke(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                ),
                modifier = Modifier.testTag("$tagPrefix$key"),
            ) {
                Text(title)
            }
        }
    }
}

/**
 * Тестовые теги organizer venue management UI.
 */
object VenueScreenTags {
    /** Тег корневого контейнера вкладки. */
    const val ROOT = "venue.root"

    /** Тег баннера venue-specific ошибки. */
    const val ERROR_BANNER = "venue.error"

    /** Тег индикатора venue loading/submitting. */
    const val LOADING = "venue.loading"

    /** Тег счетчика площадок. */
    const val COUNT = "venue.count"

    /** Тег кнопки ручного refresh списка площадок. */
    const val REFRESH_BUTTON = "venue.refresh"

    /** Тег пустого состояния workspace selector-а. */
    const val WORKSPACE_EMPTY = "venue.workspace.empty"

    /** Префикс кнопок выбора workspace. */
    const val WORKSPACE_SELECTOR_PREFIX = "venue.workspace."

    /** Тег поля названия площадки. */
    const val VENUE_NAME_INPUT = "venue.form.name"

    /** Тег поля города площадки. */
    const val VENUE_CITY_INPUT = "venue.form.city"

    /** Тег поля адреса площадки. */
    const val VENUE_ADDRESS_INPUT = "venue.form.address"

    /** Тег поля timezone площадки. */
    const val VENUE_TIMEZONE_INPUT = "venue.form.timezone"

    /** Тег поля вместимости площадки. */
    const val VENUE_CAPACITY_INPUT = "venue.form.capacity"

    /** Тег поля описания площадки. */
    const val VENUE_DESCRIPTION_INPUT = "venue.form.description"

    /** Тег поля контактов площадки. */
    const val VENUE_CONTACTS_INPUT = "venue.form.contacts"

    /** Тег кнопки создания площадки. */
    const val CREATE_BUTTON = "venue.form.create"

    /** Тег пустого состояния списка площадок. */
    const val VENUE_EMPTY = "venue.list.empty"

    /** Префикс карточек площадок. */
    const val VENUE_CARD_PREFIX = "venue.card."

    /** Префикс кнопок выбора площадки для builder-секции. */
    const val VENUE_SELECTOR_PREFIX = "venue.selector."

    /** Тег пустого состояния builder-секции. */
    const val TEMPLATE_EMPTY = "venue.template.empty"

    /** Тег кнопки сброса template editor-а. */
    const val TEMPLATE_RESET_BUTTON = "venue.template.reset"

    /** Тег кнопки верхнеуровневого clone для выбранного шаблона. */
    const val TEMPLATE_CLONE_BUTTON = "venue.template.clone.selected"

    /** Тег поля названия шаблона. */
    const val TEMPLATE_NAME_INPUT = "venue.template.name"

    /** Префикс кнопок выбора template status-а. */
    const val TEMPLATE_STATUS_PREFIX = "venue.template.status."

    /** Тег поля сцены. */
    const val TEMPLATE_STAGE_INPUT = "venue.template.stage"

    /** Тег поля ценовых зон. */
    const val TEMPLATE_PRICE_ZONES_INPUT = "venue.template.priceZones"

    /** Тег поля standing/sector зон. */
    const val TEMPLATE_ZONES_INPUT = "venue.template.zones"

    /** Тег поля рядов. */
    const val TEMPLATE_ROWS_INPUT = "venue.template.rows"

    /** Тег поля столов. */
    const val TEMPLATE_TABLES_INPUT = "venue.template.tables"

    /** Тег поля служебных зон. */
    const val TEMPLATE_SERVICE_AREAS_INPUT = "venue.template.serviceAreas"

    /** Тег поля blocked seats. */
    const val TEMPLATE_BLOCKED_SEATS_INPUT = "venue.template.blockedSeats"

    /** Тег кнопки сохранения шаблона. */
    const val TEMPLATE_SAVE_BUTTON = "venue.template.save"

    /** Префикс inline-кнопок редактирования шаблона. */
    const val TEMPLATE_EDIT_PREFIX = "venue.template.edit."

    /** Префикс inline-кнопок clone шаблона. */
    const val TEMPLATE_CLONE_PREFIX = "venue.template.clone."
}
