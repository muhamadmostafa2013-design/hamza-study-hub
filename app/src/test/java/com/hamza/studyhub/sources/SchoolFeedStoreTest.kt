package com.hamza.studyhub.sources

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
class SchoolFeedStoreTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before fun clearFeed() {
        listOf("school_notifications.jsonl", "school_notifications.jsonl.bak", "school_notifications.jsonl.new")
            .forEach { File(context.filesDir, it).delete() }
    }

    @After fun cleanup() = clearFeed()

    @Test fun editingExistingHomeworkDoesNotRemoveNewNotifications() {
        SchoolFeedStore.append(context, JSONObject().put("externalId", "homework").put("progressPercent", 0))
        SchoolFeedStore.edit(context) { items ->
            items.first().put("progressPercent", 50)
            items.add(JSONObject().put("externalId", "new-message"))
        }
        assertEquals(2, SchoolFeedStore.read(context).size)
        assertEquals(50, SchoolFeedStore.read(context).first().getInt("progressPercent"))
    }

    @Test fun concurrentAppendsKeepAllUniqueEntries() {
        val pool = Executors.newFixedThreadPool(6)
        try {
            val jobs = (1..30).map { id ->
                pool.submit { SchoolFeedStore.append(context, JSONObject().put("externalId", "message-$id")) }
            }
            jobs.forEach { it.get(20, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }
        val items = SchoolFeedStore.read(context)
        assertEquals(30, items.size)
        assertEquals(30, items.map { it.getString("externalId") }.toSet().size)
        assertTrue(File(context.filesDir, "school_notifications.jsonl").readText().endsWith("\n"))
    }
}
