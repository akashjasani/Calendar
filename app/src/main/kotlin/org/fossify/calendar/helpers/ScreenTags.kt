package org.fossify.calendar.helpers

import android.app.Activity
import android.app.Application
import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.annotation.IdRes
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.fossify.calendar.BuildConfig
import org.fossify.calendar.R
import org.fossify.calendar.activities.EventActivity
import org.fossify.calendar.activities.ManageCalendarsActivity
import org.fossify.calendar.activities.SelectTimeZoneActivity
import org.fossify.calendar.activities.SettingsActivity
import org.fossify.calendar.activities.TaskActivity
import org.fossify.calendar.activities.WidgetDateConfigureActivity
import org.fossify.calendar.activities.WidgetListConfigureActivity
import org.fossify.calendar.activities.WidgetMonthlyConfigureActivity
import org.fossify.calendar.fragments.DayFragmentsHolder
import org.fossify.calendar.fragments.EventListFragment
import org.fossify.calendar.fragments.MonthDayFragmentsHolder
import org.fossify.calendar.fragments.MonthFragmentsHolder
import org.fossify.calendar.fragments.WeekFragmentsHolder
import org.fossify.calendar.fragments.YearFragmentsHolder
import org.fossify.commons.activities.AboutActivity
import org.fossify.commons.activities.CustomizationActivity

/**
 * mqa screen tags (skill android-screen-tags). Debug and uat builds only: every entry point is a no-op when
 * BuildConfig.EXPOSE_TEST_TAGS is false, which it is in defaultConfig and release.
 *
 * A tag is a View id named `screen_tag_<Name>`, declared in res/values/mqa_screen_tags.xml and attached here at
 * run time to the root of a window's content. The names are resource names (literals), so R8 cannot rename them.
 */
object ScreenTags {
    /** Activities whose content root names the screen. */
    val activityTags: Map<Class<out Activity>, Int> = mapOf(
        EventActivity::class.java to R.id.screen_tag_EventActivity,
        TaskActivity::class.java to R.id.screen_tag_TaskActivity,
        SelectTimeZoneActivity::class.java to R.id.screen_tag_SelectTimeZoneActivity,
        SettingsActivity::class.java to R.id.screen_tag_SettingsActivity,
        ManageCalendarsActivity::class.java to R.id.screen_tag_ManageCalendarsActivity,
        WidgetMonthlyConfigureActivity::class.java to R.id.screen_tag_WidgetMonthlyConfigureActivity,
        WidgetListConfigureActivity::class.java to R.id.screen_tag_WidgetListConfigureActivity,
        WidgetDateConfigureActivity::class.java to R.id.screen_tag_WidgetDateConfigureActivity,
        AboutActivity::class.java to R.id.screen_tag_AboutActivity,
        CustomizationActivity::class.java to R.id.screen_tag_CustomizationActivity,
    )

    /** The view types of MainActivity. Each is a destination the user picks; its root view names the screen. */
    val fragmentTags: Map<Class<out Fragment>, Int> = mapOf(
        DayFragmentsHolder::class.java to R.id.screen_tag_DayFragmentsHolder,
        WeekFragmentsHolder::class.java to R.id.screen_tag_WeekFragmentsHolder,
        MonthFragmentsHolder::class.java to R.id.screen_tag_MonthFragmentsHolder,
        MonthDayFragmentsHolder::class.java to R.id.screen_tag_MonthDayFragmentsHolder,
        YearFragmentsHolder::class.java to R.id.screen_tag_YearFragmentsHolder,
        EventListFragment::class.java to R.id.screen_tag_EventListFragment,
    )

    fun register(app: Application) {
        if (!BuildConfig.EXPOSE_TEST_TAGS) return
        app.registerActivityLifecycleCallbacks(ActivityTagger)
    }

    /** Tags the dialog that will hold [view] (an own dialog's content), when that dialog is shown. */
    fun tagDialogOf(view: View, @IdRes id: Int) {
        if (!BuildConfig.EXPOSE_TEST_TAGS) return
        if (view.isAttachedToWindow) {
            tagWindowRoot(view.rootView, id)
            return
        }
        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                tagWindowRoot(v.rootView, id)
            }

            override fun onViewDetachedFromWindow(v: View) = Unit
        })
    }

    /** Tags a platform dialog (DatePickerDialog, TimePickerDialog). Call it after show(). */
    fun tagDialog(dialog: Dialog, @IdRes id: Int) {
        if (!BuildConfig.EXPOSE_TEST_TAGS) return
        dialog.window?.let { tagWindow(it, id) }
    }

    /** Tags a DialogFragment's window when it starts (its dialog is shown in onStart). */
    fun tagDialogFragment(fragment: DialogFragment, @IdRes id: Int) {
        if (!BuildConfig.EXPOSE_TEST_TAGS) return
        fragment.lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                fragment.dialog?.window?.let { tagWindow(it, id) }
            }
        })
    }

    private fun tagWindow(window: Window, @IdRes id: Int) = tagWindowRoot(window.decorView, id)

    /** The first child of android.R.id.content: the layout root, or an AlertDialog's whole panel (title, body, buttons). */
    private fun tagWindowRoot(decor: View, @IdRes id: Int) {
        val content = decor.findViewById<ViewGroup>(android.R.id.content) ?: return
        val root = if (content.childCount > 0) content.getChildAt(0) else return
        root.id = id
    }

    private object ActivityTagger : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            if (activity is FragmentActivity) {
                activity.supportFragmentManager.registerFragmentLifecycleCallbacks(FragmentTagger, false)
            }
        }

        // After onCreate: setContentView has run.
        override fun onActivityStarted(activity: Activity) {
            val id = activityTags[activity.javaClass] ?: return
            tagWindow(activity.window, id)
        }

        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }

    /**
     * The view types do not tag their own root: the pager roots carry a random id that keeps the page fragments
     * of an old and a new holder apart (they live in the activity's FragmentManager), so it must not change.
     * The tag goes on the parent of MainActivity's fragment container instead (the swipe-refresh layout, an id
     * nothing looks up). It holds exactly one view type at a time, so it names the one showing.
     */
    private object FragmentTagger : FragmentManager.FragmentLifecycleCallbacks() {
        override fun onFragmentViewCreated(fm: FragmentManager, f: Fragment, v: View, savedInstanceState: Bundle?) {
            val id = fragmentTags[f.javaClass] ?: return
            val container = f.activity?.findViewById<View>(R.id.fragments_holder) ?: return
            (container.parent as? View)?.id = id
        }
    }
}
