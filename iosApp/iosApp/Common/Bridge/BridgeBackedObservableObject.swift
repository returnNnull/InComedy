import Foundation
import Shared

@MainActor
class BridgeBackedObservableObject: ObservableObject {
    private var bindingHandle: BridgeHandle?

    func setBinding(_ handle: BridgeHandle) {
        bindingHandle?.dispose()
        bindingHandle = handle
    }

    deinit {
        bindingHandle?.dispose()
    }
}
