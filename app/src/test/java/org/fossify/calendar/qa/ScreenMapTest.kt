package org.fossify.calendar.qa

import org.fossify.calendar.helpers.ScreenTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The mqa screen map (src/qa/assets/mqa-screens.json) must list every screen the code has, and every screen
 * must be tagged. A new activity or view type fails here until it is in the map, the ids and ScreenTags.
 */
class ScreenMapTest {
    /** Activities that show no screen of their own, with the reason. */
    private val notScreens = mapOf(
        "SplashActivity" to "no UI: it forwards to MainActivity, EventActivity or TaskActivity",
        "MainActivity" to "a host: its view types (the *FragmentsHolder and EventListFragment) are the screens",
        "SnoozeReminderActivity" to "transparent: it shows only a Fossify Commons dialog (untagged)",
        "CalendarPickerActivity" to "transparent: it shows only a Fossify Commons dialog (untagged)",
    )

    private val activitiesInManifest: Set<String>
        get() = Regex("""<activity\s+android:name="([^"]+)"""")
            .findAll(File("src/main/AndroidManifest.xml").readText())
            .map { it.groupValues[1].substringAfterLast('.') }
            .toSet()

    private val viewTypesInCode: Set<String>
        get() = File("src/main/kotlin/org/fossify/calendar/fragments").listFiles().orEmpty()
            .flatMap { Regex("""class (\w+) : MyFragmentHolder\(""").findAll(it.readText()).map { m -> m.groupValues[1] } }
            .toSet()

    private val tagsInMap: Set<String>
        get() = Regex(""""tag":\s*"(\w+)"""")
            .findAll(File("src/qa/assets/mqa-screens.json").readText())
            .map { it.groupValues[1] }
            .toSet()

    private val declaredIds: Set<String>
        get() = Regex("""name="screen_tag_([\w.]+)"""")
            .findAll(File("src/main/res/values/mqa_screen_tags.xml").readText())
            .map { it.groupValues[1] }
            .toSet()

    @Test
    fun everyScreenInCodeIsInTheMap() {
        val inCode = (activitiesInManifest + viewTypesInCode) - notScreens.keys
        assertEquals(inCode.toSortedSet(), tagsInMap.toSortedSet())
    }

    @Test
    fun everyScreenInTheMapIsTagged() {
        val tagged = (ScreenTags.activityTags.keys + ScreenTags.fragmentTags.keys).map { it.simpleName }.toSet()
        assertEquals(tagsInMap.toSortedSet(), tagged.toSortedSet())
        assertTrue("ids missing: ${tagsInMap - declaredIds}", declaredIds.containsAll(tagsInMap))
    }
}
