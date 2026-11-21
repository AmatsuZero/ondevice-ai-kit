import Foundation

/// Exact small-gallery cosine index for IndexStore's derived in-memory cache.
/// Contains sensitive vectors: never log, export or persist these bytes as plaintext.
/// Building an index does not establish a calibrated recognition policy.
public enum ExactCosineIdentityIndex {
    private static let magic = Data("ODAI-COSINE-IDX-1".utf8)

    public static func build(_ embeddings: [IdentityEmbedding], stopSignal: IdentityStopSignal) throws -> Data {
        try stopSignal.check()
        let space = try validateMatrix(embeddings)
        var data = try header(space, count: embeddings.count)
        data.reserveCapacity(data.count + embeddings.count * space.dimension * 4)
        for embedding in embeddings {
            try stopSignal.check()
            guard normSquared(embedding.values) > 0 else { throw IdentityError(.invalidInput) }
            for (index, value) in embedding.values.enumerated() {
                if index % 256 == 0 { try stopSignal.check() }
                appendLittleUInt32(value.bitPattern, to: &data)
            }
        }
        try stopSignal.check()
        return data
    }

    /// Only the engine may search after acquiring the exact snapshot's template leases.
    static func scores(_ bytes: Data, probe: IdentityEmbedding, retained: [IdentityEmbedding],
                       stopSignal: IdentityStopSignal) throws -> [Double] {
        try stopSignal.check()
        let space = try validateMatrix(retained)
        guard probe.embeddingSpace == space else { throw IdentityError(.embeddingSpaceMismatch) }
        let expectedHeader = try header(space, count: retained.count)
        guard bytes.count == expectedHeader.count + retained.count * space.dimension * 4,
              bytes.prefix(expectedHeader.count) == expectedHeader else { throw IdentityError(.featureFormatUnsupported) }
        let probeNorm = normSquared(probe.values)
        guard probeNorm > 0 else { throw IdentityError(.invalidInput) }
        let scores: [Double] = try bytes.withUnsafeBytes { (buffer: UnsafeRawBufferPointer) in
            var offset = expectedHeader.count
            return try retained.map { embedding in
                try stopSignal.check()
                var dot = 0.0, norm = 0.0
                for (index, expected) in embedding.values.enumerated() {
                    if index % 256 == 0 { try stopSignal.check() }
                    let bits = UInt32(buffer[offset]) | UInt32(buffer[offset + 1]) << 8 |
                        UInt32(buffer[offset + 2]) << 16 | UInt32(buffer[offset + 3]) << 24
                    offset += 4
                    // Bind payload order/content to the currently authorized snapshot.
                    guard bits == expected.bitPattern else { throw IdentityError(.featureFormatUnsupported) }
                    let value = Double(Float(bitPattern: bits))
                    dot += Double(probe.values[index]) * value
                    norm += value * value
                }
                guard norm > 0 else { throw IdentityError(.invalidInput) }
                let score = dot / (sqrt(probeNorm) * sqrt(norm))
                guard score.isFinite else { throw IdentityError(.invalidInput) }
                return min(1, max(-1, score))
            }
        }
        try stopSignal.check()
        return scores
    }

    private static func validateMatrix(_ embeddings: [IdentityEmbedding]) throws -> EmbeddingSpaceId {
        guard let space = embeddings.first?.embeddingSpace, embeddings.count <= 4096,
              (1...4096).contains(space.dimension), space.dtype == .float32, space.endianness == .little,
              space.scoreMetric == "cosine", space.scoreDirection == .higherIsBetter,
              embeddings.allSatisfy({ $0.embeddingSpace == space }) else { throw IdentityError(.featureFormatUnsupported) }
        return space
    }

    private static func header(_ space: EmbeddingSpaceId, count: Int) throws -> Data {
        let identity = Data(space.canonicalJSON().utf8)
        guard identity.count <= 65_536 else { throw IdentityError(.featureFormatUnsupported) }
        var data = magic
        appendLittleUInt32(UInt32(identity.count), to: &data)
        appendLittleUInt32(UInt32(count), to: &data)
        appendLittleUInt32(UInt32(space.dimension), to: &data)
        data.append(identity)
        return data
    }

    private static func appendLittleUInt32(_ value: UInt32, to data: inout Data) {
        var little = value.littleEndian
        withUnsafeBytes(of: &little) { data.append(contentsOf: $0) }
    }

    private static func normSquared(_ values: [Float]) -> Double {
        values.reduce(0) { $0 + Double($1) * Double($1) }
    }
}
