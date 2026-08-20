package dev.mizarc.waystonewarps.infrastructure.scheduling

import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.plugin.Plugin

/**
 * Thin wrapper around the Folia region scheduler API.
 *
 * Folia executes independent regions of the world on separate threads, so there is no single
 * "main thread" any more and [org.bukkit.Bukkit.getScheduler] throws on Folia. Every piece of work
 * has to be dispatched onto the thread that owns the data it touches:
 *
 *  * [global]    - work that touches no specific world data (plugin bookkeeping, timers).
 *  * [atRegion]  - work that touches blocks/chunks/entities at a specific [Location].
 *  * [forEntity] - work that touches a specific entity; the task follows the entity across regions
 *                  and is silently dropped once the entity is removed.
 *
 * These schedulers are also present on plain Paper (they are part of the Paper API, not a Folia-only
 * addition), so the plugin behaves identically on Paper and Folia.
 */
object FoliaScheduler {

    /** Runs [task] on the global region thread as soon as possible. */
    fun global(plugin: Plugin, task: () -> Unit) {
        Bukkit.getGlobalRegionScheduler().execute(plugin, task)
    }

    /** Runs [task] on the global region thread after [delayTicks] ticks (minimum of one tick). */
    fun globalLater(plugin: Plugin, delayTicks: Long, task: () -> Unit): ScheduledTask =
        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, { task() }, delayTicks.coerceAtLeast(1L))

    /** Repeats [task] on the global region thread. */
    fun globalTimer(plugin: Plugin, delayTicks: Long, periodTicks: Long, task: () -> Unit): ScheduledTask =
        Bukkit.getGlobalRegionScheduler().runAtFixedRate(
            plugin, { task() }, delayTicks.coerceAtLeast(1L), periodTicks.coerceAtLeast(1L))

    /**
     * Runs [task] on the thread that owns [location]. This is the only legal way to touch blocks,
     * chunks or entities that belong to a region other than the caller's.
     */
    fun atRegion(plugin: Plugin, location: Location, task: () -> Unit) {
        Bukkit.getRegionScheduler().execute(plugin, location, task)
    }

    /** Runs [task] on the thread that owns [location] after [delayTicks] ticks. */
    fun atRegionLater(plugin: Plugin, location: Location, delayTicks: Long, task: () -> Unit): ScheduledTask =
        Bukkit.getRegionScheduler().runDelayed(plugin, location, { task() }, delayTicks.coerceAtLeast(1L))

    /**
     * Runs [task] on the thread currently owning [entity]. Returns null when the entity has already
     * been removed, in which case the task will never run.
     */
    fun forEntity(plugin: Plugin, entity: Entity, task: () -> Unit): ScheduledTask? =
        entity.scheduler.run(plugin, { task() }, null)

    /** Runs [task] on the thread owning [entity] after [delayTicks] ticks. */
    fun forEntityLater(plugin: Plugin, entity: Entity, delayTicks: Long, task: () -> Unit): ScheduledTask? =
        entity.scheduler.runDelayed(plugin, { task() }, null, delayTicks.coerceAtLeast(1L))

    /** Repeats [task] on the thread owning [entity]; automatically cancelled when the entity is removed. */
    fun forEntityTimer(plugin: Plugin, entity: Entity, delayTicks: Long, periodTicks: Long,
                       task: (ScheduledTask) -> Unit): ScheduledTask? =
        entity.scheduler.runAtFixedRate(
            plugin, { scheduled -> task(scheduled) }, null,
            delayTicks.coerceAtLeast(1L), periodTicks.coerceAtLeast(1L))
}
