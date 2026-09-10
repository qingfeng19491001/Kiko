package com.kuikly.stockchat.data.chat

import com.kuikly.stockchat.domain.chat.Conversation
import com.tencent.kuikly.core.module.SharedPreferencesModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONArray
import com.tencent.kuikly.core.pager.IPager

/**
 * 键值存储抽象（默认实现基于 Kuikly SharedPreferencesModule）。
 */
interface KeyValueStore {
    fun get(key: String): String
    fun set(key: String, value: String)
}

class KuiklyKeyValueStore(private val pager: IPager) : KeyValueStore {
    private val module: SharedPreferencesModule
        get() = pager.acquireModule(SharedPreferencesModule.MODULE_NAME)

    override fun get(key: String): String = module.getItem(key)
    override fun set(key: String, value: String) = module.setItem(key, value)
}

/**
 * 会话历史仓库：全部会话序列化为一个 JSON 数组存储，最多保留 [maxConversations] 条。
 */
class ConversationRepository(private val store: KeyValueStore) {

    var maxConversations: Int = 50

    private var cache: MutableList<Conversation>? = null

    fun loadAll(): List<Conversation> {
        cache?.let { return it }
        val list = mutableListOf<Conversation>()
        val raw = runCatching { store.get(KEY_CONVERSATIONS) }.getOrDefault("")
        if (raw.isNotEmpty()) {
            runCatching {
                val array = JSONArray(raw)
                for (i in 0 until array.length()) {
                    array.optJSONObject(i)?.let(ChatCodec::decodeConversation)?.let { list += it }
                }
            }
        }
        list.sortByDescending { it.updatedAt }
        cache = list
        return list
    }

    fun find(id: String): Conversation? = loadAll().firstOrNull { it.id == id }

    fun save(conversation: Conversation) {
        val list = loadAll().toMutableList()
        list.removeAll { it.id == conversation.id }
        list.add(0, conversation)
        while (list.size > maxConversations) list.removeAt(list.size - 1)
        cache = list
        persist(list)
    }

    fun delete(id: String) {
        val list = loadAll().toMutableList()
        list.removeAll { it.id == id }
        cache = list
        persist(list)
    }

    fun clear() {
        cache = mutableListOf()
        persist(emptyList())
    }

    private fun persist(list: List<Conversation>) {
        val array = JSONArray()
        list.forEach { array.put(ChatCodec.encode(it)) }
        runCatching { store.set(KEY_CONVERSATIONS, array.toString()) }
    }

    fun reloadFromStore(): List<Conversation> {
        cache = null
        return loadAll()
    }

    companion object {
        private const val KEY_CONVERSATIONS = "stockchat.conversations.v1"
    }
}

/** 应用偏好：语音播报等跨页面共享的开关。 */
class SettingsRepository(private val store: KeyValueStore) {
    fun isTtsEnabled(): Boolean = runCatching { store.get(KEY_TTS) }.getOrDefault("") == "1"

    fun setTtsEnabled(enabled: Boolean) {
        runCatching { store.set(KEY_TTS, if (enabled) "1" else "0") }
    }

    companion object {
        private const val KEY_TTS = "stockchat.settings.tts"
    }
}

/**
 * 自选 / 最近查看的标的仓库。
 */
class WatchlistRepository(private val store: KeyValueStore) {

    fun load(): List<String> {
        val raw = runCatching { store.get(KEY_WATCHLIST) }.getOrDefault("")
        if (raw.isEmpty()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { array.optString(it) }
        }.getOrDefault(emptyList())
    }

    fun contains(key: String): Boolean = load().contains(key)

    fun toggle(key: String): Boolean {
        val list = load().toMutableList()
        val added = if (list.contains(key)) {
            list.remove(key)
            false
        } else {
            list.add(0, key)
            true
        }
        val array = JSONArray()
        list.forEach { array.put(it) }
        runCatching { store.set(KEY_WATCHLIST, array.toString()) }
        return added
    }

    companion object {
        private const val KEY_WATCHLIST = "stockchat.watchlist.v1"
    }
}