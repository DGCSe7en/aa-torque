package com.aatorque.prefs

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.appcompat.content.res.AppCompatResources
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import com.aatorque.stats.R
import com.aatorque.stats.TorqueServiceWrapper
import kotlinx.coroutines.launch
import timber.log.Timber

class SettingsDashboard: PreferenceFragmentCompat() {

    lateinit var performanceTitle: EditTextPreference
    lateinit var mainCat: PreferenceCategory
    lateinit var optionsCat: PreferenceCategory

    val clockText = arrayOf(
        R.string.pref_leftclock,
        R.string.pref_centerclock,
        R.string.pref_rightclock,
    )
    val clockIcon = arrayOf(
        R.drawable.ic_settings_clockl,
        R.drawable.ic_settings_clockc,
        R.drawable.ic_settings_clockr,
    )
    val displayIcon = arrayOf(
        R.drawable.ic_settings_view1,
        R.drawable.ic_settings_view2,
        R.drawable.ic_settings_view3,
        R.drawable.ic_settings_view4,
    )
    val displayText = arrayOf(
        R.string.pref_view1,
        R.string.pref_view2,
        R.string.pref_view3,
        R.string.pref_view4,
    )
    var mBound = false

    var torqueConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            mBound = true
            val torqueService = (service as TorqueServiceWrapper.LocalBinder).getService()
            torqueService.loadPidInformation(false) {
                    pids ->
                activity?.let{
                        it.runOnUiThread {
                        mainCat.title = resources.getText(R.string.pref_dataelementsettings_1).replace("1".toRegex(), (dashboardIndex() + 1).toString())
                        val dbIndex = dashboardIndex()
                        lifecycleScope.launch {
                            requireContext().dataStore.data.collect { userPreference ->
                                val screen = userPreference.getScreens(dbIndex)
                                performanceTitle.text = screen.title
                                optionsCat.removeAll()
                                val sources = arrayOf(screen.gaugesList, screen.displaysList)
                                val texts = arrayOf(clockText, displayText)
                                val icons = arrayOf(clockIcon, displayIcon)
                                arrayOf("clock", "display").forEachIndexed { i, type ->
                                    sources[i].forEachIndexed { j, screen ->
                                        optionsCat.addPreference(
                                            Preference(requireContext()).also {
                                                it.key = "${type}_${dbIndex}_${j}"
                                                it.summary = pids.firstOrNull { pid ->
                                                    "torque_${pid.first}" == screen.pid
                                                }?.second?.get(0) ?: ""
                                                it.title = requireContext().getString(texts[i][j])
                                                it.icon = AppCompatResources.getDrawable(
                                                    requireContext(),
                                                    icons[i][j]
                                                )
                                                it.fragment =
                                                    SettingsPIDFragment::class.java.canonicalName
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            mBound = false
        }
    }

    fun dashboardIndex(): Int {
        return requireArguments().getCharSequence("prefix")?.split("_")!!.last().toInt()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mBound = TorqueServiceWrapper.runStartIntent(requireContext(), torqueConnection)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.display_setting)
        mainCat = findPreference("displayCat")!!
        optionsCat = findPreference("displayOptions")!!
        performanceTitle = findPreference("performanceTitle")!!
        performanceTitle.summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()
        performanceTitle.setOnPreferenceChangeListener {
                _, newValue ->
            lifecycleScope.launch {
                requireContext().dataStore.updateData {
                    val screen = it.getScreens(dashboardIndex()).toBuilder().setTitle(newValue as String)
                    return@updateData it.toBuilder().setScreens(dashboardIndex(), screen).build()
                }
            }
            return@setOnPreferenceChangeListener true
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (mBound) {
            try {
                requireActivity().unbindService(torqueConnection)
            } catch (e: IllegalArgumentException) {
                Timber.e("Failed to unbind service", e)
            }
            mBound = false
        }
    }

}