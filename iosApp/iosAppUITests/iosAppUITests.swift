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
        app.tabBars.buttons["Площадки"].tap()

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
        app.tabBars.buttons["События"].tap()

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

    /// Проверяет вкладку аккаунта, смену роли и возврат к авторизации после выхода.
    func testAccountTabSwitchesRoleAndSignsOut() {
        app.tabBars.buttons["Аккаунт"].tap()

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
