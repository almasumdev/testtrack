package com.eazyverse.testtrack.video

import org.junit.Test
import org.junit.runner.Description
import org.junit.runner.Runner
import org.junit.runner.manipulation.Filter
import org.junit.runner.manipulation.Filterable
import org.junit.runner.notification.RunNotifier
import org.robolectric.RobolectricTestRunner

/** Set by `-PvideoFrames`; see app/build.gradle.kts. */
internal val videoFramesOn: Boolean get() = System.getProperty("videoFrames") == "true"

/**
 * Robolectric, but only when the video frames were asked for.
 *
 * Without the property every test is reported skipped before Robolectric is even built, so a plain
 * `testDebugUnitTest` neither boots a sandbox nor downloads an Android jar for frames nobody wanted.
 * The tests carry their own `Assume` as well, for a run that reaches them some other way.
 */
class VideoFramesRunner(
    private val klass: Class<*>,
) : Runner(),
    Filterable {
    private val robolectric: RobolectricTestRunner? = if (videoFramesOn) RobolectricTestRunner(klass) else null

    private val skipped: Description =
        Description.createSuiteDescription(klass).apply {
            klass.methods
                .filter { it.isAnnotationPresent(Test::class.java) }
                .sortedBy { it.name }
                .forEach { addChild(Description.createTestDescription(klass, it.name)) }
        }

    override fun getDescription(): Description = robolectric?.description ?: skipped

    override fun run(notifier: RunNotifier) {
        val runner = robolectric
        if (runner != null) {
            runner.run(notifier)
        } else {
            skipped.children.forEach(notifier::fireTestIgnored)
        }
    }

    override fun filter(filter: Filter) {
        robolectric?.filter(filter)
    }
}
