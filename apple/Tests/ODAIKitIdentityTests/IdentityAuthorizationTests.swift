import XCTest
@testable import ODAIKitIdentity

final class IdentityAuthorizationTests: XCTestCase {
    // Removing epoch comparisons would admit a declaration the authority did not issue.
    func testIndependentAuthorityEpochMustEqualDeclaration() throws {
        let state = try IdentityAuthorizationState(purpose: "login")
        for epoch: Int64 in [0, 2] {
            assertCode(.authorizationRevoked) {
                _ = try state.admitting(grant(epoch: 1), scope: .enrollment, provider: Authority(epoch: epoch), nowMillis: 100)
            }
        }
        assertCode(.authorizationRequired) {
            _ = try state.admitting(grant(), scope: .enrollment, provider: Authority(denied: true), nowMillis: 100)
        }
    }

    // Revocation must survive changing grant IDs and restoration from persistent metadata.
    func testSameDomainRevocationSurvivesSnapshotAndNewGrantID() throws {
        let initial = try IdentityAuthorizationState(purpose: "login")
        let admitted = try initial.admitting(grant(), scope: .enrollment, provider: Authority(), nowMillis: 100)
        let revoked = try admitted.revoking(grantId: "g1")
        let restored = try IdentityAuthorizationState.restore(revoked.canonicalJSON(), purpose: "login")
        assertCode(.authorizationRevoked) {
            _ = try restored.admitting(grant(id: "new"), scope: .enrollment, provider: Authority(), nowMillis: 100)
        }
        XCTAssertEqual(restored.highestEpoch(domain: "tenant.subject"), 1)
        let renewed = try restored.admitting(grant(id: "new", epoch: 1), scope: .enrollment,
                                            provider: Authority(epoch: 1), nowMillis: 100)
        XCTAssertEqual(renewed.highestEpoch(domain: "tenant.subject"), 1)
        assertCode(.authorizationRevoked) {
            _ = try renewed.admitting(grant(), scope: .enrollment, provider: Authority(epoch: 1), nowMillis: 100)
        }
    }

    func testRepeatedAndSiblingRevocationDoNotAdvanceDomainAgain() throws {
        let state = try IdentityAuthorizationState(purpose: "login")
            .admitting(grant(), scope: .enrollment, provider: Authority(), nowMillis: 100)
            .admitting(grant(id: "sibling"), scope: .enrollment, provider: Authority(), nowMillis: 100)
        let revoked = try state.revoking(grantId: "g1")
        XCTAssertEqual(try revoked.revoking(grantId: "g1").canonicalJSON(), try revoked.canonicalJSON())
        XCTAssertEqual(try revoked.revoking(grantId: "sibling").canonicalJSON(), try revoked.canonicalJSON())
        assertCode(.authorizationRequired) { _ = try revoked.revoking(grantId: "unknown") }
        let other = try revoked.admitting(grant(id: "other", domain: "other.subject"), scope: .enrollment,
                                         provider: Authority(), nowMillis: 100)
        XCTAssertEqual(other.highestEpoch(domain: "other.subject"), 0)
        XCTAssertEqual(other.highestEpoch(domain: "tenant.subject"), 1)
    }

    func testFullGrantDeclarationIsImmutableAcrossRestore() throws {
        let original = try grant()
        let state = try IdentityAuthorizationState(purpose: "login")
            .admitting(original, scope: .enrollment, provider: Authority(), nowMillis: 100)
        let restored = try IdentityAuthorizationState.restore(state.canonicalJSON(), purpose: "login")
        let changed = try [
            grant(domain: "other.subject"), grant(epoch: 1), grant(purpose: "other"),
            grant(scopes: [.enrollment, .verification]), grant(issued: 1), grant(expires: 300), grant(evidence: "other")
        ]
        for declaration in changed {
            assertCode(.authorizationRequired) {
                _ = try restored.admitting(declaration, scope: .enrollment,
                                           provider: Authority(epoch: declaration.authorizationEpoch), nowMillis: 100)
            }
        }
        XCTAssertEqual(try restored.admitting(original, scope: .enrollment, provider: Authority(), nowMillis: 100)
            .canonicalJSON(), try state.canonicalJSON())
    }

    func testScopePurposeAndWallClockAreCheckedOnEveryAdmission() throws {
        let state = try IdentityAuthorizationState(purpose: "login")
        for invalid in try [grant(scopes: [.verification]), grant(purpose: "other"), grant(issued: 101)] {
            assertCode(.authorizationRequired) {
                _ = try state.admitting(invalid, scope: .enrollment, provider: Authority(), nowMillis: 100)
            }
        }
        let accepted = try state.admitting(grant(), scope: .enrollment, provider: Authority(), nowMillis: 100)
        assertCode(.authorizationExpired) {
            _ = try accepted.admitting(grant(), scope: .enrollment, provider: Authority(), nowMillis: 200)
        }
        assertCode(.authorizationRequired) {
            _ = try accepted.admitting(grant(), scope: .enrollment, provider: Authority(), nowMillis: -1)
        }
    }

    func testStagedStateDoesNotPublishIntoOriginal() throws {
        let initial = try IdentityAuthorizationState(purpose: "login")
        let before = try initial.canonicalJSON()
        let admitted = try initial.admitting(grant(epoch: 4), scope: .enrollment, provider: Authority(epoch: 4), nowMillis: 100)
        let revoked = try admitted.revoking(grantId: "g1")
        XCTAssertEqual(initial.highestEpoch(domain: "tenant.subject"), 0)
        XCTAssertEqual(try initial.canonicalJSON(), before)
        XCTAssertEqual(admitted.highestEpoch(domain: "tenant.subject"), 4)
        XCTAssertEqual(revoked.highestEpoch(domain: "tenant.subject"), 5)
    }

    func testAuthoritativeEpochCannotRollBackPersistedBound() throws {
        let current = try IdentityAuthorizationState(purpose: "login")
            .admitting(grant(epoch: 4), scope: .enrollment, provider: Authority(epoch: 4), nowMillis: 100)
        assertCode(.authorizationRevoked) {
            _ = try current.admitting(grant(id: "rollback", epoch: 3), scope: .enrollment,
                                      provider: Authority(epoch: 3), nowMillis: 100)
        }
        let bounded = try current.admitting(grant(id: "current", epoch: 4), scope: .enrollment,
                                           provider: LowerBoundAuthority(expectedMinimum: 4), nowMillis: 100)
        XCTAssertEqual(bounded.highestEpoch(domain: "tenant.subject"), 4)
    }

    func testOwnershipUsesIndependentProviderAndStoreBinding() throws {
        let ownership = try OwnershipAuthorization(storeId: "store-a", evidenceRef: "owner")
        assertCode(.authorizationRequired) {
            try IdentityAuthorizationState.requireOwnership(ownership, storeId: "store-b", provider: Authority(), nowMillis: 100)
        }
        assertCode(.authorizationRequired) {
            try IdentityAuthorizationState.requireOwnership(ownership, storeId: "store-a", provider: Authority(denied: true), nowMillis: 100)
        }
        // No active biometric grant is required for removal of an owned resource.
        try IdentityAuthorizationState.requireOwnership(ownership, storeId: "store-a", provider: Authority(), nowMillis: 300)
    }

    func testOverflowCannotResetEpoch() throws {
        let state = try IdentityAuthorizationState(purpose: "login")
            .admitting(grant(epoch: .max), scope: .enrollment, provider: Authority(epoch: .max), nowMillis: 100)
        assertCode(.invalidInput) { _ = try state.revoking(grantId: "g1") }
        XCTAssertEqual(state.highestEpoch(domain: "tenant.subject"), Int64.max)
    }

    func testRestoreRejectsMalformedSchemaAndInconsistentBindings() throws {
        let valid = try IdentityAuthorizationState(purpose: "login")
            .admitting(grant(), scope: .enrollment, provider: Authority(), nowMillis: 100).canonicalJSON()
        var fields = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(valid.utf8)) as? [String: Any])
        fields["version"] = 2
        assertCode(.invalidInput) { _ = try IdentityAuthorizationState.restore(identityJSON(fields), purpose: "login") }
        fields["version"] = 1; fields["epochs"] = ["tenant.subject": -1]
        assertCode(.invalidInput) { _ = try IdentityAuthorizationState.restore(identityJSON(fields), purpose: "login") }
        fields["epochs"] = [:]
        assertCode(.invalidInput) { _ = try IdentityAuthorizationState.restore(identityJSON(fields), purpose: "login") }
        fields["epochs"] = ["tenant.subject": 0]; fields["grants"] = ["wrong-id": try grant().canonicalJSON()]
        assertCode(.invalidInput) { _ = try IdentityAuthorizationState.restore(identityJSON(fields), purpose: "login") }
        assertCode(.invalidInput) { _ = try IdentityAuthorizationState.restore(valid, purpose: "other") }
        assertCode(.invalidInput) { _ = try IdentityAuthorizationState.restore("{\"version\":1,\"version\":1}", purpose: "login") }
    }

    func testSnapshotBoundsAndStrictEpochTypes() throws {
        for value in ["true", "0.0", "9223372036854775808"] {
            let json = "{\"version\":1,\"purpose\":\"login\",\"epochs\":{\"unused\":\(value)},\"grants\":{}}"
            assertCode(.invalidInput) { _ = try IdentityAuthorizationState.restore(json, purpose: "login") }
        }
        var state = try IdentityAuthorizationState(purpose: "login")
        for index in 0..<300 {
            state = try state.admitting(grant(id: "g-\(index)"), scope: .enrollment, provider: Authority(), nowMillis: 100)
        }
        assertCode(.invalidInput) { _ = try state.canonicalJSON() }
    }

    private func grant(id: String = "g1", domain: String = "tenant.subject", epoch: Int64 = 0,
                       purpose: String = "login", scopes: Set<IdentityScope> = [.enrollment],
                       issued: Int64 = 0, expires: Int64? = 200, evidence: String = "audit") throws -> AuthorizationGrant {
        try AuthorizationGrant(grantId: id, revocationDomainId: domain, scope: scopes, purpose: purpose,
                               issuedAtMillis: issued, expiresAtMillis: expires, authorizationEpoch: epoch, evidenceRef: evidence)
    }
    private func assertCode(_ code: IdentityErrorCode, file: StaticString = #filePath, line: UInt = #line,
                            _ body: () throws -> Void) {
        XCTAssertThrowsError(try body(), file: file, line: line) {
            XCTAssertEqual(($0 as? IdentityError)?.code, code, file: file, line: line)
        }
    }
}

private struct Authority: IdentityAuthorizationProvider {
    var epoch: Int64 = 0
    var denied = false
    func authoritativeEpoch(grant: AuthorizationGrant, minimumEpoch: Int64, nowMillis: Int64) throws -> Int64 {
        if denied { throw IdentityError(.authorizationRequired) }
        return epoch
    }
    func validateOwnership(_ authorization: OwnershipAuthorization, nowMillis: Int64) throws {
        if denied { throw IdentityError(.authorizationRequired) }
    }
}
private struct LowerBoundAuthority: IdentityAuthorizationProvider {
    let expectedMinimum: Int64
    func authoritativeEpoch(grant: AuthorizationGrant, minimumEpoch: Int64, nowMillis: Int64) throws -> Int64 {
        guard minimumEpoch == expectedMinimum else { throw IdentityError(.authorizationRequired) }
        return 4
    }
    func validateOwnership(_ authorization: OwnershipAuthorization, nowMillis: Int64) throws {}
}
