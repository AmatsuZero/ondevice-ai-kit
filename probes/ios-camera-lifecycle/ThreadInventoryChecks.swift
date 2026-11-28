import Darwin
import Foundation

/// Standalone macOS check; not compiled into the device validation app.
@main struct ThreadInventoryChecks {
    static func main() throws {
        let port = pthread_mach_thread_np(pthread_self())
        func references() -> mach_port_urefs_t {
            var value: mach_port_urefs_t = 0
            precondition(mach_port_get_refs(mach_task_self_, port, mach_port_right_t(MACH_PORT_RIGHT_SEND), &value) == KERN_SUCCESS)
            return value
        }
        let before = references()
        for _ in 0..<100 {
            let snapshot = ThreadInventory.capture()
            precondition(snapshot["status"] as? String == "complete-snapshot")
            let rows = snapshot["threads"] as! [[String: Any]]
            let observer = rows.filter { $0["kernelThreadID"] as? UInt64 == snapshot["observerKernelThreadID"] as? UInt64 }
            precondition(observer.count == 1)
            precondition(observer[0]["pthreadPointerHex"] as? String == snapshot["observerPthreadPointerHex"] as? String)
            // THREAD_IDENTIFIER_INFO documents a libproc handle, not pthread_t.
            // Preserve both raw values; macOS currently returns different addresses.
            precondition(observer[0]["threadHandleHex"] is String)
            _ = try JSONSerialization.data(withJSONObject: snapshot)
        }
        let after = references()
        precondition(before == after, "Inventory must release task_threads send rights")
        let missingPthread = ThreadInventory.capture(pthreadLookup: { _ in nil })
        guard missingPthread["status"] as? String == "partial-snapshot" else {
            fputs("FAIL: missing pthread mappings must not report a complete snapshot\n", stderr)
            exit(1)
        }
        let missingRows = missingPthread["threads"] as! [[String: Any]]
        precondition(!missingRows.isEmpty)
        precondition(missingPthread["pthreadLookupFailures"] as? Int == missingRows.count)
        precondition(missingRows.allSatisfy { $0["pthreadPointerHex"] is NSNull })
        precondition(references() == before, "Partial snapshots must also release send rights")
        _ = try JSONSerialization.data(withJSONObject: missingPthread)
        guard let marker = ThreadInventoryMarker() else { fatalError("marker-allocation-failed") }
        let report: [String: Any] = ["snapshots": 100, "sendReferencesBefore": before,
                                   "sendReferencesAfter": after, "marker": marker.evidence,
                                   "snapshot": ThreadInventory.capture(), "platform": "macOS-host-only",
                                   "missingPthreadMapping": missingPthread]
        print(String(data: try JSONSerialization.data(withJSONObject: report, options: [.sortedKeys, .prettyPrinted]), encoding: .utf8)!)
        withExtendedLifetime(marker) {}
    }
}
