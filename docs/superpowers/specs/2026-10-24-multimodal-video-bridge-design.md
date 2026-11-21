# Typed video KMP bridge design

2026-10-24. Continues Androidnativeparity6986e86 and fixed Apple nativevideo gates. Author design under user autonomous solo execution; independent review pending. Initialremote sync05c67d2 still blocked by jihuTLS/resetpreflight; all new worklocal.

## Intent and boundaries

Expose explicit single-video request to shared callers without making existing single-image bridge implementations handle video. Use opaque host-owned token, never paths/URLs/filebytes incommon DTO. No Demo/hoststager/picker/model/backend/newdependency change; no KMPdeviceinference/quality/resource/minOS acceptance. RetainiOS15/API26floors andfixednativeSDKs. Preserveprotectedfiles/artifacts; no push/cleanup.

## Decision and interfaces

Author chooses optionalderived `MultimodalVideoBridge : MultimodalBridge` with `generateMultimodalVideo(request:MultimodalBridgeVideoRequest,selection,callback):BridgeTask`. Request(inputReference:String,prompt:String,maxOutputTokens:Int=128) requiresnonblanktoken/prompt,positivebudget, preservesallvalues. ExistingMultimodalBridge andimageDTO remain unchanged. UnavailableMultimodalBridge implementsderivedinterface withsameunlinked/closed failure andnoresult.

Rejected: addmediaenumtooldrequest (changesoldcallcontract); useimageoperationforvideo (wrongnativeAPI); separatebridgeinstance/registry (overlappinglifetime). Bothnativebridges implementderivedinterface androuteoneprivateimage/video request throughcurrenttaskmanagement/nativeclient. Nativeinputlease/provider typesremain URL/File+release withnomediareadinginbridge. Addoptional `videoInputs` provider argument defaultnil/null toexistingnativebridgeconstructor. Sameproviderinterface isreusable, butexplicitseparateinjectionprevents video accidentallyresolving throughimage-onlyhoststager. Noclient/unlinked/closed/busy/invalidselection rejectsbeforeprovider acquisition; missingvideoprovider→INVALID_INPUT. Unknown/unavailable/legacy nativebackend maps existingstablecodes, neverfallback. Existinghost construction andimage behavior unaffected; Android original3argconstructor retained viaJvmOverloads.

## Ownership and callbacks

Admission/provider/callback remainUI dispatcher/MainActor, cancelanythread. Acquireonelease exactlyonce; constructmatchingnativevideo request; validationfailure releases acquiredlease before failure. One shared active task holds slot throughnativegeneration+drain+inputrelease; cleanup precedes terminal so callbackcanreenterothermodality. Immediatecancel mustreleasewithoutstart; cancel/closewinslateordinaryerror orreleasefailure. ExistingKotlinNonCancellable drainfinallyrelease anddetachedownerscope retained; SwiftuncancelleddrainthenMainActor synchronousrelease retained. Old/regressive/postcancel/postterminalupdates filtered bytaskidentity/sequence; nativeidentity/selection preserved. Closejoinsqueriesandbothmediatasks, thennativeclientclose, andsharesrepeatedcompletion. Errorsstable/sanitized, nopath/prompt incommonfailure. Availabilityremainsbackendwide, no newclaimofvideoprofile.

## Verification

Task1commonRED→GREENnewvaluevalidation/optionalinterface/unlinkedclosedtests, fullcommon andboth frameworklink. No new requirementtooldfakebridgeimplementations; oldVideo/Speechselectors regressionsmustcontinue. Task2Androidrealnativeclientwithcontrolledexternalbackends: videoacquisition/dispatchidentity,missingprovider/unlinked,explicit/legacy, bidirectionalbusy/cancel/drain/release,orderedupdates,immediatecancel,hostcancellation/query/close/redaction. Task3Swiftadapterusingexistingfreshgenerator, bothODAIKitBinding/ODAIKitDemoABIs: actualmissingAPIRed→video tests andoldimage/hosttestsGreen, knownsimulatordevice only. Generatorrequiredsourceincluded; fulltools/lint/nativecompile checks. No fakeclient/modelinference. Native8frameweightdeviceevidence stayspreviousscope; KMPbridgeinjection tests not newdevicevideo acceptance.

Nextindependentpackage sharedtypedvideo input/state/UI andbothhoststagers, thenexplicitfixedKMPdevicechain. Do not addUI/realpickerhere. Roadmap5/12/P9active, independentreviewpending; preserveallupstream8→7/shortsample/binaryexit65/naturalquality/Metalenergy/Androidbackend/floor/oldUI/P6gaps.
