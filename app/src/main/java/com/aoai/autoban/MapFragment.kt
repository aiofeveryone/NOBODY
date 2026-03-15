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
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
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
            view?.findViewById<Button>(R.id.btn_start_navigation)?.visibility = View.VISIBLE
        }
    }

    private fun startGoogleNavigation(dest: LatLng, label: String) {
        val latStr = String.format(Locale.US, "%.6f", dest.latitude)
        val lngStr = String.format(Locale.US, "%.6f", dest.longitude)

        val navigationUri = Uri.parse("google.navigation:q=$latStr,$lngStr&mode=d")
        Log.d("ROUTE", "Starting Navigation: $label at $latStr,$lngStr")

        val mapIntent = Intent(Intent.ACTION_VIEW, navigationUri).apply {
            setPackage("com.google.android.apps.maps")
        }

        try {
            if (mapIntent.resolveActivity(requireContext().packageManager) != null) {
                startActivity(mapIntent)
            } else {
                val mapsUrl = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$latStr,$lngStr&travelmode=driving")
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
        mapFragment?.getMapAsync(this)

        view.findViewById<View>(R.id.btn_search).setOnClickListener {
            showSearchDestinationDialog()
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

    private fun showSearchDestinationDialog() {
        val items = arrayOf(
            "Google Maps",
            "Naver Map",
            "Kakao Map",
            "Atlan Navigation"
        )

        AlertDialog.Builder(requireContext())
            .setTitle("Search Destination")
            .setItems(items) { _, which ->
                val url = when (which) {
                    0 -> "https://www.google.co.kr/maps"
                    1 -> "https://map.naver.com"
                    2 -> "https://map.kakao.com"
                    3 -> "https://play.google.com/store/apps/details?id=kr.mappers.AtlanSmart&hl=ko"
                    else -> ""
                }

                if (url.isNotEmpty()) {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    startActivity(intent)
                }

                // Save preferred app key for auto-launch logic
                val appKey = when(which) {
                    0 -> "google"
                    1 -> "naver"
                    2 -> "kakao"
                    3 -> "atlan"
                    else -> null
                }
                if (appKey != null) {
                    requireActivity().getSharedPreferences("settings", Context.MODE_PRIVATE)
                        .edit().putString("preferred_navigation_app", appKey).apply()
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        updateLocationUI()
        getDeviceLocation(animate = false)

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
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
            } else {
                // Default Kakao Map link as before
                val url = "https://map.kakao.com/link/map/${latLng.latitude},${latLng.longitude}"
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                startActivity(intent)
            }

            lifecycleScope.launch {
                val addressInfo = findFullAddressInfo(requireContext(), latLng.latitude, latLng.longitude)
                val label = addressInfo?.first ?: ""
                setDestination(latLng, label)
            }
        }
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
