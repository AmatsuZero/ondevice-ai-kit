import Foundation
import XCTest
import ODAIKitVoiceEffects
import ODAIKitAppleVoiceEffects
@preconcurrency import ODAIKitDemo

@MainActor
final class IOSVoiceEffectsBridgeTests: XCTestCase {
    func testRealHighLowAndOriginalPreserveFrames() async throws {
        let root=try directory();defer{try? FileManager.default.removeItem(at:root)}
        let input=root.appendingPathComponent("tone.wav");var bytes=wav()
        for i in 0..<1600 {
            let sample=Int16(12000*sin(2*Double.pi*440*Double(i)/16000))
            bytes[44+i*2]=UInt8(truncatingIfNeeded:sample);bytes[45+i*2]=UInt8(truncatingIfNeeded:sample>>8)
        }
        try bytes.write(to:input)
        let bridge=IOSVoiceEffectsBridge(client:try VoiceEffectsClient(backend:AppleVoiceEffectsBackend(),outputDirectory:root),inputs:TestVoiceInputs(input))
        for preset in [VoiceEffectsBridgePreset.original,.high,.low] {
            let reply=VoiceReply();_=bridge.process(request:request(preset),callback:reply)
            try await wait{reply.terminals>0};XCTAssertNil(reply.failure)
            let output=try XCTUnwrap(reply.output);XCTAssertEqual(output.sampleRate,16000);XCTAssertEqual(output.frameCount,1600);XCTAssertEqual(output.backendId,"apple.time-pitch")
            let result=try Data(contentsOf:XCTUnwrap(bridge.outputURL(reference:output.reference)))
            if preset == .original { XCTAssertEqual(result,bytes) } else { XCTAssertNotEqual(result,bytes) }
            let release=VoiceReleaseReply();bridge.releaseOutput(reference:output.reference,callback_:release)
            try await wait{release.completed};XCTAssertNil(release.failure)
        }
        let close=VoiceCloseReply();bridge.close(callback__:close);try await wait{close.completed}
    }
    func testRealCopyOpaqueReferencesAndOutputAcrossClose() async throws {
        let root = try directory(); defer { try? FileManager.default.removeItem(at: root) }
        let input = root.appendingPathComponent("in.wav"); let data = wav(); try data.write(to: input)
        let inputs = TestVoiceInputs(input)
        let client = try VoiceEffectsClient(backend: AppleVoiceEffectsBackend(), outputDirectory: root)
        let bridge = IOSVoiceEffectsBridge(client: client, inputs: inputs)
        let callback = VoiceReply(); _ = bridge.process(request: request(), callback: callback)
        try await wait { callback.output != nil || callback.failure != nil }
        XCTAssertNil(callback.failure); let output = try XCTUnwrap(callback.output)
        XCTAssertEqual(inputs.releases,1); XCTAssertFalse(output.reference.contains(input.path))
        let url = try XCTUnwrap(bridge.outputURL(reference: output.reference)); XCTAssertEqual(try Data(contentsOf: url),data)
        let close1 = VoiceCloseReply(), close2 = VoiceCloseReply()
        bridge.close(callback__: close1); bridge.close(callback__: close2)
        try await wait { close1.completed && close2.completed }; XCTAssertTrue(FileManager.default.fileExists(atPath:url.path))
        let rejected = VoiceReply(); _ = bridge.process(request: request(),callback:rejected)
        XCTAssertEqual(rejected.failure?.code,.closed)
        let other = IOSVoiceEffectsBridge(client: nil, inputs: inputs), foreign = VoiceReleaseReply()
        other.releaseOutput(reference: output.reference,callback_:foreign); XCTAssertEqual(foreign.failure?.code,.invalidInput)
        XCTAssertTrue(FileManager.default.fileExists(atPath:url.path))
        let release = VoiceReleaseReply(); bridge.releaseOutput(reference:output.reference,callback_:release)
        try await wait { release.completed }; XCTAssertNil(release.failure); XCTAssertFalse(FileManager.default.fileExists(atPath:url.path))
        let again = VoiceReleaseReply(); bridge.releaseOutput(reference:output.reference,callback_:again)
        XCTAssertTrue(again.completed); XCTAssertNil(again.failure)
        XCTAssertTrue(FileManager.default.fileExists(atPath:input.path))
    }
    func testCancelCloseWaitForNativeRenderAndLeaseRelease() async throws {
        let root = try directory(); defer { try? FileManager.default.removeItem(at:root) }
        let input = root.appendingPathComponent("in.wav"); try wav().write(to:input)
        let backend = BlockingVoiceBackend(), inputs = TestVoiceInputs(input)
        let client = try VoiceEffectsClient(backend:backend,outputDirectory:root)
        let bridge = IOSVoiceEffectsBridge(client:client,inputs:inputs)
        let callback = VoiceReply(); let task = bridge.process(request:request(.high),callback:callback)
        try await wait { backend.entered }
        let busy = VoiceReply(); _ = bridge.process(request:request(),callback:busy); XCTAssertEqual(busy.failure?.code,.busy)
        task.cancel(); let close = VoiceCloseReply(); bridge.close(callback__:close)
        await Task.yield(); XCTAssertEqual(inputs.releases,0); XCTAssertEqual(callback.terminals,0); XCTAssertFalse(close.completed)
        backend.unblock(); try await wait { close.completed }
        XCTAssertEqual(inputs.releases,1); XCTAssertEqual(callback.terminals,1); XCTAssertTrue(callback.cancelled); XCTAssertNil(callback.output)
    }
    func testUnavailableAndUnknownReleaseAreExplicit() {
        let bridge = IOSVoiceEffectsBridge(client:nil,inputs:TestVoiceInputs(URL(fileURLWithPath:"/unused")))
        let reply = VoiceReply(); _ = bridge.process(request:request(),callback:reply)
        XCTAssertEqual(reply.failure?.code,.backendUnavailable)
        let release = VoiceReleaseReply(); bridge.releaseOutput(reference:"unknown",callback_:release)
        XCTAssertEqual(release.failure?.code,.invalidInput)
    }
    private func directory() throws -> URL {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("voice-test-\(UUID().uuidString)")
        try FileManager.default.createDirectory(at:url,withIntermediateDirectories:true); return url
    }
    private func request(_ preset: VoiceEffectsBridgePreset = .original) -> VoiceEffectsBridgeRequest {
        VoiceEffectsBridgeRequest(inputReference:"input",preset:preset)
    }
    private func wait(_ predicate: () -> Bool) async throws {
        let deadline = Date().addingTimeInterval(10)
        while !predicate() { if Date() > deadline { XCTFail("native operation did not drain"); throw CancellationError() }; try await Task.sleep(nanoseconds:1_000_000) }
    }
    private func wav() -> Data {
        var data = Data(); func text(_ s:String){ data.append(contentsOf:s.utf8) }
        func n(_ x:Int,_ size:Int){for i in 0..<size{data.append(UInt8((x>>(8*i))&255))}}
        text("RIFF");n(36+3200,4);text("WAVEfmt ");n(16,4);n(1,2);n(1,2);n(16000,4);n(32000,4);n(2,2);n(16,2);text("data");n(3200,4);data.append(Data(repeating:0,count:3200));return data
    }
}
@MainActor private final class TestVoiceInputs: IOSVoiceEffectsInputProvider {
    let url:URL; var releases=0
    init(_ url:URL){self.url=url}
    func acquire(reference:String)throws->any IOSVoiceEffectsInputLease {
        guard reference == "input" else { throw CancellationError() }
        return TestVoiceLease(url){self.releases += 1}
    }
}
@MainActor private final class TestVoiceLease: IOSVoiceEffectsInputLease {
    let url:URL; var done:(()->Void)?
    init(_ url:URL,_ done:@escaping ()->Void){self.url=url;self.done=done}
    func release() async { done?(); done=nil }
}
@MainActor private final class VoiceReply:NSObject,@preconcurrency VoiceEffectsBridgeProcessCallback {
    var output:VoiceEffectsBridgeOutput?;var failure:VoiceEffectsBridgeFailure?;var cancelled=false;var terminals=0
    func onResult(output__:VoiceEffectsBridgeOutput){XCTAssertTrue(Thread.isMainThread);terminals += 1;output=output__}
    func onFailure(failure__:VoiceEffectsBridgeFailure){XCTAssertTrue(Thread.isMainThread);terminals += 1;failure=failure__}
    func onCancelled(){XCTAssertTrue(Thread.isMainThread);terminals += 1;cancelled=true}
}
@MainActor private final class VoiceReleaseReply:NSObject,@preconcurrency VoiceEffectsBridgeReleaseCallback {
    var completed=false;var failure:VoiceEffectsBridgeFailure?
    func onReleased(){completed=true}
    func onFailure(failure__:VoiceEffectsBridgeFailure){failure=failure__;completed=true}
}
@MainActor private final class VoiceCloseReply:NSObject,@preconcurrency VoiceEffectsBridgeCloseCallback {
    var completed=false
    func onClosed(){completed=true}
    func onFailure(failure__:VoiceEffectsBridgeFailure){XCTFail("close failed: \(failure__.code)");completed=true}
}
private final class BlockingVoiceBackend:VoiceEffectsBackend,@unchecked Sendable {
    let backendId="apple.time-pitch";private let condition=NSCondition();private var started=false;private var allowed=false
    var entered:Bool{condition.lock();defer{condition.unlock()};return started}
    func unblock(){condition.lock();allowed=true;condition.broadcast();condition.unlock()}
    func render(_ input:VoiceEffectsPCMInput,preset:VoiceEffectsPreset,checkCancellation:@escaping @Sendable ()throws->Void)throws->Data {
        condition.lock();started=true
        while !allowed { condition.wait() };condition.unlock()
        try checkCancellation();return input.pcm16LittleEndian
    }
}
