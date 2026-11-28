import Darwin
import Foundation

/// Validation-only snapshot of this process. No other task, stack, or memory is read.
enum ThreadInventory {
    static func capture(
        pthreadLookup: (thread_t) -> pthread_t? = pthread_from_mach_thread_np
    ) -> [String: Any] {
        let started = ProcessInfo.processInfo.systemUptime
        var observerID: UInt64 = 0
        let observerStatus = pthread_threadid_np(nil, &observerID)
        let observerPointer = UInt(bitPattern: pthread_self())
        var list: thread_act_array_t?
        var count: mach_msg_type_number_t = 0
        let status = task_threads(mach_task_self_, &list, &count)
        guard status == KERN_SUCCESS, let list else {
            return ["status": "enumeration-failed", "taskThreadsStatus": status,
                    "startedUptime": started, "observerIDStatus": observerStatus]
        }

        var rows: [[String: Any]] = []
        var infoFailures = 0
        var pthreadLookupFailures = 0
        var releaseFailures = 0
        for port in UnsafeBufferPointer(start: list, count: Int(count)) {
            var info = thread_identifier_info()
            var size = mach_msg_type_number_t(MemoryLayout<thread_identifier_info>.size / MemoryLayout<natural_t>.size)
            let expectedSize = size
            let result = withUnsafeMutablePointer(to: &info) {
                $0.withMemoryRebound(to: integer_t.self, capacity: Int(size)) {
                    thread_info(port, thread_flavor_t(THREAD_IDENTIFIER_INFO), $0, &size)
                }
            }
            if result == KERN_SUCCESS && size == expectedSize {
                // Convert the returned handle to a number only; do not dereference a
                // different thread's pthread storage, which may concurrently exit.
                let pointer = pthreadLookup(port).map { UInt(bitPattern: $0) }
                if pointer == nil { pthreadLookupFailures += 1 }
                rows.append([
                    "kernelThreadID": info.thread_id,
                    "threadHandleHex": hex(info.thread_handle),
                    "pthreadPointerHex": pointer.map { hex(UInt64($0)) } as Any? ?? NSNull(),
                    // This is a hypothesis to calibrate against trace records, not a TID.
                    "pthreadLow32CandidateHex": pointer.map { hex(UInt64(UInt32(truncatingIfNeeded: $0))) } as Any? ?? NSNull(),
                    "identifierInfoStatus": result
                ])
            } else {
                infoFailures += 1
                rows.append(["identifierInfoStatus": result, "returnedCount": size])
            }
            if mach_port_deallocate(mach_task_self_, port) != KERN_SUCCESS { releaseFailures += 1 }
        }
        let arrayRelease = vm_deallocate(mach_task_self_, vm_address_t(UInt(bitPattern: list)),
                                         vm_size_t(Int(count) * MemoryLayout<thread_t>.stride))
        let complete = observerStatus == 0 && infoFailures == 0 && pthreadLookupFailures == 0
            && releaseFailures == 0 && arrayRelease == KERN_SUCCESS
        return ["status": complete ? "complete-snapshot" : "partial-snapshot",
                "taskThreadsStatus": status, "enumeratedCount": count, "threads": rows,
                "identifierInfoFailures": infoFailures, "portReleaseFailures": releaseFailures,
                "pthreadLookupFailures": pthreadLookupFailures,
                "arrayReleaseStatus": arrayRelease, "observerKernelThreadID": observerID,
                "observerIDStatus": observerStatus, "observerPthreadPointerHex": hex(UInt64(observerPointer)),
                "startedUptime": started, "finishedUptime": ProcessInfo.processInfo.systemUptime,
                "boundary": "non-atomic own-task snapshot; absence is not an exact thread exit timestamp"]
    }

    static func hex(_ value: UInt64) -> String { "0x" + String(value, radix: 16) }
}

/// A known, live allocation to calibrate the trace's thread label on the actual device.
/// It stays owned by the validation host until that host exits; it is not a GrOp.
final class ThreadInventoryMarker {
    private let pointer: UnsafeMutableRawPointer
    let evidence: [String: Any]

    init?() {
        let size = 4097
        guard let pointer = malloc(size) else { return nil }
        memset(pointer, 0x5a, size)
        self.pointer = pointer
        var threadID: UInt64 = 0
        let status = pthread_threadid_np(nil, &threadID)
        let pthreadPointer = UInt(bitPattern: pthread_self())
        evidence = ["address": ThreadInventory.hex(UInt64(UInt(bitPattern: pointer))),
                    "requestedBytes": size, "kernelThreadID": threadID, "threadIDStatus": status,
                    "pthreadPointerHex": ThreadInventory.hex(UInt64(pthreadPointer)),
                    "pthreadLow32CandidateHex": ThreadInventory.hex(UInt64(UInt32(truncatingIfNeeded: pthreadPointer))),
                    "uptime": ProcessInfo.processInfo.systemUptime,
                    "lifetime": "validation host owns this marker until host exit"]
    }

    deinit { free(pointer) }
}
