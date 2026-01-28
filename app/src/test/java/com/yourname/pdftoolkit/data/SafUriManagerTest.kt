package com.yourname.pdftoolkit.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.content.ContentResolver
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.mockito.Mockito
import org.mockito.ArgumentMatchers
import java.io.ByteArrayInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SafUriManagerTest {

    private lateinit var context: Context
    private lateinit var contentResolver: ContentResolver

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        contentResolver = Mockito.mock(ContentResolver::class.java)
    }

    @Test
    fun benchmarkLoadRecentFiles() = runBlocking {
        // Setup a mock context that returns our mock content resolver
        val mockContext = Mockito.mock(Context::class.java)
        Mockito.`when`(mockContext.contentResolver).thenReturn(contentResolver)

        // Setup SharedPreferences mock
        val mockPrefs = Mockito.mock(SharedPreferences::class.java)
        Mockito.`when`(mockContext.getSharedPreferences(ArgumentMatchers.anyString(), ArgumentMatchers.anyInt())).thenReturn(mockPrefs)

        // Create 50 fake files
        val filesArray = JSONArray()
        for (i in 0 until 50) {
            val file = JSONObject().apply {
                put("uriString", "content://com.fake.provider/file_$i")
                put("name", "File $i.pdf")
                put("mimeType", "application/pdf")
                put("size", 1024L)
                put("lastAccessed", System.currentTimeMillis())
            }
            filesArray.put(file)
        }

        Mockito.`when`(mockPrefs.getString(ArgumentMatchers.anyString(), ArgumentMatchers.anyString())).thenReturn(filesArray.toString())

        // Simulate slow I/O in openInputStream
        Mockito.`when`(contentResolver.openInputStream(ArgumentMatchers.any(Uri::class.java))).thenAnswer {
             // Simulate 10ms delay per file check
             Thread.sleep(10)
             ByteArrayInputStream(ByteArray(0))
        }

        println("Starting benchmark...")
        val startTime = System.nanoTime()

        val result = SafUriManager.loadRecentFiles(mockContext)

        val endTime = System.nanoTime()
        val durationMs = (endTime - startTime) / 1_000_000

        println("Benchmark completed in $durationMs ms")
        println("Loaded ${result.size} files")

        // Assert that we loaded 50 files
        assert(result.size == 50)
    }
}
