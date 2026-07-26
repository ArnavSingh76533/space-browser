package com.spacebrowser.core.adblock

import android.content.Context
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicLong

/**
 * Suffix-based host matcher. An entry `example.com` blocks `example.com` and
 * every subdomain of it. Pure Kotlin so it is unit-testable.
 */
class HostMatcher(rules: Collection<String>) {

    private val exact = HashSet<String>(rules.size * 2)

    init {
        for (r in rules) {
            val rule = r.trim().lowercase()
            if (rule.isEmpty() || rule.startsWith("#")) continue
            exact += rule.removePrefix("*.").removePrefix(".")
        }
    }

    val size: Int get() = exact.size

    fun matches(host: String?): Boolean {
        var h = host?.lowercase() ?: return false
        while (true) {
            if (h in exact) return true
            val dot = h.indexOf('.')
            if (dot < 0) return false
            h = h.substring(dot + 1)
        }
    }
}

/**
 * Runtime blocker: bundled list + user rules, per-site allowlist, and a
 * lock-free session counter that TabManager periodically flushes to stats.
 */
class AdBlocker(context: Context) {

    @Volatile private var bundled: HostMatcher = HostMatcher(emptyList())
    @Volatile private var custom: HostMatcher = HostMatcher(emptyList())
    @Volatile private var allowedSites: Set<String> = emptySet()

    /** Trackers blocked since the last flush (see TabManager.startStatsFlusher). */
    val sessionBlocked = AtomicLong(0)

    init {
        bundled = HostMatcher(loadBundled(context))
    }

    val ruleCount: Int get() = bundled.size + custom.size

    fun updateUserRules(rules: Set<String>, allowlist: Set<String>) {
        custom = HostMatcher(rules)
        allowedSites = allowlist.map { it.trim().lowercase().removePrefix("www.") }.toSet()
    }

    fun isSiteAllowlisted(pageHost: String?): Boolean {
        val h = pageHost?.lowercase()?.removePrefix("www.") ?: return false
        return h in allowedSites
    }

    /** Decide for a sub-resource request while [pageHost] is loaded. */
    fun shouldBlock(requestHost: String?, pageHost: String?): Boolean {
        if (requestHost == null) return false
        if (isSiteAllowlisted(pageHost)) return false
        val blocked = bundled.matches(requestHost) || custom.matches(requestHost)
        if (blocked) sessionBlocked.incrementAndGet()
        return blocked
    }

    private fun loadBundled(context: Context): List<String> = try {
        context.assets.open("adblock/hosts.txt").bufferedReader().readLines()
    } catch (_: Exception) {
        emptyList()
    }

    companion object {
        /** Shared empty body returned for blocked requests. */
        fun emptyStream() = ByteArrayInputStream(ByteArray(0))
    }
}
