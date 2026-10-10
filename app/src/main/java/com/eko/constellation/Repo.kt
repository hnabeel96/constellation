package com.eko.constellation

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.math.exp
import kotlin.math.max

data class Neighbor(val link: Link, val other: Thought, val outgoing: Boolean)

/** Single source of truth. Everything lives in one JSON blob on the phone. */
object Repo {
    private const val PREFS = "constellation"
    private const val KEY = "graph"
    const val DAY = 86_400_000L

    private var prefs: SharedPreferences? = null

    var graph by mutableStateOf(Graph())
        private set

    fun init(ctx: Context) {
        if (prefs != null) return
        val p = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        val raw = p.getString(KEY, null)
        graph = if (raw == null) seed() else runCatching { GraphJson.parse(raw) }.getOrElse {
            // Never silently lose data: park the unreadable copy before starting fresh.
            p.edit().putString("${KEY}_unreadable_${System.currentTimeMillis()}", raw).apply()
            seed()
        }
        save()
    }

    private fun seed(): Graph {
        val n = now()
        val roots = listOf("Games", "Apps", "Career", "Life").map {
            Thought(id = newId(), text = it, createdAt = n, updatedAt = n, isRoot = true, lastTouched = n)
        }
        return Graph(thoughts = roots)
    }

    private fun newId() = UUID.randomUUID().toString()
    private fun save() { prefs?.edit()?.putString(KEY, GraphJson.write(graph))?.apply() }
    private fun update(f: (Graph) -> Graph) { graph = f(graph); save() }
    private fun mapThought(id: String, f: (Thought) -> Thought) =
        update { g -> g.copy(thoughts = g.thoughts.map { if (it.id == id) f(it) else it }) }

    fun flag(name: String) = prefs?.getBoolean(name, false) ?: false
    fun setFlag(name: String) { prefs?.edit()?.putBoolean(name, true)?.apply() }

    // ---- time helpers ----
    fun now() = System.currentTimeMillis()
    fun dueIn(days: Int): Long =
        LocalDate.now().plusDays(days.toLong()).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    fun todayKey(): String = LocalDate.now().toString()

    // ---- reads ----
    fun byId(id: String?): Thought? = id?.let { i -> graph.thoughts.find { it.id == i } }
    fun roots(g: Graph = graph) = g.thoughts.filter { it.isRoot && it.status == Status.ACTIVE }
    fun ideas(g: Graph = graph) = g.thoughts.filter { !it.isRoot && it.status == Status.ACTIVE }

    fun neighbors(id: String, g: Graph = graph): List<Neighbor> = g.links.mapNotNull { l ->
        when (id) {
            l.from -> g.thoughts.find { it.id == l.to }?.let { Neighbor(l, it, true) }
            l.to -> g.thoughts.find { it.id == l.from }?.let { Neighbor(l, it, false) }
            else -> null
        }
    }

    fun linkCounts(g: Graph = graph): Map<String, Int> {
        val m = HashMap<String, Int>()
        g.links.forEach {
            m[it.from] = (m[it.from] ?: 0) + 1
            m[it.to] = (m[it.to] ?: 0) + 1
        }
        return m
    }

    /** 0..1. Grows with visits and links, cools off when untouched (3-week half-life-ish). */
    fun heat(t: Thought, links: Int): Float {
        val days = (now() - max(t.lastTouched, t.createdAt)).coerceAtLeast(0) / DAY.toDouble()
        val score = (t.visits + 2 * links + 1) * exp(-days / 21.0)
        return (1 - exp(-score / 5.0)).toFloat().coerceIn(0f, 1f)
    }

    // ---- writes ----
    fun capture(text: String, treeId: String?, parentId: String?, type: LinkType): String? {
        val clean = text.trim()
        if (clean.isEmpty()) return null
        val n = now()
        val id = newId()
        val parent = byId(parentId)
        val tree = if (parent?.isRoot == true) parent.id else treeId
        val t = Thought(
            id = id, text = clean, createdAt = n, updatedAt = n, treeId = tree,
            nextReview = dueIn(1), intervalDays = 1, lastTouched = n,
        )
        update { g ->
            var thoughts = g.thoughts + t
            var links = g.links
            if (parent != null && !parent.isRoot) {
                links = links + Link(newId(), id, parent.id, type, n)
                thoughts = thoughts.map { if (it.id == parent.id) it.copy(lastTouched = n) else it }
            }
            g.copy(thoughts = thoughts, links = links)
        }
        return id
    }

    fun edit(id: String, text: String) {
        if (text.isBlank()) return
        mapThought(id) { it.copy(text = text.trim(), updatedAt = now(), lastTouched = now()) }
    }

    fun setTree(id: String, treeId: String?) = mapThought(id) { it.copy(treeId = treeId) }
    fun visit(id: String) = mapThought(id) { it.copy(visits = it.visits + 1, lastTouched = now()) }

    fun link(from: String, to: String, type: LinkType) {
        if (from == to) return
        val n = now()
        update { g ->
            val links = g.links.filterNot { (it.from == from && it.to == to) || (it.from == to && it.to == from) } +
                Link(newId(), from, to, type, n)
            val thoughts = g.thoughts.map {
                when (it.id) {
                    from -> if (type == LinkType.MERGED_INTO) it.copy(status = Status.MERGED, endedAt = n, lastTouched = n)
                    else it.copy(lastTouched = n)
                    to -> it.copy(lastTouched = n)
                    else -> it
                }
            }
            g.copy(thoughts = thoughts, links = links)
        }
    }

    fun unlink(linkId: String) = update { g -> g.copy(links = g.links.filterNot { it.id == linkId }) }

    /** Branched or linked from the daily queue: it's alive, check back in 3 days. */
    fun engaged(id: String) {
        mapThought(id) { it.copy(intervalDays = 3, nextReview = dueIn(3), visits = it.visits + 1, lastTouched = now()) }
        markDone(id)
    }

    /** Not now. Each park doubles the wait: 2, 4, 8, 16... days. */
    fun park(id: String): Int {
        val t = byId(id) ?: return 0
        val interval = (t.intervalDays * 2).coerceIn(2, 64)
        mapThought(id) { it.copy(intervalDays = interval, nextReview = dueIn(interval)) }
        markDone(id)
        return interval
    }

    fun kill(id: String, reason: String?) {
        val why = reason?.trim()?.ifEmpty { null }
        mapThought(id) { it.copy(status = Status.KILLED, note = why, endedAt = now()) }
        markDone(id)
    }

    fun revive(id: String) = update { g ->
        g.copy(
            thoughts = g.thoughts.map {
                if (it.id == id) it.copy(
                    status = Status.ACTIVE, note = null, endedAt = 0,
                    nextReview = dueIn(1), intervalDays = 1, lastTouched = now(),
                ) else it
            },
            links = g.links.filterNot { it.from == id && it.type == LinkType.MERGED_INTO },
        )
    }

    fun deleteForever(id: String) = update { g ->
        g.copy(
            thoughts = g.thoughts.filterNot { it.id == id }.map { if (it.treeId == id) it.copy(treeId = null) else it },
            links = g.links.filterNot { it.from == id || it.to == id },
            queue = g.queue.copy(ids = g.queue.ids - id),
        )
    }

    fun addTree(name: String) {
        if (name.isBlank()) return
        val n = now()
        update { it.copy(thoughts = it.thoughts + Thought(newId(), name.trim(), n, n, isRoot = true, lastTouched = n)) }
    }

    // ---- daily queue ----
    private fun markDone(id: String) = update { g ->
        if (id in g.queue.ids) g.copy(queue = g.queue.copy(ids = g.queue.ids - id, done = g.queue.done + 1)) else g
    }

    /** Unlinked thoughts first (they're the ones you'd forget), then whatever is most overdue. */
    private fun candidates(g: Graph, exclude: Set<String>): List<Thought> {
        val lc = linkCounts(g)
        return ideas(g).filter { it.id !in exclude }
            .sortedWith(compareBy<Thought>({ if ((lc[it.id] ?: 0) == 0) 0 else 1 }, { it.nextReview }))
    }

    fun ensureQueue() {
        val g = graph
        val today = todayKey()
        if (g.queue.day == today) return
        val n = now()
        val ids = candidates(g, emptySet()).filter { it.nextReview <= n }.take(g.settings.perDay).map { it.id }
        update { it.copy(queue = Queue(today, ids, 0)) }
    }

    fun pullOne(): Boolean {
        val g = graph
        val next = candidates(g, g.queue.ids.toSet()).firstOrNull() ?: return false
        update { it.copy(queue = it.queue.copy(ids = it.queue.ids + next.id)) }
        return true
    }

    // ---- settings & backup ----
    fun updateSettings(f: (AppSettings) -> AppSettings) = update { it.copy(settings = f(it.settings)) }

    fun exportJson(): String = GraphJson.write(graph)

    fun importJson(raw: String): Boolean = runCatching {
        val g = GraphJson.parse(raw)
        graph = g.copy(queue = Queue())
        save()
    }.isSuccess
}
