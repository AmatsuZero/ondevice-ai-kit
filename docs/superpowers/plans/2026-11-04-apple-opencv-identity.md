# Apple OpenCV Identity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans. User authorized autonomous continuation; implement inline, bounded independent review if service is available.

**Goal:** Run the approved real OpenCV Identity model on Apple and connect an optional Demo host.

**Architecture:** Optional package contains ObjC++ CPU primitives and Swift factory/model descriptor. Existing LocalIdentityBackend, IdentityStore, IOSIdentityBridge and shared UI retain lifecycle/authorization decisions.

**Tech Stack:** Swift, Objective-C++, official OpenCV 4.12.0 SDK; existing YuNet/SFace ONNX models.

**Spec:** ../specs/2026-11-04-apple-opencv-identity-design.md

## Global Constraints

Default Apple package unchanged; exact approved runtime/models/sample only. No invented VERIFIED policy/PAD, raw vectors/images in logs or new dependency runtime. Protect nine files including Demo pbxproj. No push/merge/release. Stop/close waits for blocking native call; corner/resource enhancements deferred.

## Review Focus

Exact manifest/version binding; upright BGR/preprocessing and single-face geometry; exception/memory lifetime across native calls; close/stop cannot publish late results or release model early; cleanup revokes every session candidate.

### Task 1: Optional real backend

Files: new apple-opencv/Package.swift and Sources/{ODAIOpenCVNative,ODAIKitOpenCVIdentity}; tests under apple-opencv/Tests. Local SDK under ignored Vendor/artifacts.
Interfaces: `OpenCvIdentityModel.manifest()`, `.ref`, `.embeddingSpace`, `.unverifiedPolicy(kind:)`; `OpenCvIdentity.open(modelStore:) async throws -> LocalIdentityBackend`.

- [ ] Verify official SDK hash, slices, minOS, ObjC++ APIs and notices; stage only ignored artifacts.
- [ ] Write tests then observe absent backend symbols RED after dependency setup.
- [ ] Implement fixed manifest/native decode/detect/align/embed and factory with existing lease lifecycle.
- [ ] Build optional device/simulator targets; run tests/real approved fixture where supported. Record real inference separately from compile.

### Task 2: Optional Demo session and consumer

Files: new optional Demo Swift host/session and isolated generator/probe; existing DemoController hooks only if required, no protected project edit.
Interfaces: session exposes IOSIdentityBridge/configuration/probe resolver and async full-grant delete/close.

- [ ] Test real image enrollment, blank/policy rejection, candidate deletion and grant-wide teardown with durable reopen and model lease release; RED before missing session if tests require it.
- [ ] Wire explicit local consent/model import plus existing shared UI; build isolated host and record touch/picker gaps accurately.
- [ ] Run default Apple suite, protected hashes, bounded review; update validation/handoff/ledger and local commit.
