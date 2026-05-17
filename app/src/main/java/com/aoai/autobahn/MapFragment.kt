package com.aoai.autobahn

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

class MapFragment : Fragment(R.layout.fragment_map), OnMapReadyCallback {

    private var googleMap: GoogleMap? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentLocation: LatLng? = null
    private var selectedDestination: LatLng? = null
    private var destinationLabel: String = ""
    private var isFirstLoad = true
    private var mapLoaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        
        setFragmentResultListener("search_request") { _, bundle ->
            val lat = bundle.getDouble("lat")
            val lng = bundle.getDouble("lng")
            val title = bundle.getString("title") ?: ""
            setDestination(LatLng(lat, lng), title)
        }
    }

    private fun setDestination(latLng: LatLng, label: String) {
        selectedDestination = latLng
        destinationLabel = label
        googleMap?.let { map ->
            map.clear()
            map.addMarker(MarkerOptions().position(latLng).title(label.ifEmpty { "목적지" }))
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
            
            view?.findViewById<View>(R.id.card_navigation_actions)?.visibility = View.VISIBLE
            view?.findViewById<TextView>(R.id.tv_destination_name)?.text = label.ifEmpty { "선택된 위치" }
        }
    }

    private fun startGoogleNavigation(dest: LatLng, label: String) {
        val latStr = String.format(Locale.US, "%.6f", dest.latitude)
        val lngStr = String.format(Locale.US, "%.6f", dest.longitude)

        val navigationUri = "google.navigation:q=$latStr,$lngStr&mode=d".toUri()
        Log.d("ROUTE", "Starting Navigation: $label at $latStr,$lngStr")

        val mapIntent = Intent(Intent.ACTION_VIEW, navigationUri).apply {
            setPackage("com.google.android.apps.maps")
        }

        try {
            if (mapIntent.resolveActivity(requireContext().packageManager) != null) {
                startActivity(mapIntent)
            } else {
                val mapsUrl = "https://www.google.com/maps/dir/?api=1&destination=$latStr,$lngStr&travelmode=driving".toUri()
                startActivity(Intent(Intent.ACTION_VIEW, mapsUrl))
            }
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(requireContext(), "구글 지도 앱을 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ViewCompat.setOnApplyWindowInsetsListener(view) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(bottom = insets.bottom)
            windowInsets
        }

        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?
        if (mapFragment != null) {
            Log.d("MapFragment", "Map fragment found, calling getMapAsync")
            mapFragment.getMapAsync(this)
        } else {
            Log.e("MapFragment", "Map fragment not found in layout")
            Toast.makeText(requireContext(), "Map initialization failed", Toast.LENGTH_SHORT).show()
        }

        view.findViewById<View>(R.id.btn_search_container).setOnClickListener {
            findNavController().navigate(R.id.action_MapFragment_to_SearchFragment)
        }

        view.findViewById<View>(R.id.fab_my_location).setOnClickListener {
            getDeviceLocation(animate = true)
        }

        view.findViewById<Button>(R.id.btn_start_navigation).setOnClickListener {
            selectedDestination?.let { dest ->
                startGoogleNavigation(dest, destinationLabel)
            }
        }
    }


    override fun onMapReady(map: GoogleMap) {
        Log.d("MapFragment", "Map is ready!")
        googleMap = map
        try {
            // Listen for when the map finishes an initial render. If it doesn't render within
            // a short timeout we assume an authorization/tile loading problem (API key) and
            // offer the user a fallback to open the external Google Maps app.
            mapLoaded = false
            googleMap?.setOnMapLoadedCallback {
                mapLoaded = true
                Log.d("MapFragment", "Map rendered successfully")
            }

            // If the map doesn't finish loading within 4 seconds, show a helpful dialog.
            view?.postDelayed({
                if (!mapLoaded) {
                    Log.w("MapFragment", "Map did not finish loading in time — possible API auth issue")
                    showMapLoadFailureDialog()
                }
            }, 4000)

            updateLocationUI()
            getDeviceLocation(animate = false)
            
            // Auto-launch on first load if enabled
            if (isFirstLoad) {
                isFirstLoad = false
                checkAndAutoLaunch()
            }
        } catch (e: Exception) {
            Log.e("MapFragment", "Error initializing map: ${e.message}", e)
        }

        googleMap?.setOnMapLongClickListener { latLng ->
            // Check auto-launch setting
            val sharedPrefs = requireActivity().getSharedPreferences("settings", Context.MODE_PRIVATE)
            val autoLaunch = sharedPrefs.getBoolean("auto_launch_on_drive", false)
            val preferredApp = sharedPrefs.getString("preferred_navigation_app", null)

            if (autoLaunch && preferredApp != null) {
                val url = when (preferredApp) {
                    "google" -> "https://www.google.co.kr/maps"
                    "naver" -> "https://map.naver.com"
                    "kakao" -> "https://map.kakao.com/link/map/${latLng.latitude},${latLng.longitude}"
                    "atlan" -> "https://play.google.com/store/apps/details?id=kr.mappers.AtlanSmart&hl=ko"
                    else -> null
                }
                if (url != null) {
                    startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                }
            } else {
                // Default Kakao Map link
                val url = "https://map.kakao.com/link/map/${latLng.latitude},${latLng.longitude}"
                val intent = Intent(Intent.ACTION_VIEW, url.toUri())
                startActivity(intent)
            }

            lifecycleScope.launch {
                val addressInfo = findFullAddressInfo(requireContext(), latLng.latitude, latLng.longitude)
                val label = addressInfo?.first ?: ""
                setDestination(latLng, label)
            }
        }
    }

    private fun checkAndAutoLaunch() {
        // Use the same preference key as SettingsFragment so the toggle actually takes effect.
        val sharedPrefs = requireActivity().getSharedPreferences("settings", Context.MODE_PRIVATE)
        val autoLaunch = sharedPrefs.getBoolean("auto_launch_on_drive", false)

        // Show the prompt when the user enabled auto-launch on drive. Don't require currentLocation
        // because location may take a moment to become available; the prompt can still guide the user
        // to search for a destination immediately after app start.
        if (autoLaunch) {
            view?.postDelayed({
                showAutoLaunchPrompt()
            }, 1500)
        }
    }

    private fun showAutoLaunchPrompt() {
        AlertDialog.Builder(requireContext())
            .setTitle("Ready to Navigate")
            .setMessage("Would you like to search for a destination?")
            .setPositiveButton("Search") { _, _ ->
                findNavController().navigate(R.id.action_MapFragment_to_SearchFragment)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private suspend fun findFullAddressInfo(context: Context, lat: Double, lng: Double): Pair<String, LatLng>? = withContext(Dispatchers.IO) {
        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses: List<Address>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocation(lat, lng, 1) { result -> cont.resume(result) }
                }
            } else {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(lat, lng, 1)
            }
            
            addresses?.firstOrNull()?.let { addr ->
                val fullAddress = addr.getAddressLine(0) ?: addr.thoroughfare ?: ""
                Pair(fullAddress, LatLng(addr.latitude, addr.longitude))
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun showMapLoadFailureDialog() {
        // If user set always use external map, open external maps immediately
        val sharedPrefs = requireActivity().getSharedPreferences("settings", Context.MODE_PRIVATE)
        val alwaysExternal = sharedPrefs.getBoolean("always_use_external_map", false)
        if (alwaysExternal) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, "geo:0,0?q=".toUri())
                intent.setPackage("com.google.android.apps.maps")
                if (intent.resolveActivity(requireContext().packageManager) != null) startActivity(intent)
                else startActivity(Intent(Intent.ACTION_VIEW, "https://www.google.com/maps".toUri()))
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "외부 지도를 열 수 없습니다.", Toast.LENGTH_SHORT).show()
            }
            return
        }

        AlertDialog.Builder(requireContext())
            .setTitle("지도를 불러올 수 없습니다")
            .setMessage("앱 내 지도를 불러오지 못했습니다. Google Maps API 키 설정을 확인하거나 외부 지도 앱을 사용하세요.")
            .setPositiveButton("외부 지도 열기") { _, _ ->
                // Open Google Maps main app
                try {
                    val intent = Intent(Intent.ACTION_VIEW, "geo:0,0?q=".toUri())
                    intent.setPackage("com.google.android.apps.maps")
                    if (intent.resolveActivity(requireContext().packageManager) != null) startActivity(intent)
                    else startActivity(Intent(Intent.ACTION_VIEW, "https://www.google.com/maps".toUri()))
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "외부 지도를 열 수 없습니다.", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("도움말") { _, _ ->
                // Open documentation link or show instructions
                val url = "https://developers.google.com/maps/documentation/android-sdk/start"
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                } catch (_: Exception) { /* ignore */ }
            }
            .setNeutralButton("닫기", null)
            .show()
    }

    @SuppressLint("MissingPermission")
    private fun updateLocationUI() {
        if (googleMap == null) return
        if (hasLocationPermission()) {
            googleMap?.isMyLocationEnabled = true
            googleMap?.uiSettings?.isMyLocationButtonEnabled = false
        }
    }

    @SuppressLint("MissingPermission")
    private fun getDeviceLocation(animate: Boolean) {
        if (hasLocationPermission()) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    currentLocation = LatLng(location.latitude, location.longitude)
                    val cameraUpdate = CameraUpdateFactory.newLatLngZoom(currentLocation!!, 15f)
                    if (animate) googleMap?.animateCamera(cameraUpdate)
                    else googleMap?.moveCamera(cameraUpdate)
                }
            }
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
}


