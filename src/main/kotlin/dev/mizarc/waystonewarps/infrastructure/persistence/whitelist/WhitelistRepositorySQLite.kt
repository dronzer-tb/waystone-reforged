package dev.mizarc.waystonewarps.infrastructure.persistence.whitelist

import co.aikar.idb.Database
import dev.mizarc.waystonewarps.domain.whitelist.Whitelist
import dev.mizarc.waystonewarps.domain.whitelist.WhitelistRepository
import dev.mizarc.waystonewarps.infrastructure.persistence.storage.Storage
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class WhitelistRepositorySQLite(private val storage: Storage<Database>): WhitelistRepository {
    // Cache reachable from every region thread; both levels must be concurrent on Folia.
    private val whitelistMap = ConcurrentHashMap<UUID, MutableSet<UUID>>()

    init {
        preload()
    }

    override fun isWhitelisted(warpId: UUID, playerId: UUID): Boolean {
        return whitelistMap[warpId]?.contains(playerId) == true
    }


    override fun getByWarp(warpId: UUID): List<UUID> {
        return whitelistMap[warpId]?.toList() ?: emptyList()
    }

    override fun getByPlayer(playerId: UUID): List<UUID> {
        return whitelistMap.entries
            .filter { (_, players) -> players.contains(playerId) }
            .map { (warpId, _) -> warpId }
            .toList()
    }

    override fun add(whitelist: Whitelist) {
        whitelistMap.computeIfAbsent(whitelist.warpId) { ConcurrentHashMap.newKeySet() }.add(whitelist.playerId)
        storage.connection.executeInsert("INSERT INTO whitelist (warpId, playerId, creationTime) " +
                "VALUES (?, ?, ?);",
            whitelist.warpId, whitelist.playerId, Instant.now())
    }

    override fun remove(warpId: UUID, playerId: UUID) {
        whitelistMap[warpId]?.remove(playerId)
        if (whitelistMap[warpId]?.isEmpty() == true) {
            whitelistMap.remove(warpId)
        }
        storage.connection.executeUpdate("DELETE FROM whitelist WHERE warpId=? AND playerId=?", warpId, playerId)
    }

    override fun removeByWarp(warpId: UUID) {
        whitelistMap.remove(warpId)
        storage.connection.executeUpdate("DELETE FROM whitelist WHERE warpId=?", warpId)
    }

    private fun preload() {
        val results = storage.connection.getResults("SELECT * FROM whitelist;")
        for (result in results) {
            val warpId = UUID.fromString(result.getString("warpId"))
            val playerId = UUID.fromString(result.getString("playerId"))
            whitelistMap.computeIfAbsent(warpId) { ConcurrentHashMap.newKeySet() }.add(playerId)
        }
    }
}