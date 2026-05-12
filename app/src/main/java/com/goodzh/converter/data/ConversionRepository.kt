package com.goodzh.converter.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class ConversionRepository(context: Context) {
    private val prefs = context.getSharedPreferences("goodzh_records", Context.MODE_PRIVATE)
    private val _records = MutableStateFlow(loadRecords())
    val records: StateFlow<List<ConversionRecord>> = _records.asStateFlow()

    suspend fun save(record: ConversionRecord): Long {
        val next = listOf(record) + _records.value
        persist(next)
        return record.id
    }

    suspend fun update(record: ConversionRecord) {
        persist(_records.value.map { if (it.id == record.id) record else it })
    }

    suspend fun delete(record: ConversionRecord) {
        persist(_records.value.filterNot { it.id == record.id })
    }

    suspend fun clear() {
        persist(emptyList())
    }

    private fun persist(records: List<ConversionRecord>) {
        _records.value = records
        prefs.edit().putString("items", records.toJson().toString()).apply()
    }

    private fun loadRecords(): List<ConversionRecord> {
        val raw = prefs.getString("items", null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    add(array.getJSONObject(index).toRecord())
                }
            }
        }.getOrDefault(emptyList())
    }
}

private fun List<ConversionRecord>.toJson(): JSONArray {
    val array = JSONArray()
    forEach { record ->
        array.put(
            JSONObject()
                .put("id", record.id)
                .put("type", record.type.name)
                .put("title", record.title)
                .put("sourceUri", record.sourceUri)
                .put("outputUri", record.outputUri)
                .put("resultText", record.resultText)
                .put("segmentsJson", record.segmentsJson)
                .put("translatedText", record.translatedText)
                .put("translatedSegmentsJson", record.translatedSegmentsJson)
                .put("subtitleDisplayMode", record.subtitleDisplayMode)
                .put("status", record.status.name)
                .put("message", record.message)
                .put("createdAt", record.createdAt)
        )
    }
    return array
}

private fun JSONObject.toRecord(): ConversionRecord =
    ConversionRecord(
        id = optLong("id", System.currentTimeMillis()),
        type = ConversionType.valueOf(optString("type", ConversionType.Image.name)),
        title = optString("title", "未命名文件"),
        sourceUri = optString("sourceUri", ""),
        outputUri = optString("outputUri", ""),
        resultText = optString("resultText", ""),
        segmentsJson = optString("segmentsJson", ""),
        translatedText = optString("translatedText", ""),
        translatedSegmentsJson = optString("translatedSegmentsJson", ""),
        subtitleDisplayMode = optString("subtitleDisplayMode", "Original"),
        status = ConversionStatus.valueOf(optString("status", ConversionStatus.Success.name)),
        message = optString("message", ""),
        createdAt = optLong("createdAt", System.currentTimeMillis())
    )
