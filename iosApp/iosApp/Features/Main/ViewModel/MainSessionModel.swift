import Foundation
import Security
import Shared

/// Модель главного SwiftUI-экрана, которая адаптирует общий `SessionBridge` для iOS UI.
final class MainSessionModel: ObservableObject {
    /// Показывает, авторизован ли пользователь в текущий момент.
    @Published var isAuthorized: Bool = false

    /// Хранит отображаемое имя профиля.
    @Published var displayName: String = "Сессия активна"

    /// Хранит username профиля, если он известен.
    @Published var username: String?

    /// Хранит внутренний идентификатор пользователя.
    @Published var userId: String?

    /// Хранит URL фотографии профиля.
    @Published var photoURL: String?

    /// Хранит ключ основного способа входа.
    @Published var providerKey: String?

    /// Хранит доступные роли пользователя.
    @Published var roles: [String] = []

    /// Хранит текущую активную роль.
    @Published var activeRoleKey: String?

    /// Хранит ключи всех привязанных способов входа.
    @Published var linkedProviderKeys: [String] = []

    /// Хранит список доступных рабочих пространств.
    @Published var workspaces: [MainWorkspaceItem] = []

    /// Хранит pending invitations текущего пользователя.
    @Published var workspaceInvitations: [MainWorkspaceInvitationItem] = []

    /// Показывает, что загружается контекст главного экрана.
    @Published var isLoadingContext: Bool = false

    /// Показывает, что сейчас идет смена активной роли.
    @Published var isUpdatingRole: Bool = false

    /// Показывает, что сейчас создается рабочее пространство.
    @Published var isCreatingWorkspace: Bool = false

    /// Показывает, что сейчас выполняется membership mutation внутри workspace.
    @Published var isManagingWorkspaceMembers: Bool = false

    /// Хранит текст последней ошибки экрана.
    @Published var errorMessage: String?

    /// Удерживает мост к общей модели сессии.
    private let bridge: SessionBridge?

    /// Хранит активную подписку на состояние моста.
    private var bindingHandle: NSObject?

    /// Хранилище токена доступа для очистки при выходе.
    private let tokenStore = AuthTokenKeychainStore()

    /// Создает модель главного экрана и сразу подписывается на общее состояние.
    ///
    /// - Parameter bridge: Необязательный мост для внедрения в тестах и превью.
    init(bridge: SessionBridge? = nil) {
        self.bridge = bridge ?? SessionBridge(
            viewModel: InComedyKoin.shared.getSessionViewModel()
        )
        bind()
    }

    /// Создает модель главного экрана на основе детерминированного fixture-состояния.
    ///
    /// - Parameter fixture: Фикстура авторизованного состояния для тестов и превью.
    init(fixture: MainSessionFixture) {
        self.bridge = nil
        apply(fixture: fixture)
    }

    deinit {
        disposeBindingIfNeeded()
        bridge?.dispose()
    }

    /// Выполняет выход пользователя и очищает локальный токен.
    func signOut() {
        tokenStore.deleteToken()
        if let bridge {
            bridge.signOut()
        } else {
            isAuthorized = false
        }
    }

    /// Переключает активную роль через общий слой сессии.
    ///
    /// - Parameter role: Код роли, которую нужно сделать активной.
    func setActiveRole(_ role: String) {
        if let bridge {
            bridge.setActiveRole(role: role)
        } else {
            activeRoleKey = role
        }
    }

    /// Создает рабочее пространство через общий слой сессии.
    ///
    /// - Parameters:
    ///   - name: Название рабочего пространства.
    ///   - slug: Необязательный slug рабочего пространства.
    func createWorkspace(name: String, slug: String?) {
        let normalizedName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        let normalizedSlug = slug?.trimmingCharacters(in: .whitespacesAndNewlines)
        let resolvedSlug = normalizedSlug?.isEmpty == true ? nil : normalizedSlug
        if let bridge {
            bridge.createWorkspace(
                name: normalizedName,
                slug: resolvedSlug
            )
        } else {
            let slugValue = resolvedSlug ?? normalizedName
                .lowercased()
                .replacingOccurrences(of: " ", with: "-")
            workspaces.append(
                MainWorkspaceItem(
                    id: slugValue,
                    name: normalizedName,
                    slug: slugValue,
                    status: "active",
                    permissionRole: "owner",
                    canManageMembers: true,
                    assignablePermissionRoles: ["manager", "checker", "host"],
                    memberships: [
                        MainWorkspaceMembershipItem(
                            membershipId: "membership-\(slugValue)",
                            userId: "user-1",
                            displayName: displayName,
                            username: username,
                            permissionRole: "owner",
                            status: "active",
                            invitedByDisplayName: nil,
                            isCurrentUser: true,
                            canEditRole: false,
                            assignablePermissionRoles: []
                        )
                    ]
                )
            )
        }
    }

    /// Создает invitation существующему пользователю внутри workspace.
    ///
    /// - Parameters:
    ///   - workspaceId: Идентификатор рабочего пространства.
    ///   - inviteeIdentifier: Логин или username целевого пользователя.
    ///   - permissionRole: Назначаемая permission role.
    func createWorkspaceInvitation(
        workspaceId: String,
        inviteeIdentifier: String,
        permissionRole: String
    ) {
        let normalizedIdentifier = inviteeIdentifier.trimmingCharacters(in: .whitespacesAndNewlines)
        if let bridge {
            bridge.createWorkspaceInvitation(
                workspaceId: workspaceId,
                inviteeIdentifier: normalizedIdentifier,
                permissionRole: permissionRole
            )
        } else {
            guard let index = workspaces.firstIndex(where: { $0.id == workspaceId }) else { return }
            let invitedMembership = MainWorkspaceMembershipItem(
                membershipId: "invite-\(workspaceId)-\(normalizedIdentifier)",
                userId: "user-invited",
                displayName: normalizedIdentifier,
                username: normalizedIdentifier,
                permissionRole: permissionRole,
                status: "invited",
                invitedByDisplayName: displayName,
                isCurrentUser: false,
                canEditRole: true,
                assignablePermissionRoles: workspaces[index].assignablePermissionRoles
            )
            workspaces[index].memberships.append(invitedMembership)
        }
    }

    /// Принимает pending invitation текущего пользователя.
    ///
    /// - Parameter membershipId: Идентификатор invitation записи.
    func acceptWorkspaceInvitation(_ membershipId: String) {
        if let bridge {
            bridge.acceptWorkspaceInvitation(membershipId: membershipId)
        } else {
            workspaceInvitations.removeAll { $0.membershipId == membershipId }
        }
    }

    /// Отклоняет pending invitation текущего пользователя.
    ///
    /// - Parameter membershipId: Идентификатор invitation записи.
    func declineWorkspaceInvitation(_ membershipId: String) {
        if let bridge {
            bridge.declineWorkspaceInvitation(membershipId: membershipId)
        } else {
            workspaceInvitations.removeAll { $0.membershipId == membershipId }
        }
    }

    /// Меняет permission role membership внутри workspace.
    ///
    /// - Parameters:
    ///   - workspaceId: Идентификатор рабочего пространства.
    ///   - membershipId: Идентификатор membership записи.
    ///   - permissionRole: Новая permission role.
    func updateWorkspaceMembershipRole(
        workspaceId: String,
        membershipId: String,
        permissionRole: String
    ) {
        if let bridge {
            bridge.updateWorkspaceMembershipRole(
                workspaceId: workspaceId,
                membershipId: membershipId,
                permissionRole: permissionRole
            )
        } else {
            guard let workspaceIndex = workspaces.firstIndex(where: { $0.id == workspaceId }) else { return }
            guard let membershipIndex = workspaces[workspaceIndex].memberships.firstIndex(where: { $0.membershipId == membershipId }) else { return }
            workspaces[workspaceIndex].memberships[membershipIndex].permissionRole = permissionRole
        }
    }

    /// Скрывает текущую ошибку главного экрана.
    func clearError() {
        if let bridge {
            bridge.clearError()
        } else {
            errorMessage = nil
        }
    }

    /// Подписывается на изменения общего состояния сессии и маппит их в SwiftUI-поля.
    private func bind() {
        guard let bridge else { return }
        setBinding(
            bridge.observeState { [weak self] snapshot in
                guard let self else { return }
                Task { @MainActor in
                    self.apply(snapshot: snapshot)
                }
            }
        )
    }

    /// Применяет bridge-снимок к опубликованным полям SwiftUI-модели.
    ///
    /// - Parameter snapshot: Снимок общего состояния сессии из KMP-моста.
    private func apply(snapshot: SessionStateSnapshot) {
        isAuthorized = snapshot.isAuthorized
        providerKey = snapshot.providerKey
        displayName = snapshot.displayName ?? "Сессия активна"
        username = snapshot.username
        userId = snapshot.userId
        photoURL = snapshot.photoUrl
        roles = snapshot.roles
        activeRoleKey = snapshot.activeRole
        linkedProviderKeys = snapshot.linkedProviders
        workspaces = snapshot.workspaces.map {
            MainWorkspaceItem(
                id: $0.id,
                name: $0.name,
                slug: $0.slug,
                status: $0.status,
                permissionRole: $0.permissionRole,
                canManageMembers: $0.canManageMembers,
                assignablePermissionRoles: $0.assignablePermissionRoles,
                memberships: $0.memberships.map {
                    MainWorkspaceMembershipItem(
                        membershipId: $0.membershipId,
                        userId: $0.userId,
                        displayName: $0.displayName,
                        username: $0.username,
                        permissionRole: $0.permissionRole,
                        status: $0.status,
                        invitedByDisplayName: $0.invitedByDisplayName,
                        isCurrentUser: $0.isCurrentUser,
                        canEditRole: $0.canEditRole,
                        assignablePermissionRoles: $0.assignablePermissionRoles
                    )
                }
            )
        }
        workspaceInvitations = snapshot.workspaceInvitations.map {
            MainWorkspaceInvitationItem(
                membershipId: $0.membershipId,
                workspaceId: $0.workspaceId,
                workspaceName: $0.workspaceName,
                workspaceSlug: $0.workspaceSlug,
                workspaceStatus: $0.workspaceStatus,
                permissionRole: $0.permissionRole,
                invitedByDisplayName: $0.invitedByDisplayName
            )
        }
        isLoadingContext = snapshot.isLoadingContext
        isUpdatingRole = snapshot.isUpdatingRole
        isCreatingWorkspace = snapshot.isCreatingWorkspace
        isManagingWorkspaceMembers = snapshot.isManagingWorkspaceMembers
        errorMessage = snapshot.errorMessage
    }

    /// Применяет фикстуру, когда экран запускается в тестовом или preview-режиме.
    ///
    /// - Parameter fixture: Детерминированное состояние главного экрана.
    private func apply(fixture: MainSessionFixture) {
        isAuthorized = fixture.isAuthorized
        displayName = fixture.displayName
        username = fixture.username
        userId = fixture.userId
        photoURL = fixture.photoURL
        providerKey = fixture.providerKey
        roles = fixture.roles
        activeRoleKey = fixture.activeRoleKey
        linkedProviderKeys = fixture.linkedProviderKeys
        workspaces = fixture.workspaces
        workspaceInvitations = fixture.workspaceInvitations
        isLoadingContext = fixture.isLoadingContext
        isUpdatingRole = fixture.isUpdatingRole
        isCreatingWorkspace = fixture.isCreatingWorkspace
        isManagingWorkspaceMembers = fixture.isManagingWorkspaceMembers
        errorMessage = fixture.errorMessage
    }

    /// Сохраняет новую подписку на состояние и очищает предыдущую.
    ///
    /// - Parameter handle: Дескриптор активной подписки на общее состояние.
    private func setBinding(_ handle: Any) {
        disposeBindingIfNeeded()
        bindingHandle = handle as? NSObject
    }

    /// Освобождает текущую подписку на мост при смене подписки или деинициализации.
    private func disposeBindingIfNeeded() {
        guard let bindingHandle else { return }
        let disposeSelector = NSSelectorFromString("dispose")
        if bindingHandle.responds(to: disposeSelector) {
            _ = bindingHandle.perform(disposeSelector)
        }
        self.bindingHandle = nil
    }
}

/// Детерминированное состояние главного экрана для превью и UI-тестов.
struct MainSessionFixture {
    /// Показывает, что пользователь находится в авторизованной зоне.
    let isAuthorized: Bool

    /// Отображаемое имя пользователя.
    let displayName: String

    /// Username профиля.
    let username: String?

    /// Внутренний идентификатор пользователя.
    let userId: String?

    /// URL фотографии профиля.
    let photoURL: String?

    /// Ключ базового провайдера авторизации.
    let providerKey: String?

    /// Все роли пользователя.
    let roles: [String]

    /// Активная роль пользователя.
    let activeRoleKey: String?

    /// Привязанные провайдеры авторизации.
    let linkedProviderKeys: [String]

    /// Рабочие пространства, видимые на главной вкладке.
    let workspaces: [MainWorkspaceItem]

    /// Pending invitations текущего пользователя.
    let workspaceInvitations: [MainWorkspaceInvitationItem]

    /// Показывает индикатор загрузки контекста.
    let isLoadingContext: Bool

    /// Показывает, что выполняется смена роли.
    let isUpdatingRole: Bool

    /// Показывает, что выполняется создание рабочего пространства.
    let isCreatingWorkspace: Bool

    /// Показывает, что выполняется membership mutation внутри workspace.
    let isManagingWorkspaceMembers: Bool

    /// Последняя ошибка экрана.
    let errorMessage: String?

    /// Готовая фикстура авторизованного главного экрана для UI-тестов.
    static let uiTestMain = MainSessionFixture(
        isAuthorized: true,
        displayName: "Тестовый Пользователь",
        username: "test_user",
        userId: "user-1",
        photoURL: nil,
        providerKey: "password",
        roles: ["audience", "organizer", "comedian"],
        activeRoleKey: "audience",
        linkedProviderKeys: ["password"],
        workspaces: [
            MainWorkspaceItem(
                id: "ws-1",
                name: "Moscow Cellar",
                slug: "moscow-cellar",
                status: "active",
                permissionRole: "owner",
                canManageMembers: true,
                assignablePermissionRoles: ["manager", "checker", "host"],
                memberships: [
                    MainWorkspaceMembershipItem(
                        membershipId: "wm-1",
                        userId: "user-1",
                        displayName: "Тестовый Пользователь",
                        username: "test_user",
                        permissionRole: "owner",
                        status: "active",
                        invitedByDisplayName: nil,
                        isCurrentUser: true,
                        canEditRole: false,
                        assignablePermissionRoles: []
                    ),
                    MainWorkspaceMembershipItem(
                        membershipId: "wm-2",
                        userId: "user-2",
                        displayName: "Сценический Чекер",
                        username: "checker_user",
                        permissionRole: "checker",
                        status: "active",
                        invitedByDisplayName: nil,
                        isCurrentUser: false,
                        canEditRole: true,
                        assignablePermissionRoles: ["manager", "checker", "host"]
                    )
                ]
            )
        ],
        workspaceInvitations: [
            MainWorkspaceInvitationItem(
                membershipId: "invite-1",
                workspaceId: "ws-2",
                workspaceName: "Late Night Standup",
                workspaceSlug: "late-night-standup",
                workspaceStatus: "active",
                permissionRole: "checker",
                invitedByDisplayName: "Владелец Площадки"
            )
        ],
        isLoadingContext: false,
        isUpdatingRole: false,
        isCreatingWorkspace: false,
        isManagingWorkspaceMembers: false,
        errorMessage: nil
    )
}

/// Модель рабочего пространства для SwiftUI-слоя.
struct MainWorkspaceItem: Identifiable {
    /// Уникальный идентификатор рабочего пространства.
    let id: String

    /// Название рабочего пространства.
    let name: String

    /// Публичный slug рабочего пространства.
    let slug: String

    /// Текущий статус рабочего пространства.
    let status: String

    /// Роль доступа пользователя в рабочем пространстве.
    let permissionRole: String

    /// Показывает, может ли viewer управлять командой workspace.
    let canManageMembers: Bool

    /// Роли, которые viewer может назначать в invite flow.
    let assignablePermissionRoles: [String]

    /// Active и pending membership записи workspace.
    var memberships: [MainWorkspaceMembershipItem]
}

/// Модель membership записи внутри workspace для SwiftUI-слоя.
struct MainWorkspaceMembershipItem: Identifiable {
    /// Идентификатор membership/invitation записи.
    let membershipId: String

    /// Идентификатор пользователя.
    let userId: String

    /// Отображаемое имя участника.
    let displayName: String

    /// Username участника.
    let username: String?

    /// Permission role внутри workspace.
    var permissionRole: String

    /// Состояние membership.
    let status: String

    /// Имя инициатора invite.
    let invitedByDisplayName: String?

    /// Показывает, что membership принадлежит текущему пользователю.
    let isCurrentUser: Bool

    /// Показывает, что role этой записи можно менять.
    let canEditRole: Bool

    /// Роли, которые можно назначить этой записи.
    let assignablePermissionRoles: [String]

    /// Использует membership id как stable `Identifiable` id.
    var id: String { membershipId }
}

/// Pending invitation текущего пользователя для SwiftUI-слоя.
struct MainWorkspaceInvitationItem: Identifiable {
    /// Идентификатор invitation записи.
    let membershipId: String

    /// Идентификатор рабочего пространства.
    let workspaceId: String

    /// Название рабочего пространства.
    let workspaceName: String

    /// Публичный slug рабочего пространства.
    let workspaceSlug: String

    /// Статус рабочего пространства.
    let workspaceStatus: String

    /// Роль, которая предлагается пользователю.
    let permissionRole: String

    /// Имя инициатора invitation.
    let invitedByDisplayName: String?

    /// Использует membership id как stable `Identifiable` id.
    var id: String { membershipId }
}

/// Вспомогательное Keychain-хранилище, которое удаляет access token при выходе.
private final class AuthTokenKeychainStore {
    /// Идентификатор Keychain-сервиса.
    private let service: String

    /// Имя записи access token в Keychain.
    private let account: String

    /// Создает Keychain-хранилище для access token.
    ///
    /// - Parameters:
    ///   - service: Идентификатор Keychain-сервиса.
    ///   - account: Имя записи токена.
    init(
        service: String = Bundle.main.bundleIdentifier ?? "com.bam.incomedy",
        account: String = "auth.access_token"
    ) {
        self.service = service
        self.account = account
    }

    /// Удаляет токен доступа из Keychain.
    func deleteToken() {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account
        ]
        SecItemDelete(query as CFDictionary)
    }
}
