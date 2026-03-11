package dev.mizarc.waystonewarps.infrastructure.persistence.migrations

import co.aikar.idb.Database

class Migration2_AddHomeAndProtection : Migration {
    override val fromVersion: Int = 1
    override val toVersion: Int = 2

    override fun migrate(db: Database) {
        runCatching {
            db.executeUpdate("ALTER TABLE warps ADD COLUMN isHome INTEGER DEFAULT 0;")
        }
        runCatching {
            db.executeUpdate("ALTER TABLE warps ADD COLUMN isProtected INTEGER DEFAULT 0;")
        }
    }
}
