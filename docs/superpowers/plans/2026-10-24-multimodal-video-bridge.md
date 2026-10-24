# Typed multimodal video KMP bridge implementation plan

> **For agentic workers:** superpowers:executing-plans inline solo peruser. Testsfirst, taskscopeauthorreview, exactlocalcommits; no subagents/cleanup/push.

**Goal:** Addoptionaltypedvideobridgeover existingnativeclients without expandingDemo/mediaaccess.
**Architecture:** Optionalderivedcommonprotocol; separateoptionalvideoInputs injection; one existing bridge operation/nativeclient/leasecleanup.
**Tech Stack:** currentKotlin/KMP/Swift6-compatibleMainActor/XCTest/JUnit/Gradleoffline/Xcode.
**Spec:** `docs/superpowers/specs/2026-10-24-multimodal-video-bridge-design.md`

## Global constraints

KeepbaseiOS15/API26, model/runtime/lock/cache, oldimageAPI/hosts/UI unchanged. No newdeps/download/signing/private media/recording/remote writes. Protected2files untouched; retainallfailedlogs/artifacts. Shellrtk, fulltools explicitRuby. Authorreviewonly, independentpending,5/12/P9active.

## Review focus

- Media-specificprovider injection preventsaccidentalimage leaseforvideo.
- Sharedbridge/nativeclient slot spansbothmedia untiljoin+release, cancelwinslateerror.
- Terminalcallbacks occuraftercleanup andallowcrossmediareentry onUI.
- Legacy/unavailable/unknownnativebackend andunknownSwiftselection neverfallback/leakprivateerrors.
- BothKMPAppleABIs andoldimagehostconstructors remaincompatible, noselectorcollision.

### Task 1: Common optional typed video contract

**Files:** kmp-binding/src/commonMain/kotlin/io/ondeviceai/binding/MultimodalBridge.kt; new commonTest MultimodalVideoBridgeContractTest.kt.
**Interfaces:** data class MultimodalBridgeVideoRequest(inputReference:String,prompt:String,maxOutputTokens:Int=128); interface MultimodalVideoBridge:MultimodalBridge withgenerateMultimodalVideo(request,selection,callback):BridgeTask. Existingcallbacks/outputs/failure unchanged.

- [ ] Writevalues/unavailable/closed/oldimageconformer compatibility tests. Run `rtk proxy python3 scripts/gradle-local.py --offline :kmp-binding:iosSimulatorArm64Test --console=plain`,expectedmissingAPI RED.
- [ ] Implementrequest/derivedprotocol/unavailablevideoentrypoint. RunfullcommonGREEN+linkbothbinding/Demoframeworks; recordcounts/headernames/oldVideo selector compatibility. Authorreview/exactlocalcommit.

### Task 2: Android video adapter

**Files:** existingAndroidMultimodalBridge.kt; new android/multimodal/src/test/kotlin/io/ondeviceai/multimodal/AndroidMultimodalVideoBridgeTest.kt.
**Interfaces:** existingconstructor(client,inputs,scope) remains; optional4thvideoInputs:AndroidMultimodalInputProvider?=null, @JvmOverloads. Derivedprotocolimplemented. Bothentrypointsprivatecommongeneratewithselectedprovider andmatchingnative request.

- [ ] Addrealclient+externalgatedbackendvideotests(identity/selection/UI,missingprovider,legacy,unlinked,2waybusy/cancel/drain/release,reentry,hostcancel,query/close,redaction). Missingentrypoint/constructor REDviaoffline module testfilter.
- [ ] Factorcurrentgenerateintooneprivatepath, matchingnativeimage/video dispatch, shareupdate/submit/drain/release; nosecondactiveoperation. Newconstructor defaultmustnotpickimageproviderforvideo.
- [ ] Fullmodule/oldimagebridge/nativevideo tests/lint/bindingcompileGREEN, authorreview andlocalcommit.

### Task 3: Apple adapter and both ABIs

**Files:** kmp-binding/apple/IOSMultimodal{Bridge,Input}.swift; new probes/ios-multimodal-bridge/IOSMultimodalVideoBridgeTests.swift; generator source list.
**Interfaces:** init(client:inputs:videoInputs:...=nil), optionalprovider usesexistingIOSMultimodalInputProvider, bothentrypoints oneprivategenerate+typedNativeRequestenum. Noinputprotocolnewrequirement.

- [ ] NewvideotestsusingrealSwiftclient/externalbackend, includingnilprovider/acquirevalidationcleanup,identity/explicit/unknownselection/legacy,2waybusy/close/cancel/drain+lease/UI/oldcallbacks/immediatecancel/redaction. Includeactualtestsourceingenerator. RebuildfixedKMPframeworks, generatefreshBindingtestproject andrunmissingadapterAPI RED.
- [ ] Implementmatchingnativevideo request andcommonpath, preserveimage/submit/taskhandle/close. RunBinding+DemoframeworkABI newvideotests+oldimage/hostsuite GREEN. Usefreshoutputdir, simulatorDF2DC7EA-257B-4E5F-9AFD-EC4BD2ED6D7F and-disableAutomaticPackageResolution.
- [ ] Swiftformatstrict forchangedSwift andfulltools explicitRuby, fullAndroidverifyiffurtherKotlinchanges, diffcheck/protectedhash. Scopeauthorreview/localcommit; no KMPmodeldeviceclaim.

### Task 4: Recovery checkpoint

- [ ] Newvalidation+roadmap/handoff/plan/ledgerchecks withactualcounts/paths/commits; initialsyncpendingaccurate; oldacceptancegaps+independentreviewpending retained.
- [ ] Exactsource/hash/index/difflink checks, authorselfreview/docscommit. NextsharedvideoDemo independentdesign, no automaticUI/modelrun.
