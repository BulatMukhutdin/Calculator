package tat.mukhutdinov.calculator

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @get:Rule
    var activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun returnCorrectNum_onSum() {
        onView(withId(R.id.first))
            .perform(typeText("999"))

        onView(withId(R.id.second))
            .perform(typeText("111"), closeSoftKeyboard())

        onView(withId(R.id.calculate))
            .perform(click())

        onView(withId(R.id.result))
            .check(matches(withText("888")))
    }
}
