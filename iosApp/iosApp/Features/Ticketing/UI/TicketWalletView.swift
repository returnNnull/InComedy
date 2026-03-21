import CoreImage
import CoreImage.CIFilterBuiltins
import SwiftUI
import UIKit

/// SwiftUI-экран audience/staff ticketing внутри авторизованного tab shell.
struct TicketWalletView: View {
    /// Модель ticketing feature.
    @ObservedObject var model: TicketWalletModel

    /// Organizer workspaces, доступные текущему пользователю.
    let workspaces: [MainWorkspaceItem]

    /// Идентификатор билета, для которого сейчас раскрыт QR-блок.
    @State private var expandedTicketId: String?

    /// Введенный QR payload для staff check-in формы.
    @State private var scanPayload: String = ""

    /// Показывает, что пользователь имеет staff-доступ к check-in surface.
    private var canUseCheckIn: Bool {
        workspaces.contains { ["owner", "manager", "checker"].contains($0.permissionRole) }
    }

    /// Отрисовывает ticketing surface со списком билетов и проверкой QR.
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text("Билеты")
                    .font(.title3.bold())
                    .accessibilityIdentifier("ticketing.root")
                Text("Здесь зритель показывает QR, а персонал проверяет payload через сервер")
                    .foregroundColor(.secondary)

                if let errorMessage = model.errorMessage {
                    TicketingBanner(
                        message: errorMessage,
                        buttonTitle: "Скрыть",
                        onDismiss: model.clearError
                    )
                    .accessibilityIdentifier("ticketing.error")
                }

                if model.isLoading || model.isScanning {
                    ProgressView()
                        .accessibilityIdentifier("ticketing.loading")
                }

                HStack(spacing: 12) {
                    Text("Билетов: \(model.tickets.count)")
                        .foregroundColor(.secondary)
                        .accessibilityIdentifier("ticketing.count")
                    Button("Обновить") {
                        model.refresh()
                    }
                    .buttonStyle(.bordered)
                    .disabled(model.isLoading || model.isScanning)
                    .accessibilityIdentifier("ticketing.refresh")
                }

                if model.tickets.isEmpty && !model.isLoading {
                    TicketEmptyState()
                        .accessibilityIdentifier("ticketing.empty")
                } else {
                    ForEach(model.tickets) { ticket in
                        TicketCardView(
                            ticket: ticket,
                            isExpanded: expandedTicketId == ticket.id,
                            onToggleQr: {
                                expandedTicketId = expandedTicketId == ticket.id ? nil : ticket.id
                            }
                        )
                    }
                }

                Divider()

                VStack(alignment: .leading, spacing: 8) {
                    Text("Проверка на входе")
                        .font(.headline)
                    Text(
                        canUseCheckIn
                        ? "Подходит для внешних сканеров, которые вставляют QR payload как обычный текст"
                        : "Сканирование доступно сотрудникам с ролями owner, manager или checker"
                    )
                    .foregroundColor(.secondary)

                    TextField("QR payload", text: $scanPayload, axis: .vertical)
                        .textFieldStyle(.roundedBorder)
                        .lineLimit(3...5)
                        .disabled(!canUseCheckIn || model.isScanning)
                        .accessibilityIdentifier("ticketing.scan.input")

                    Button("Проверить билет") {
                        model.scanTicket(qrPayload: scanPayload)
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(!canUseCheckIn || scanPayload.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || model.isScanning)
                    .accessibilityIdentifier("ticketing.scan.button")

                    if let lastCheckInResult = model.lastCheckInResult {
                        CheckInResultView(
                            result: lastCheckInResult,
                            onDismiss: model.clearCheckInResult
                        )
                    }
                }
            }
            .padding()
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }
}

/// Пустое состояние вкладки `Мои билеты`.
private struct TicketEmptyState: View {
    /// Отрисовывает пустое состояние без выданных билетов.
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Пока нет выданных билетов")
                .font(.headline)
            Text("После успешной оплаты билет появится здесь вместе с QR для входа")
                .foregroundColor(.secondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        .background(Color(.secondarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }
}

/// Карточка одного билета внутри `Мои билеты`.
private struct TicketCardView: View {
    /// Билет текущего пользователя.
    let ticket: TicketItem

    /// Показывает, раскрыт ли QR-блок.
    let isExpanded: Bool

    /// Команда раскрытия или скрытия QR-блока.
    let onToggleQr: () -> Void

    /// Отрисовывает карточку билета и при необходимости QR-блок.
    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(ticket.label)
                .font(.headline)
            Text("Событие: \(ticket.eventId)")
            Text("Позиция: \(ticket.inventoryRef)")
            Text("Статус: \(ticketStatusTitle(ticket.statusKey))")
                .fontWeight(.semibold)
                .accessibilityIdentifier("ticketing.ticket.status.\(ticket.id)")
            if let checkedInAtIso = ticket.checkedInAtIso {
                Text("Отмечен на входе: \(checkedInAtIso)")
                    .font(.footnote)
                    .foregroundColor(.secondary)
            }
            if ticket.qrPayload != nil {
                Button(isExpanded ? "Скрыть QR" : "Показать QR") {
                    onToggleQr()
                }
                .buttonStyle(.bordered)
                .accessibilityIdentifier("ticketing.ticket.qr.toggle.\(ticket.id)")
            }
            if isExpanded, let qrPayload = ticket.qrPayload {
                TicketQrBlock(
                    qrPayload: qrPayload,
                    accessibilitySuffix: ticket.id
                )
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        .background(Color(.secondarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }
}

/// Визуальный блок QR-кода билета.
private struct TicketQrBlock: View {
    /// Непрозрачный payload билета.
    let qrPayload: String

    /// Суффикс accessibility id конкретного билета.
    let accessibilitySuffix: String

    /// Контекст CoreImage для построения CGImage.
    private let context = CIContext()

    /// Фильтр системного генератора QR-кода.
    private let filter = CIFilter.qrCodeGenerator()

    /// Отрисовывает QR и исходный payload в текстовом виде.
    var body: some View {
        VStack(spacing: 8) {
            if let uiImage = qrImage(from: qrPayload) {
                Image(uiImage: uiImage)
                    .interpolation(.none)
                    .resizable()
                    .scaledToFit()
                    .frame(width: 220, height: 220)
            } else {
                Text("Не удалось построить QR для этого билета")
            }
            Text(qrPayload)
                .font(.footnote)
                .textSelection(.enabled)
                .accessibilityIdentifier("ticketing.ticket.qr.payload.\(accessibilitySuffix)")
        }
        .frame(maxWidth: .infinity)
        .padding()
        .background(Color.white)
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .accessibilityIdentifier("ticketing.ticket.qr.block.\(accessibilitySuffix)")
    }

    /// Строит изображение QR из непрозрачного payload-а билета.
    ///
    /// - Parameter payload: Непрозрачная строка билета.
    /// - Returns: `UIImage`, если CoreImage смог построить QR.
    private func qrImage(from payload: String) -> UIImage? {
        filter.message = Data(payload.utf8)
        filter.correctionLevel = "M"
        guard let outputImage = filter.outputImage?.transformed(by: CGAffineTransform(scaleX: 10, y: 10)) else {
            return nil
        }
        guard let cgImage = context.createCGImage(outputImage, from: outputImage.extent) else {
            return nil
        }
        return UIImage(cgImage: cgImage)
    }
}

/// Карточка результата staff check-in.
private struct CheckInResultView: View {
    /// Результат последней server-side проверки QR.
    let result: TicketCheckInOutcomeItem

    /// Команда скрытия результата.
    let onDismiss: () -> Void

    /// Отрисовывает карточку результата staff check-in.
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(scanResultTitle(result.resultCodeKey))
                .font(.headline)
                .accessibilityIdentifier("ticketing.scan.resultCode")
            Text(result.ticket.label)
            Text("Событие: \(result.ticket.eventId)")
            Text("Статус билета: \(ticketStatusTitle(result.ticket.statusKey))")
            if let checkedInAtIso = result.ticket.checkedInAtIso {
                Text("Время отметки: \(checkedInAtIso)")
                    .font(.footnote)
                    .foregroundColor(.secondary)
            }
            Button("Скрыть результат") {
                onDismiss()
            }
            .buttonStyle(.bordered)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        .background(Color(.tertiarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }
}

/// Унифицированный баннер ticketing-ошибки.
private struct TicketingBanner: View {
    /// Текст баннера.
    let message: String

    /// Подпись кнопки скрытия.
    let buttonTitle: String

    /// Команда скрытия баннера.
    let onDismiss: () -> Void

    /// Отрисовывает баннер ошибки и кнопку скрытия.
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(message)
            Button(buttonTitle) {
                onDismiss()
            }
            .buttonStyle(.bordered)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        .background(Color(.secondarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }
}

/// Возвращает человекочитаемую подпись статуса билета.
private func ticketStatusTitle(_ statusKey: String) -> String {
    switch statusKey {
    case "issued":
        "Действителен"
    case "checked_in":
        "Уже использован"
    case "canceled":
        "Аннулирован"
    default:
        statusKey
    }
}

/// Возвращает человекочитаемую подпись результата staff check-in.
private func scanResultTitle(_ resultCodeKey: String) -> String {
    switch resultCodeKey {
    case "checked_in":
        "Билет подтвержден"
    case "duplicate":
        "Повторное сканирование"
    default:
        resultCodeKey
    }
}
