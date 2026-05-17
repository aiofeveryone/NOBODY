package com.aoai.autobahn

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import java.security.MessageDigest

class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private var isCheckingUpdate = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvSelectedLanguage = view.findViewById<TextView>(R.id.tv_selected_language)
        val switchAutoLaunch = view.findViewById<SwitchCompat>(R.id.switch_auto_launch)
        val tvVersionInfo = view.findViewById<TextView>(R.id.tv_version_info)
        val btnUpdate = view.findViewById<Button>(R.id.btn_check_update)

        // Shared Preferences
        val sharedPrefs = requireActivity().getSharedPreferences("settings", Context.MODE_PRIVATE)

        // Load saved language
        val selectedCode = sharedPrefs.getString("language_code", "auto") ?: "auto"
        val selectedLabel = LanguageOptions.options.find { it.code == selectedCode }?.label ?: "Detect language"
        tvSelectedLanguage.text = selectedLabel

        // Load auto launch setting
        val isAutoLaunchEnabled = sharedPrefs.getBoolean("auto_launch_on_drive", false)
        switchAutoLaunch.isChecked = isAutoLaunchEnabled

        // Set version info
        val currentVersion = try {
            val pInfo: PackageInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            pInfo.versionName ?: "1.0.103"
        } catch (_: Exception) {
            "1.0.103"
        }
        tvVersionInfo.text = getString(R.string.app_name) + " v$currentVersion"

        // Handle language selection click
        view.findViewById<View>(R.id.layout_language).setOnClickListener {
            findNavController().navigate(R.id.action_SettingsFragment_to_LanguageSelectionFragment)
        }

        // Handle auto launch toggle
        switchAutoLaunch.setOnCheckedChangeListener { _, isChecked: Boolean ->
            sharedPrefs.edit {
                putBoolean("auto_launch_on_drive", isChecked)
            }
            Toast.makeText(
                requireContext(),
                if (isChecked) "Auto launch enabled" else "Auto launch disabled",
                Toast.LENGTH_SHORT
            ).show()
        }

        // Always use external map switch
        val switchAlwaysExternal = view.findViewById<SwitchCompat>(R.id.switch_always_external_map)
        val alwaysExternalEnabled = sharedPrefs.getBoolean("always_use_external_map", false)
        switchAlwaysExternal.isChecked = alwaysExternalEnabled
        switchAlwaysExternal.setOnCheckedChangeListener { _, checked: Boolean ->
            sharedPrefs.edit { putBoolean("always_use_external_map", checked) }
            Toast.makeText(requireContext(), if (checked) "외부 지도를 항상 사용합니다" else "앱 내 지도를 사용합니다", Toast.LENGTH_SHORT).show()
        }

        // Handle update click - Enhanced
        btnUpdate.setOnClickListener {
            if (!isCheckingUpdate) {
                checkAndUpdateApp(btnUpdate)
            }
        }

        // Map diagnostics button - show API key and app signing SHA1 for console registration
        view.findViewById<Button>(R.id.btn_map_diagnostics).setOnClickListener {
            val apiKey = try {
                val ai: ApplicationInfo = requireContext().packageManager.getApplicationInfo(requireContext().packageName, PackageManager.GET_META_DATA)
                val bundle = ai.metaData
                bundle?.getString("com.google.android.geo.API_KEY") ?: "(no key in manifest)"
            } catch (e: Exception) {
                "(unable to read manifest)"
            }

            // Get signing SHA1(s)
            val shaList = mutableListOf<String>()
            try {
                val pkg: PackageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    requireContext().packageManager.getPackageInfo(requireContext().packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                } else {
                    @Suppress("DEPRECATION")
                    requireContext().packageManager.getPackageInfo(requireContext().packageName, PackageManager.GET_SIGNATURES)
                }
                val sigs: Array<out Signature>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pkg.signingInfo?.apkContentsSigners else pkg.signatures
                sigs?.forEach { sig: Signature ->
                    val md = MessageDigest.getInstance("SHA1")
                    val sha1 = md.digest(sig.toByteArray()).joinToString(":") { String.format("%02X", it) }
                    shaList.add(sha1)
                }
            } catch (e: Exception) {
                // ignore
            }

            val message = StringBuilder()
            message.append("API Key: \n").append(apiKey).append("\n\n")
            message.append("Package: ").append(requireContext().packageName).append("\n\n")
            message.append("Signing SHA1(s):\n")
            if (shaList.isEmpty()) message.append("(not available)") else shaList.forEach { message.append(it).append("\n") }

            AlertDialog.Builder(requireContext())
                .setTitle("Map 진단 정보")
                .setMessage(message.toString())
                .setPositiveButton("복사") { _: DialogInterface, _: Int ->
                    val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("map_diagnostics", message.toString())
                    cm.setPrimaryClip(clip)
                    Toast.makeText(requireContext(), "복사되었습니다", Toast.LENGTH_SHORT).show()
                }
                .setNeutralButton("콘솔 열기") { _: DialogInterface, _: Int ->
                    val url = "https://console.cloud.google.com/apis/credentials"
                    try { startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) } catch (_: Exception) {}
                }
                .setNegativeButton("닫기", null)
                .show()
        }
    }

    private fun checkAndUpdateApp(btnUpdate: Button) {
        isCheckingUpdate = true
        btnUpdate.isEnabled = false

        val originalText = btnUpdate.text
        btnUpdate.text = getString(R.string.updating)

        // Simulate version check (in production, this would check a backend API)
        view?.postDelayed({
            // For demo, always offer update
            val playStoreUrl = "https://play.google.com/store/apps/details?id=${requireContext().packageName}"

            val intent = Intent(Intent.ACTION_VIEW, playStoreUrl.toUri())
            try {
                startActivity(intent)
                Toast.makeText(requireContext(), "Opening Play Store...", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Toast.makeText(requireContext(), "Unable to open Play Store", Toast.LENGTH_SHORT).show()
            }

            btnUpdate.text = originalText
            btnUpdate.isEnabled = true
            isCheckingUpdate = false
        }, 1000)
    }
}
