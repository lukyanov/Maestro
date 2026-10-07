import Foundation
import FlyingFox
import XCTest

@MainActor
struct SetHingeAngleHandler: HTTPHandler {

    func handleRequest(_ request: HTTPRequest) async throws -> HTTPResponse {
        guard let requestBody = try? await JSONDecoder().decode(SetHingeAngleRequest.self, from: request.bodyData) else {
            return AppError(type: .precondition, message: "incorrect request body provided for set hinge angle").httpResponse
        }

        do {
            try DeviceHub.setHinge(degrees: requestBody.degrees)
            return HTTPResponse(statusCode: .ok)
        } catch let error as AppError {
            return error.httpResponse
        }
    }
}
