package com.eazyverse.testtrack.data

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.IOException

/** A new version deletes the one it replaces, and the sweep leaves only what a review still refers to. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class)
class ProofStoreSweepTest {
    private val context = RuntimeEnvironment.getApplication()

    private fun picture(name: String): Uri {
        val file = File(context.cacheDir, name)
        val bitmap = Bitmap.createBitmap(40, 60, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF336699.toInt()) }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return Uri.fromFile(file)
    }

    @Test
    fun aNewVersionDeletesTheOneItReplaces() {
        val first = requireNotNull(ProofStore.keep(context, "r1", picture("a.jpg")))
        val second = requireNotNull(ProofStore.keep(context, "r1", picture("b.jpg")))
        assertFalse(first.exists())
        assertEquals(second, ProofStore.proof(context, "r1"))
    }

    @Test
    fun theSweepKeepsOnlyWhatAReviewRefersTo() {
        val kept = requireNotNull(ProofStore.keep(context, "r2", picture("c.jpg")))
        val stray = File(kept.parentFile, "r2_1").apply { writeText("left over") }
        val edit = File(context.cacheDir, "${ProofStore.EDIT_PREFIX}x.jpg").apply { writeText("edit") }
        val capture = File(File(context.cacheDir, "review_proof").apply { mkdirs() }, "proof_1.jpg").apply { writeText("shot") }
        ProofStore.sweep(context, now = System.currentTimeMillis() + ProofStore.SWEEP_AGE_MS)
        assertTrue(kept.exists())
        assertEquals(kept, ProofStore.proof(context, "r2"))
        assertFalse(stray.exists())
        assertFalse(edit.exists())
        assertFalse(capture.exists())
    }

    @Test
    fun theSweepLeavesFreshFilesAndOnesInUse() {
        val dir = requireNotNull(ProofStore.keep(context, "r3", picture("d.jpg"))).parentFile
        val writing = File(dir, "r3_new.part").apply { writeText("half") }
        val edit = File(context.cacheDir, "${ProofStore.EDIT_PREFIX}live.jpg").apply { writeText("edit") }
        val capture = File(File(context.cacheDir, "review_proof").apply { mkdirs() }, "proof_2.jpg").apply { writeText("shot") }
        val old = System.currentTimeMillis() - 2 * ProofStore.SWEEP_AGE_MS
        edit.setLastModified(old)
        capture.setLastModified(old)
        ProofStore.hold(edit)
        ProofStore.hold(capture)
        try {
            ProofStore.sweep(context)
            assertTrue("a write under way is not taken", writing.exists())
            assertTrue("the editor's live copy stays, however old", edit.exists())
            assertTrue("a capture not yet taken over stays", capture.exists())
        } finally {
            ProofStore.release(edit)
            ProofStore.release(capture)
        }
        writing.setLastModified(old)
        ProofStore.sweep(context)
        assertFalse(writing.exists())
        assertFalse(edit.exists())
        assertFalse(capture.exists())
    }

    @Test
    fun aFailedWriteLeavesNoPartAndKeepsTheOldOne() {
        val first = requireNotNull(ProofStore.keep(context, "r4", picture("e.jpg")))
        val failed =
            runCatching {
                ProofStore.write(context, "r4", "image/jpeg") { out ->
                    out.write(ByteArray(64) { 1 })
                    throw IOException("cut short")
                }
            }
        assertTrue(failed.isFailure)
        assertEquals(listOf(first.name), first.parentFile!!.list()!!.filter { it.startsWith("r4_") })
        assertEquals(first, ProofStore.proof(context, "r4"))
    }
}
