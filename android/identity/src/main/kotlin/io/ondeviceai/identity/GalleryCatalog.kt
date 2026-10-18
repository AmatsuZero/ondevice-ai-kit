package io.ondeviceai.identity

import java.nio.file.Path
import java.util.UUID

/** Encrypted, copy-on-write gallery metadata catalog. IdentityStore remains authoritative. */
internal class GalleryCatalog(root: Path, val identityStoreId: String, keys: IdentityKeyProvider) : AutoCloseable {
    val storeId: String
    private val journal: IdentityJournal
    private val lock = Any()
    private var snapshots = emptyMap<String, GallerySnapshotRecord>()
    private var heads = emptyMap<String, String>()
    private var tombstones = emptyMap<String, String>()
    private var closed = false
    private var publicationsSealed = false
    private var retired = false
    private var operating = false

    init {
        token(identityStoreId)
        val opened = IdentityJournal(root, "index", keys)
        try {
            val data = opened.read()
            if (data == null) {
                storeId = UUID.randomUUID().toString().lowercase()
                opened.commit(encode(storeId, identityStoreId, snapshots, heads, tombstones))
            } else {
                val fields = IdentityContractJSON.parse(data.decodeToString())
                val version = fields.long("version")
                val expectedKeys = when (version) {
                    1L -> setOf("version", "storeId", "identityStoreId", "snapshots", "heads")
                    2L -> setOf("version", "storeId", "identityStoreId", "snapshots", "heads", "tombstones")
                    else -> emptySet()
                }
                if (fields.keys != expectedKeys)
                    throw IdentityError(IdentityErrorCode.INVALID_INPUT)
                val persistedStore = fields.string("storeId")
                val persistedIdentity = fields.string("identityStoreId")
                token(persistedStore); token(persistedIdentity)
                if (persistedIdentity != identityStoreId) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
                val rawSnapshots = fields["snapshots"] as? Map<*, *> ?: throw IdentityError(IdentityErrorCode.INVALID_INPUT)
                val rawHeads = fields["heads"] as? Map<*, *> ?: throw IdentityError(IdentityErrorCode.INVALID_INPUT)
                val restoredTombstones = if (version == 2L) {
                    val raw = fields["tombstones"] as? Map<*, *> ?: throw IdentityError(IdentityErrorCode.INVALID_INPUT)
                    raw.entries.associate { (lineage, receipt) ->
                        val id = lineage as? String ?: throw IdentityError(IdentityErrorCode.INVALID_INPUT)
                        val value = receipt as? String ?: throw IdentityError(IdentityErrorCode.INVALID_INPUT)
                        token(id); token(value); id to value
                    }.also { values ->
                        if (values.values.toSet().size != values.size) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
                    }
                } else emptyMap()
                val restored = rawSnapshots.entries.associate { (revision, encoded) ->
                    val key = revision as? String ?: throw IdentityError(IdentityErrorCode.INVALID_INPUT)
                    val value = encoded as? String ?: throw IdentityError(IdentityErrorCode.INVALID_INPUT)
                    val record = GallerySnapshotRecord.restore(value)
                    if (key != record.ref.revision || record.indexStoreId != persistedStore || record.identityStoreId != identityStoreId)
                        throw IdentityError(IdentityErrorCode.INVALID_INPUT)
                    key to record
                }
                val restoredHeads = rawHeads.entries.associate { (lineage, revision) ->
                    val id = lineage as? String ?: throw IdentityError(IdentityErrorCode.INVALID_INPUT)
                    val ref = revision as? String ?: throw IdentityError(IdentityErrorCode.INVALID_INPUT)
                    token(id); token(ref)
                    val record = restored[ref] ?: throw IdentityError(IdentityErrorCode.INVALID_INPUT)
                    if (record.ref.id != id) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
                    id to ref
                }
                restored.values.forEach { record ->
                    record.parent?.let { parent ->
                        val parentRecord = restored[parent.revision]
                        if (parentRecord?.ref != parent || parentRecord.ref.id != record.ref.id)
                            throw IdentityError(IdentityErrorCode.INVALID_INPUT)
                    }
                }
                val reachable = mutableSetOf<String>()
                restoredHeads.forEach { (lineage, revision) ->
                    var cursor: String? = revision
                    while (cursor != null) {
                        if (!reachable.add(cursor)) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
                        val record = restored[cursor] ?: throw IdentityError(IdentityErrorCode.INVALID_INPUT)
                        if (record.ref.id != lineage) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
                        cursor = record.parent?.revision
                    }
                }
                if (reachable != restored.keys) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
                storeId = persistedStore; snapshots = restored; heads = restoredHeads; tombstones = restoredTombstones
            }
            journal = opened
        } catch (error: Throwable) {
            opened.close(); throw error
        }
    }

    fun sealPublications() = synchronized(lock) {
        if (operating) throw IdentityError(IdentityErrorCode.BUSY)
        // A fault retires normal catalog access, but close must still seal and
        // drain the existing writer without attempting another journal write.
        publicationsSealed = true
    }
    fun tombstoneReceipts(): Map<String, String> = access { tombstones.toMap() }
    fun hasSnapshots(lineageId: String): Boolean = access { snapshots.values.any { it.ref.id == lineageId } }

    fun publish(record: GallerySnapshotRecord) = access {
        if (publicationsSealed) throw IdentityError(IdentityErrorCode.CLOSED)
        if (record.indexStoreId != storeId || record.identityStoreId != identityStoreId) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
        if (tombstones.containsKey(record.ref.id)) throw IdentityError(IdentityErrorCode.GALLERY_REVISION_GONE)
        if (snapshots.containsKey(record.ref.revision)) throw IdentityError(IdentityErrorCode.GALLERY_REVISION_CONFLICT)
        val parent = record.parent
        if (parent != null) {
            if (heads[record.ref.id] != parent.revision || snapshots[parent.revision]?.ref != parent)
                throw IdentityError(IdentityErrorCode.GALLERY_REVISION_CONFLICT)
        } else if (heads.containsKey(record.ref.id)) throw IdentityError(IdentityErrorCode.GALLERY_REVISION_CONFLICT)
        val nextSnapshots = snapshots + (record.ref.revision to record)
        val nextHeads = heads + (record.ref.id to record.ref.revision)
        // Every admitted lineage must retain room for its logical deletion,
        // even while all snapshots are pinned by live readers.
        val reservedTombstones = tombstones + nextHeads.keys.filterNot(tombstones::containsKey).associateWith { "r".repeat(256) }
        encode(storeId, identityStoreId, nextSnapshots, nextHeads, reservedTombstones)
        val bytes = encode(storeId, identityStoreId, nextSnapshots, nextHeads, tombstones)
        try { journal.commit(bytes) }
        catch (error: Throwable) { retired = true; throw error }
        snapshots = nextSnapshots; heads = nextHeads
    }

    fun snapshot(ref: GalleryRef): GallerySnapshotRecord = access {
        if (tombstones.containsKey(ref.id)) throw IdentityError(IdentityErrorCode.GALLERY_REVISION_GONE)
        snapshots[ref.revision]?.takeIf { it.ref == ref } ?: throw IdentityError(IdentityErrorCode.GALLERY_REVISION_GONE)
    }

    fun head(lineageId: String): GalleryRef? = access {
        if (tombstones.containsKey(lineageId)) throw IdentityError(IdentityErrorCode.GALLERY_REVISION_GONE)
        heads[lineageId]?.let { snapshots[it]?.ref ?: throw IdentityError(IdentityErrorCode.INVALID_INPUT) }
    }

    fun tombstoneReceipt(lineageId: String): String? = access { tombstones[lineageId] }

    fun tombstoneLineage(lineageId: String, receiptId: String) = access {
        token(lineageId); token(receiptId)
        tombstones[lineageId]?.let { existing ->
            if (existing != receiptId) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
            return@access
        }
        if (tombstones.values.any { it == receiptId }) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
        val revision = heads[lineageId] ?: throw IdentityError(IdentityErrorCode.GALLERY_NOT_FOUND)
        if (snapshots[revision]?.ref?.id != lineageId) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
        val next = tombstones + (lineageId to receiptId)
        val bytes = encode(storeId, identityStoreId, snapshots, heads, next)
        try { journal.commit(bytes) }
        catch (error: Throwable) { retired = true; throw error }
        tombstones = next
    }

    fun removeLineage(lineageId: String) = access {
        if (!tombstones.containsKey(lineageId)) throw IdentityError(IdentityErrorCode.GALLERY_REVISION_GONE)
        val remaining = snapshots.filterValues { it.ref.id != lineageId }
        val nextHeads = heads - lineageId
        if (remaining.size == snapshots.size && nextHeads == heads) return@access
        val bytes = encode(storeId, identityStoreId, remaining, nextHeads, tombstones)
        try { journal.commit(bytes) }
        catch (error: Throwable) { retired = true; throw error }
        snapshots = remaining; heads = nextHeads
    }

    override fun close() = synchronized(lock) {
        if (operating) throw IdentityError(IdentityErrorCode.BUSY)
        if (!closed) { closed = true; journal.close() }
    }

    private fun <T> access(body: () -> T): T = synchronized(lock) {
        if (closed || retired) throw IdentityError(IdentityErrorCode.CLOSED)
        if (operating) throw IdentityError(IdentityErrorCode.BUSY)
        operating = true
        try { body() } finally { operating = false }
    }

    private fun encode(store: String, identity: String, records: Map<String, GallerySnapshotRecord>, heads: Map<String, String>, tombstones: Map<String, String>): ByteArray {
        val json = IdentityContractJSON.encode(mapOf("version" to 2, "storeId" to store, "identityStoreId" to identity,
            "snapshots" to records.mapValues { it.value.canonicalJSON }, "heads" to heads, "tombstones" to tombstones))
        if (json.encodeToByteArray().size > 65_536) throw IdentityError(IdentityErrorCode.INVALID_INPUT)
        return json.encodeToByteArray()
    }
}
