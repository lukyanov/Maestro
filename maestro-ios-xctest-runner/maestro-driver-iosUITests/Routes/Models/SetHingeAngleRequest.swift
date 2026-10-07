import Foundation

struct SetHingeAngleRequest: Codable {
    /// 0 closed to 180 flat.
    let degrees: Double
}
