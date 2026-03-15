package com.aoai.autobahn

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController

class SettingsFragment : Fragment(R.layout.fragment_settings) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvSelectedLanguage = view.findViewById<TextView>(R.id.tv_selected_language)
        val switchAutoLaunch = view.findViewById<SwitchCompat>(R.id.switch_auto_launch)
        
        // Shared Preferences
        val sharedPrefs = requireActivity().getSharedPreferences("settings", Context.MODE_PRIVATE)

        // Load saved language
        val selectedCode = sharedPrefs.getString("language_code", "auto") ?: "auto"
        val selectedLabel = LanguageOptions.options.find { it.code == selectedCode }?.label ?: "Detect language"
        tvSelectedLanguage.text = selectedLabel

        // Load auto launch setting
        val isAutoLaunchEnabled = sharedPrefs.getBoolean("auto_launch_on_drive", false)
        switchAutoLaunch.isChecked = isAutoLaunchEnabled

        // Handle language selection click
        view.findViewById<View>(R.id.layout_language).setOnClickListener {
            findNavController().navigate(R.id.action_SettingsFragment_to_LanguageSelectionFragment)
        }

        // Handle auto launch toggle
        switchAutoLaunch.setOnCheckedChangeListener { _, isChecked ->
            sharedPrefs.edit().putBoolean("auto_launch_on_drive", isChecked).apply()
        }
    }
}
