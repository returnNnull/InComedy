package com.bam.incomedy.feature.ticketing.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bam.incomedy.domain.ticketing.IssuedTicket
import com.bam.incomedy.domain.ticketing.IssuedTicketStatus
import com.bam.incomedy.domain.ticketing.TicketCheckInResult
import com.bam.incomedy.domain.ticketing.TicketCheckInResultCode
import com.bam.incomedy.feature.ticketing.TicketingState
import com.bam.incomedy.shared.session.SessionState
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

/**
 * Набор данных и команд, который main shell передает во вкладку audience/staff ticketing.
 *
 * Binding отделяет session shell от ticketing-операций и позволяет тестировать вкладку без
 * прямой зависимости от Android `ViewModel`.
 */
internal data class TicketingTabBindings(
    /** Актуальное состояние ticketing feature. */
    val state: TicketingState = TicketingState(),
    /** Команда ручной перезагрузки списка билетов. */
    val onRefreshTickets: () -> Unit = {},
    /** Команда server-side проверки QR payload. */
    val onScanTicket: (String) -> Unit = {},
    /** Команда очистки верхнеуровневой ticketing-ошибки. */
    val onClearError: () -> Unit = {},
    /** Команда очистки последнего результата проверки QR. */
    val onClearCheckInResult: () -> Unit = {},
)

/**
 * Вкладка audience/staff ticketing внутри авторизованного Android shell.
 *
 * Вкладка объединяет `Мои билеты` для покупателя и операторский staff flow проверки билета по
 * QR payload, который подходит и для внешних keyboard-wedge сканеров.
 *
 * @property sessionState Данные текущей авторизованной сессии.
 * @property ticketingBindings Ticketing-specific state и callbacks.
 * @property modifier Внешний модификатор контейнера.
 */
@Composable
internal fun TicketWalletTab(
    sessionState: SessionState,
    ticketingBindings: TicketingTabBindings,
    modifier: Modifier = Modifier,
) {
    /** Хранит билет, для которого сейчас раскрыт QR-код. */
    var expandedTicketId by rememberSaveable { mutableStateOf<String?>(null) }
    /** Хранит введенный QR payload для staff check-in формы. */
    var scanPayload by rememberSaveable { mutableStateOf("") }

    val ticketingState = ticketingBindings.state
    val canUseCheckIn = sessionState.workspaces.any { workspace ->
        workspace.permissionRole in STAFF_CHECKIN_ROLES
    }

    LaunchedEffect(sessionState.accessToken) {
        if (!sessionState.accessToken.isNullOrBlank()) {
            ticketingBindings.onRefreshTickets()
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(TicketingScreenTags.ROOT),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Билеты",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = "Здесь зритель открывает QR для входа, а персонал проверяет payload через сервер",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        TicketingBanner(
            message = ticketingState.errorMessage,
            tag = TicketingScreenTags.ERROR_BANNER,
            actionLabel = "Скрыть",
            onAction = ticketingBindings.onClearError,
        )

        if (ticketingState.isLoading || ticketingState.isScanning) {
            CircularProgressIndicator(
                modifier = Modifier.testTag(TicketingScreenTags.LOADING),
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Билетов: ${ticketingState.tickets.size}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.testTag(TicketingScreenTags.COUNT),
            )
            OutlinedButton(
                onClick = ticketingBindings.onRefreshTickets,
                enabled = !ticketingState.isLoading && !ticketingState.isScanning,
                modifier = Modifier.testTag(TicketingScreenTags.REFRESH_BUTTON),
            ) {
                Text("Обновить")
            }
        }

        if (ticketingState.tickets.isEmpty() && !ticketingState.isLoading) {
            Surface(
                tonalElevation = 1.dp,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TicketingScreenTags.EMPTY_STATE),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Пока нет выданных билетов",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "После успешной оплаты билет появится здесь вместе с QR для входа",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        } else {
            ticketingState.tickets.forEach { ticket ->
                TicketCard(
                    ticket = ticket,
                    isExpanded = expandedTicketId == ticket.id,
                    onToggleQr = {
                        expandedTicketId = if (expandedTicketId == ticket.id) null else ticket.id
                    },
                )
            }
        }

        HorizontalDivider()

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TicketingScreenTags.STAFF_SECTION),
        ) {
            Text(
                text = "Проверка на входе",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = if (canUseCheckIn) {
                    "Подходит для внешних сканеров, которые вставляют QR payload как обычный текст"
                } else {
                    "Сканирование доступно сотрудникам с ролями owner, manager или checker"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = scanPayload,
                onValueChange = { scanPayload = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TicketingScreenTags.SCAN_INPUT),
                enabled = canUseCheckIn && !ticketingState.isScanning,
                label = { Text("QR payload") },
                placeholder = { Text("Вставьте payload из сканера или буфера") },
                minLines = 3,
            )
            Button(
                onClick = { ticketingBindings.onScanTicket(scanPayload) },
                enabled = canUseCheckIn && scanPayload.trim().isNotEmpty() && !ticketingState.isScanning,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TicketingScreenTags.SCAN_BUTTON),
            ) {
                Text("Проверить билет")
            }

            ticketingState.lastCheckInResult?.let { result ->
                CheckInResultCard(
                    result = result,
                    onDismiss = ticketingBindings.onClearCheckInResult,
                )
            }
        }
    }
}

/**
 * Унифицированный баннер ошибки/результата для ticketing surface.
 *
 * @property message Текст баннера.
 * @property tag UI-тег контейнера.
 * @property actionLabel Подпись кнопки скрытия.
 * @property onAction Команда кнопки действия.
 */
@Composable
private fun TicketingBanner(
    message: String?,
    tag: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    if (message == null) return

    Surface(
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            OutlinedButton(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}

/**
 * Карточка одного билета внутри `Мои билеты`.
 *
 * @property ticket Билет текущего пользователя.
 * @property isExpanded Показывает, раскрыт ли QR-блок.
 * @property onToggleQr Команда раскрытия или скрытия QR-блока.
 */
@Composable
private fun TicketCard(
    ticket: IssuedTicket,
    isExpanded: Boolean,
    onToggleQr: () -> Unit,
) {
    Surface(
        tonalElevation = 1.dp,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("${TicketingScreenTags.TICKET_CARD_PREFIX}${ticket.id}"),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = ticket.label,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Событие: ${ticket.eventId}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "Позиция: ${ticket.inventoryRef}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "Статус: ${ticketStatusTitle(ticket.status)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.testTag("${TicketingScreenTags.TICKET_STATUS_PREFIX}${ticket.id}"),
            )
            ticket.checkedInAtIso?.let { checkedInAt ->
                Text(
                    text = "Отмечен на входе: $checkedInAt",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (ticket.qrPayload != null) {
                OutlinedButton(
                    onClick = onToggleQr,
                    modifier = Modifier.testTag("${TicketingScreenTags.QR_TOGGLE_PREFIX}${ticket.id}"),
                ) {
                    Text(if (isExpanded) "Скрыть QR" else "Показать QR")
                }
            }
            if (isExpanded && ticket.qrPayload != null) {
                TicketQrBlock(ticket = ticket)
            }
        }
    }
}

/**
 * Блок визуального QR-кода для билета.
 *
 * @property ticket Билет, для которого нужно построить QR.
 */
@Composable
private fun TicketQrBlock(
    ticket: IssuedTicket,
) {
    val qrBitmap = rememberQrBitmap(ticket.qrPayload)

    Surface(
        tonalElevation = 1.dp,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("${TicketingScreenTags.QR_BLOCK_PREFIX}${ticket.id}"),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (qrBitmap != null) {
                Image(
                    bitmap = qrBitmap,
                    contentDescription = "QR билета ${ticket.label}",
                    modifier = Modifier.size(220.dp),
                )
            } else {
                Text(
                    text = "Не удалось построить QR для этого билета",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                text = ticket.qrPayload.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("${TicketingScreenTags.QR_PAYLOAD_PREFIX}${ticket.id}"),
            )
        }
    }
}

/**
 * Карточка результата staff check-in.
 *
 * @property result Результат server-side проверки QR.
 * @property onDismiss Команда скрытия результата.
 */
@Composable
private fun CheckInResultCard(
    result: TicketCheckInResult,
    onDismiss: () -> Unit,
) {
    Surface(
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TicketingScreenTags.SCAN_RESULT),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = scanResultTitle(result.resultCode),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.testTag(TicketingScreenTags.SCAN_RESULT_CODE),
            )
            Text(
                text = result.ticket.label,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = "Событие: ${result.ticket.eventId}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "Статус билета: ${ticketStatusTitle(result.ticket.status)}",
                style = MaterialTheme.typography.bodyMedium,
            )
            result.ticket.checkedInAtIso?.let { checkedInAt ->
                Text(
                    text = "Время отметки: $checkedInAt",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            OutlinedButton(onClick = onDismiss) {
                Text("Скрыть результат")
            }
        }
    }
}

/**
 * Строит `ImageBitmap` QR-кода из непрозрачного payload-а.
 *
 * @property qrPayload Непрозрачная строка, которую нужно закодировать в QR.
 */
@Composable
private fun rememberQrBitmap(
    qrPayload: String?,
): ImageBitmap? {
    return remember(qrPayload) {
        qrPayload
            ?.takeIf(String::isNotBlank)
            ?.let(::generateQrBitmap)
            ?.asImageBitmap()
    }
}

/**
 * Генерирует bitmap QR-кода для Android-представления билета.
 *
 * @param qrPayload Непрозрачная строка билета.
 * @return `Bitmap`, если код удалось построить.
 */
private fun generateQrBitmap(
    qrPayload: String,
): Bitmap? {
    return runCatching {
        val size = 768
        val matrix = QRCodeWriter().encode(
            qrPayload,
            BarcodeFormat.QR_CODE,
            size,
            size,
        )
        Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
            for (x in 0 until size) {
                for (y in 0 until size) {
                    setPixel(
                        x,
                        y,
                        if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE,
                    )
                }
            }
        }
    }.getOrNull()
}

/**
 * Возвращает человекочитаемую подпись статуса билета.
 */
private fun ticketStatusTitle(status: IssuedTicketStatus): String {
    return when (status) {
        IssuedTicketStatus.ISSUED -> "Действителен"
        IssuedTicketStatus.CHECKED_IN -> "Уже использован"
        IssuedTicketStatus.CANCELED -> "Аннулирован"
    }
}

/**
 * Возвращает человекочитаемую подпись результата staff check-in.
 */
private fun scanResultTitle(resultCode: TicketCheckInResultCode): String {
    return when (resultCode) {
        TicketCheckInResultCode.CHECKED_IN -> "Билет подтвержден"
        TicketCheckInResultCode.DUPLICATE -> "Повторное сканирование"
    }
}

/** Роли staff-поверхности, которым доступен check-in flow. */
private val STAFF_CHECKIN_ROLES = setOf("owner", "manager", "checker")

/**
 * Набор тегов, по которым UI-тесты находят ключевые элементы ticketing-вкладки.
 */
object TicketingScreenTags {
    /** Тег корневого контейнера ticketing-вкладки. */
    const val ROOT = "ticketing.root"

    /** Тег индикатора загрузки списка/сканирования. */
    const val LOADING = "ticketing.loading"

    /** Тег баннера ошибки. */
    const val ERROR_BANNER = "ticketing.error"

    /** Тег счетчика билетов. */
    const val COUNT = "ticketing.count"

    /** Тег кнопки ручного обновления. */
    const val REFRESH_BUTTON = "ticketing.refresh"

    /** Тег пустого состояния `Мои билеты`. */
    const val EMPTY_STATE = "ticketing.empty"

    /** Тег staff-секции проверки билета. */
    const val STAFF_SECTION = "ticketing.staff"

    /** Тег поля ввода QR payload. */
    const val SCAN_INPUT = "ticketing.scan.input"

    /** Тег кнопки проверки QR payload. */
    const val SCAN_BUTTON = "ticketing.scan.button"

    /** Тег карточки результата staff check-in. */
    const val SCAN_RESULT = "ticketing.scan.result"

    /** Тег текста с кодом результата staff check-in. */
    const val SCAN_RESULT_CODE = "ticketing.scan.resultCode"

    /** Префикс тега карточки конкретного билета. */
    const val TICKET_CARD_PREFIX = "ticketing.ticket."

    /** Префикс тега статуса конкретного билета. */
    const val TICKET_STATUS_PREFIX = "ticketing.ticket.status."

    /** Префикс тега кнопки раскрытия QR. */
    const val QR_TOGGLE_PREFIX = "ticketing.ticket.qr.toggle."

    /** Префикс тега блока QR конкретного билета. */
    const val QR_BLOCK_PREFIX = "ticketing.ticket.qr.block."

    /** Префикс тега текстового payload конкретного билета. */
    const val QR_PAYLOAD_PREFIX = "ticketing.ticket.qr.payload."
}
