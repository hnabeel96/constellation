package com.eko.constellation

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class Status { ACTIVE, KILLED, MERGED }

/** A link always reads "from <verb> to": a branch "builds on" its parent. */
enum class LinkType(val label: String, val incoming: String, val verb: String) {
    BUILDS_ON("Builds on", "Built on by", "builds on"),
    CONTRADICTS("Contradicts", "Contradicted by", "contradicts"),
    INSPIRED_BY("Inspired by", "Inspired", "inspired by"),
    RELATED("Related to", "Related to", "related to"),
    MERGED_INTO("Merged into", "Absorbed", "merged into"),
}

/** A thought is a star. Roots (isRoot) are the trees thoughts belong to. */
data class Thought(
    val id: String,
    val text: String,
    val createdAt: Long,
    val updatedAt: Long,
    val status: Status = Status.ACTIVE,
    val isRoot: Boolean = false,
    val treeId: String? = null,
    val nextReview: Long = 0,
    val intervalDays: Int = 1,
    val visits: Int = 0,
    val lastTouched: Long = 0,
    val note: String? = null,
    val endedAt: Long = 0,
)

data class Link(val id: String, val from: String, val to: String, val type: LinkType, val createdAt: Long)

data class AppSettings(val hour: Int = 9, val minute: Int = 0, val perDay: Int = 3, val notify: Boolean = true)

data class Queue(val day: String = "", val ids: List<String> = emptyList(), val done: Int = 0)

data class Graph(
    val thoughts: List<Thought> = emptyList(),
    val links: List<Link> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val queue: Queue = Queue(),
)

object GraphJson {
    fun write(g: Graph): String {
        val root = JSONObject()
        root.put("app", "constellation")
        root.put("version", 1)
        root.put("thoughts", JSONArray().apply {
            g.thoughts.forEach { t ->
                put(JSONObject().apply {
                    put("id", t.id)
                    put("text", t.text)
                    put("createdAt", t.createdAt)
                    put("updatedAt", t.updatedAt)
                    put("status", t.status.name)
                    put("isRoot", t.isRoot)
                    put("treeId", t.treeId ?: JSONObject.NULL)
                    put("nextReview", t.nextReview)
                    put("intervalDays", t.intervalDays)
                    put("visits", t.visits)
                    put("lastTouched", t.lastTouched)
                    put("note", t.note ?: JSONObject.NULL)
                    put("endedAt", t.endedAt)
                })
            }
        })
        root.put("links", JSONArray().apply {
            g.links.forEach { l ->
                put(JSONObject().apply {
                    put("id", l.id)
                    put("from", l.from)
                    put("to", l.to)
                    put("type", l.type.name)
                    put("createdAt", l.createdAt)
                })
            }
        })
        root.put("settings", JSONObject().apply {
            put("hour", g.settings.hour)
            put("minute", g.settings.minute)
            put("perDay", g.settings.perDay)
            put("notify", g.settings.notify)
        })
        root.put("queue", JSONObject().apply {
            put("day", g.queue.day)
            put("ids", JSONArray(g.queue.ids))
            put("done", g.queue.done)
        })
        return root.toString()
    }

    fun parse(raw: String): Graph {
        val o = JSONObject(raw)
        val ta = o.getJSONArray("thoughts")
        val thoughts = (0 until ta.length()).map { i ->
            val t = ta.getJSONObject(i)
            Thought(
                id = t.getString("id"),
                text = t.optString("text"),
                createdAt = t.optLong("createdAt"),
                updatedAt = t.optLong("updatedAt"),
                status = enumOr(t.str("status"), Status.ACTIVE),
                isRoot = t.optBoolean("isRoot"),
                treeId = t.str("treeId"),
                nextReview = t.optLong("nextReview"),
                intervalDays = t.optInt("intervalDays", 1),
                visits = t.optInt("visits"),
                lastTouched = t.optLong("lastTouched"),
                note = t.str("note"),
                endedAt = t.optLong("endedAt"),
            )
        }
        val ids = thoughts.map { it.id }.toSet()
        val la = o.optJSONArray("links") ?: JSONArray()
        val links = (0 until la.length()).mapNotNull { i ->
            val l = la.getJSONObject(i)
            val from = l.optString("from")
            val to = l.optString("to")
            if (from !in ids || to !in ids) null
            else Link(
                id = l.optString("id").ifEmpty { UUID.randomUUID().toString() },
                from = from,
                to = to,
                type = enumOr(l.str("type"), LinkType.RELATED),
                createdAt = l.optLong("createdAt"),
            )
        }
        val s = o.optJSONObject("settings")
        val settings = if (s == null) AppSettings() else AppSettings(
            hour = s.optInt("hour", 9),
            minute = s.optInt("minute", 0),
            perDay = s.optInt("perDay", 3),
            notify = s.optBoolean("notify", true),
        )
        val q = o.optJSONObject("queue")
        val queue = if (q == null) Queue() else Queue(
            day = q.optString("day"),
            ids = q.optJSONArray("ids")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
            done = q.optInt("done"),
        )
        return Graph(thoughts, links, settings, queue)
    }

    private fun JSONObject.str(key: String): String? = if (has(key) && !isNull(key)) optString(key) else null

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default
}
