import Foundation
import FlyingFox
import XCTest

/// Turns a foldable simulator, which ignores `XCUIDevice.orientation`.
@MainActor
struct SetOrientationWithDeviceHubHandler: HTTPHandler {

    func handleRequest(_ request: HTTPRequest) async throws -> HTTPResponse {
        guard let requestBody = try? await JSONDecoder().decode(SetOrientationRequest.self, from: request.bodyData) else {
            return AppError(type: .precondition, message: "incorrect request body provided for set orientation").httpResponse
        }

        do {
            try DeviceHub.setOrientation(requestBody.orientation)
            return HTTPResponse(statusCode: .ok)
        } catch let error as AppError {
            return error.httpResponse
        }
    }
}
