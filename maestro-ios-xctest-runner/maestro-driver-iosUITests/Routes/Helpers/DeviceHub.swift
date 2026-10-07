import Foundation

/// The hinge and orientation controls of Xcode's Device Hub: the only way to fold or turn a foldable
/// simulator such as the iPhone Duo. `simctl` has no such command, and setting `XCUIDevice.orientation`
/// on the Duo reports success without turning it.
///
/// Each control is a vendor-defined HID event (usage page 0xFF61, usage 0x5B) whose data is a
/// binary-serialized `{provider, source, type, value}`, the layout facebook/idb and
/// artemnovichkov/hinge use. It is undocumented and nothing here can tell whether the device moved, so
/// the host reads the result back with `devicectl`.
enum DeviceHub {

    static func setHinge(degrees: Double) throws {
        try post(source: "hinge-slider-control", type: "range", value: NSNumber(value: min(max(degrees, 0), 180)))
    }

    static func setOrientation(_ orientation: SetOrientationRequest.Orientation) throws {
        let value = switch orientation {
        case .portrait: "portrait"
        case .landscapeLeft: "landscape-left"
        case .landscapeRight: "landscape-right"
        case .upsideDown: "pud"
        }
        try post(source: "orientation-picker-control", type: "enum", value: value as NSString)
    }

    private static func post(source: String, type: String, value: NSObject) throws {
        let iokit = try IOKit.shared.get()
        let payload: NSDictionary = [
            "provider": "com.apple.Virtualization.VirtualMachines",
            "source": source,
            "type": type,
            "value": value,
        ]
        guard let data = iokit.serialize(payload, 1)?.takeRetainedValue() as Data? else {
            throw AppError(message: "Could not serialize the Device Hub \(source) event")
        }
        let event = data.withUnsafeBytes { bytes in
            iokit.createVendorEvent(
                nil, mach_absolute_time(), 0xFF61, 0x5B, 0,
                bytes.bindMemory(to: UInt8.self).baseAddress!, data.count, 0
            )
        }
        guard let event else {
            throw AppError(message: "Could not create the Device Hub \(source) event")
        }
        iokit.dispatch(iokit.client, event)
        Unmanaged<AnyObject>.fromOpaque(event).release()
    }

    /// The private IOKit functions the events need, looked up once.
    private struct IOKit {
        typealias CreateClient = @convention(c) (CFAllocator?, Int32, CFDictionary?) -> UnsafeMutableRawPointer?
        typealias CreateVendorEvent = @convention(c) (
            CFAllocator?, UInt64, UInt32, UInt32, UInt32, UnsafePointer<UInt8>, CFIndex, UInt32
        ) -> UnsafeMutableRawPointer?
        typealias Dispatch = @convention(c) (UnsafeMutableRawPointer, UnsafeMutableRawPointer) -> Void
        typealias Serialize = @convention(c) (CFTypeRef, CFOptionFlags) -> Unmanaged<CFData>?

        let client: UnsafeMutableRawPointer
        let createVendorEvent: CreateVendorEvent
        let dispatch: Dispatch
        let serialize: Serialize

        static let shared: Result<IOKit, AppError> = load()

        private static func load() -> Result<IOKit, AppError> {
            guard let handle = dlopen("/System/Library/Frameworks/IOKit.framework/IOKit", RTLD_NOW) else {
                return .failure(AppError(message: "IOKit is not available to post Device Hub events"))
            }
            func symbol<T>(_ name: String) -> T? {
                dlsym(handle, name).map { unsafeBitCast($0, to: T.self) }
            }
            // A simple client (type 4) is what Device Hub's own helper uses.
            guard let createClient: CreateClient = symbol("IOHIDEventSystemClientCreateWithType"),
                  let createVendorEvent: CreateVendorEvent = symbol("IOHIDEventCreateVendorDefinedEvent"),
                  let dispatch: Dispatch = symbol("IOHIDEventSystemClientDispatchEvent"),
                  let serialize: Serialize = symbol("IOCFSerialize"),
                  let client = createClient(nil, 4, nil)
            else {
                return .failure(AppError(message: "IOKit lacks the HID functions Device Hub events need"))
            }
            return .success(IOKit(client: client, createVendorEvent: createVendorEvent, dispatch: dispatch, serialize: serialize))
        }
    }
}
