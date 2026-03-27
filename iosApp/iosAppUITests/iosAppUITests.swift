import XCTest

/// UI-тесты главного экрана после авторизации.
final class iosAppUITests: XCTestCase {
    /// Экземпляр приложения, запускаемый в режиме тестовой фикстуры.
    private var app: XCUIApplication!

    /// Поднимает приложение сразу в авторизованном главном графе.
    override func setUpWithError() throws {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launchArguments = ["--ui-test-main"]
        app.launch()
    }

    /// Проверяет домашнюю вкладку и отображение рабочих пространств.
    func testHomeTabShowsWorkspaceSummary() {
        let invitationAcceptButton = app.buttons["Принять"]
        XCTAssertTrue(app.otherElements["main.root"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["main.content.home"].waitForExistence(timeout: 2))
        XCTAssertTrue(app.staticTexts["main.home.workspaceCount"].waitForExistence(timeout: 2))
        XCTAssertTrue(app.staticTexts["Moscow Cellar"].exists)
        XCTAssertTrue(app.staticTexts["Late Night Standup"].exists)
        XCTAssertTrue(scrollUntilVisible(invitationAcceptButton))
    }

    /// Проверяет создание рабочего пространства через форму на главной вкладке.
    func testHomeTabCreatesWorkspace() {
        let nameField = app.textFields["main.workspace.name"]
        XCTAssertTrue(nameField.waitForExistence(timeout: 2))
        nameField.tap()
        nameField.typeText("Fresh Space")

        let createButton = app.buttons["main.workspace.create"]
        XCTAssertTrue(createButton.exists)
        createButton.tap()

        XCTAssertTrue(app.staticTexts["Fresh Space"].waitForExistence(timeout: 2))
    }

    /// Проверяет accept pending invitation и смену роли участника workspace.
    func testHomeTabHandlesInvitationsAndMembershipRoleActions() {
        let acceptButton = app.buttons["Принять"]
        XCTAssertTrue(scrollUntilVisible(acceptButton))
        acceptButton.tap()

        let hostRoleButton = app.buttons["Ведущий"].firstMatch
        XCTAssertTrue(scrollUntilVisible(hostRoleButton))
        hostRoleButton.tap()
    }

    /// Проверяет organizer venue tab, выбор площадки и доступность действий builder-а.
    func testVenueTabShowsVenueManagementSurface() {
        openTab("Площадки")

        XCTAssertTrue(app.staticTexts["venue.root"].waitForExistence(timeout: 2))
        XCTAssertTrue(app.staticTexts["venue.count"].waitForExistence(timeout: 2))
        XCTAssertTrue(app.buttons["venue.workspace.ws-1"].waitForExistence(timeout: 2))
        XCTAssertTrue(app.buttons["venue.selector.venue-1"].waitForExistence(timeout: 2))
        XCTAssertTrue(scrollUntilVisible(app.buttons["venue.form.create"]))
        XCTAssertTrue(app.textFields["venue.template.name"].waitForExistence(timeout: 2))
        XCTAssertTrue(app.buttons["venue.template.save"].exists)
    }

    /// Проверяет organizer event tab, выбор venue/template и доступность lifecycle controls.
    func testEventTabShowsEventManagementSurface() {
        openTab("События")

        XCTAssertTrue(app.staticTexts["event.root"].waitForExistence(timeout: 2))
        XCTAssertTrue(app.staticTexts["event.count"].waitForExistence(timeout: 2))
        XCTAssertTrue(app.buttons["event.workspace.ws-1"].waitForExistence(timeout: 2))
        XCTAssertTrue(app.buttons["event.venue.venue-1"].waitForExistence(timeout: 2))
        XCTAssertTrue(app.buttons["event.template.template-1"].waitForExistence(timeout: 2))
        XCTAssertTrue(app.textFields["event.form.title"].waitForExistence(timeout: 2))
        XCTAssertTrue(scrollUntilVisible(app.buttons["event.form.create"]))
        XCTAssertTrue(app.buttons["event.publish.event-1"].waitForExistence(timeout: 2))
        XCTAssertTrue(scrollUntilVisible(app.buttons["event.sales.open.event-2"]))
        XCTAssertTrue(scrollUntilVisible(app.buttons["event.cancel.event-2"]))
        XCTAssertTrue(scrollUntilVisible(app.buttons["event.sales.pause.event-3"]))
        XCTAssertTrue(scrollUntilVisible(app.buttons["event.cancel.event-3"]))
        XCTAssertTrue(scrollUntilVisible(app.buttons["event.edit.event-1"]))
        XCTAssertTrue(
            app.descendants(matching: .any)
                .matching(identifier: "event.update.title")
                .firstMatch
                .waitForExistence(timeout: 2)
        )
        XCTAssertTrue(
            app.descendants(matching: .any)
                .matching(identifier: "event.update.priceZones")
                .firstMatch
                .waitForExistence(timeout: 2)
        )
        XCTAssertTrue(app.buttons["event.update.save"].exists)
    }

    /// Проверяет вкладку announcements/feed и publish surface.
    func testAnnouncementsTabShowsFeedAndPublishSurface() {
        openTab("Анонсы")

        let announcementsRoot = app.descendants(matching: .any)
            .matching(identifier: "announcements.root")
            .firstMatch
        let countLabel = app.descendants(matching: .any)
            .matching(identifier: "announcements.count")
            .firstMatch
        let eventButton = app.buttons["announcements.event.event-2"]
        let messageInput = app.textFields["announcements.message"]
        let publishButton = app.buttons["announcements.publish"]

        XCTAssertTrue(announcementsRoot.waitForExistence(timeout: 2))
        XCTAssertTrue(countLabel.waitForExistence(timeout: 2))
        XCTAssertEqual(countLabel.label, "Анонсов: 1")
        XCTAssertTrue(scrollUntilVisible(eventButton))
        eventButton.tap()
        XCTAssertTrue(scrollUntilVisible(messageInput))
        messageInput.tap()
        messageInput.typeText(" Новый анонс")
        XCTAssertTrue(scrollUntilVisible(publishButton))
        publishButton.tap()
        XCTAssertTrue(
            app.descendants(matching: .any)
                .matching(identifier: "announcements.card.announcement-local-2")
                .firstMatch
                .waitForExistence(timeout: 2)
        )
    }

    /// Проверяет вкладку лайнапа, organizer review controls и reorder surface.
    func testLineupTabShowsApplicationsAndReorderSurface() {
        openTab("Лайнап")

        let lineupRoot = app.descendants(matching: .any)
            .matching(identifier: "lineup.root")
            .firstMatch
        let applicationCount = app.descendants(matching: .any)
            .matching(identifier: "lineup.count.applications")
            .firstMatch
        let lineupCount = app.descendants(matching: .any)
            .matching(identifier: "lineup.count.entries")
            .firstMatch
        let loadButton = app.buttons["lineup.organizer.load"]
        let approveButton = app.buttons["lineup.application.action.application-1.approved"]
        let moveDownButton = app.buttons["lineup.entry.moveDown.entry-1"]
        let currentPerformer = app.descendants(matching: .any)
            .matching(identifier: "lineup.live.current")
            .firstMatch
        let nextPerformer = app.descendants(matching: .any)
            .matching(identifier: "lineup.live.next")
            .firstMatch
        let onStageButton = app.buttons["lineup.entry.statusAction.entry-2.on_stage"]
        let applicationStatus = app.descendants(matching: .any)
            .matching(identifier: "lineup.application.status.application-1")
            .firstMatch
        let orderLabel = app.descendants(matching: .any)
            .matching(identifier: "lineup.entry.order.entry-1")
            .firstMatch

        XCTAssertTrue(lineupRoot.waitForExistence(timeout: 2))
        XCTAssertTrue(applicationCount.waitForExistence(timeout: 2))
        XCTAssertTrue(lineupCount.waitForExistence(timeout: 2))
        XCTAssertTrue(currentPerformer.waitForExistence(timeout: 2))
        XCTAssertEqual(currentPerformer.label, "Сейчас на сцене: Иван Смехов")
        XCTAssertTrue(nextPerformer.waitForExistence(timeout: 2))
        XCTAssertEqual(nextPerformer.label, "Следующий: Мария Сетова")
        XCTAssertTrue(scrollUntilVisible(loadButton))
        loadButton.tap()
        XCTAssertTrue(scrollUntilVisible(approveButton))
        approveButton.tap()
        XCTAssertTrue(applicationStatus.waitForExistence(timeout: 2))
        XCTAssertEqual(applicationStatus.label, "Статус: Одобрена")
        XCTAssertTrue(scrollUntilVisible(onStageButton))
        onStageButton.tap()
        XCTAssertEqual(nextPerformer.label, "Следующий: еще не выбран")
        XCTAssertTrue(scrollUntilVisible(moveDownButton))
        moveDownButton.tap()
        XCTAssertTrue(orderLabel.waitForExistence(timeout: 2))
        XCTAssertEqual(orderLabel.label, "2. Иван Смехов")
    }

    /// Проверяет вкладку билетов, раскрытие QR и staff check-in форму.
    func testTicketTabShowsWalletAndCheckInSurface() {
        openTab("Билеты")

        let ticketRoot = app.descendants(matching: .any)
            .matching(identifier: "ticketing.root")
            .firstMatch
        let ticketCount = app.descendants(matching: .any)
            .matching(identifier: "ticketing.count")
            .firstMatch
        let qrToggleButton = app.descendants(matching: .any)
            .matching(identifier: "ticketing.ticket.qr.toggle.ticket-1")
            .firstMatch
        let scanInput = app.descendants(matching: .any)
            .matching(identifier: "ticketing.scan.input")
            .firstMatch
        let scanButton = app.descendants(matching: .any)
            .matching(identifier: "ticketing.scan.button")
            .firstMatch
        let scanResultCode = app.descendants(matching: .any)
            .matching(identifier: "ticketing.scan.resultCode")
            .firstMatch

        XCTAssertTrue(ticketRoot.waitForExistence(timeout: 2))
        XCTAssertTrue(ticketCount.waitForExistence(timeout: 2))
        XCTAssertTrue(scrollUntilVisible(qrToggleButton))
        qrToggleButton.tap()
        XCTAssertTrue(
            app.descendants(matching: .any)
                .matching(identifier: "ticketing.ticket.qr.block.ticket-1")
                .firstMatch
                .waitForExistence(timeout: 2)
        )

        XCTAssertTrue(scrollUntilVisible(scanInput))
        scanInput.tap()
        scanInput.typeText("incomedy.ticket.v1:ticket-1")

        XCTAssertTrue(scrollUntilVisible(scanButton))
        scanButton.tap()

        XCTAssertTrue(scrollUntilVisible(scanResultCode))
        XCTAssertEqual(scanResultCode.label, "Билет подтвержден")
    }

    /// Проверяет вкладку донатов, payout profile форму и history surface.
    func testDonationTabShowsPayoutAndHistorySurface() {
        openTab("Донаты")

        let donationRoot = app.descendants(matching: .any)
            .matching(identifier: "donations.root")
            .firstMatch
        let sentCount = app.descendants(matching: .any)
            .matching(identifier: "donations.count.sent")
            .firstMatch
        let receivedCount = app.descendants(matching: .any)
            .matching(identifier: "donations.count.received")
            .firstMatch
        let companyButton = app.buttons["donations.payout.legalType.company"]
        let beneficiaryInput = app.textFields["donations.payout.beneficiary"]
        let saveButton = app.buttons["donations.payout.save"]

        XCTAssertTrue(donationRoot.waitForExistence(timeout: 2))
        XCTAssertTrue(sentCount.waitForExistence(timeout: 2))
        XCTAssertEqual(sentCount.label, "Отправлено: 1")
        XCTAssertTrue(receivedCount.waitForExistence(timeout: 2))
        XCTAssertEqual(receivedCount.label, "Получено: 1")
        XCTAssertTrue(scrollUntilVisible(companyButton))
        companyButton.tap()
        XCTAssertTrue(scrollUntilVisible(beneficiaryInput))
        beneficiaryInput.tap()
        beneficiaryInput.typeText(" fixture-ref")
        XCTAssertTrue(scrollUntilVisible(saveButton))
        saveButton.tap()
        XCTAssertTrue(
            app.descendants(matching: .any)
                .matching(identifier: "donations.payout.card")
                .firstMatch
                .waitForExistence(timeout: 2)
        )
    }

    /// Проверяет вкладку аккаунта, смену роли и возврат к авторизации после выхода.
    func testAccountTabSwitchesRoleAndSignsOut() {
        openTab("Аккаунт")

        XCTAssertTrue(app.staticTexts["main.content.account"].waitForExistence(timeout: 2))
        XCTAssertTrue(
            app.descendants(matching: .any)
                .matching(identifier: "main.account.avatar")
                .firstMatch
                .waitForExistence(timeout: 2)
        )
        XCTAssertEqual(app.staticTexts["main.account.activeRole"].label, "Зритель")

        let organizerButton = app.buttons["main.account.role.organizer"]
        XCTAssertTrue(organizerButton.waitForExistence(timeout: 2))
        organizerButton.tap()
        XCTAssertEqual(app.staticTexts["main.account.activeRole"].label, "Организатор")

        let signOutButton = app.buttons["main.account.signOut"]
        XCTAssertTrue(signOutButton.waitForExistence(timeout: 2))
        signOutButton.tap()

        XCTAssertTrue(app.staticTexts["Авторизация"].waitForExistence(timeout: 2))
    }

    /// Прокручивает домашний экран, пока нужный элемент не появится в accessibility-дереве.
    ///
    /// - Parameters:
    ///   - element: Целевой UI-элемент.
    ///   - maxSwipes: Максимальное число прокруток вверх.
    /// - Returns: `true`, если элемент найден до исчерпания попыток.
    private func openTab(_ label: String) {
        let directTab = app.tabBars.buttons[label]
        if directTab.exists && directTab.isHittable {
            directTab.tap()
            return
        }

        let moreTab = app.tabBars.buttons.element(boundBy: app.tabBars.buttons.count - 1)
        XCTAssertTrue(moreTab.waitForExistence(timeout: 2))
        moreTab.tap()

        let overflowCell = app.tables.cells.containing(.staticText, identifier: label).firstMatch
        XCTAssertTrue(overflowCell.waitForExistence(timeout: 2))
        overflowCell.tap()
    }

    private func scrollUntilVisible(_ element: XCUIElement, maxSwipes: Int = 6) -> Bool {
        if element.waitForExistence(timeout: 2) {
            return true
        }

        for _ in 0..<maxSwipes {
            app.swipeUp()
            if element.waitForExistence(timeout: 1) {
                return true
            }
        }

        return false
    }
}
