package com.aoai.autobahn

import android.content.Context
import android.content.Intent
import android.location.Address
import android.location.Geocoder
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aoai.autobahn.databinding.FragmentSearchBinding
import com.google.android.gms.maps.model.LatLng
import java.util.Locale

class SearchFragment : Fragment() {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: SearchResultAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = SearchResultAdapter { address ->
            val latLng = LatLng(address.latitude, address.longitude)
            
            val title = getBestTitle(address)
            
            // Send result back to MapFragment
            setFragmentResult("search_request", bundleOf(
                "lat" to latLng.latitude,
                "lng" to latLng.longitude,
                "title" to title
            ))
            findNavController().popBackStack()
        }

        binding.recyclerResults.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerResults.adapter = adapter

        // Handle Search button click -> Perform actual search
        binding.btnDoSearch.setOnClickListener {
            performSearch()
        }

        // Handle Keyboard Search action -> Open Naver Map
        binding.editSearch.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard(v)
                openNaverMap()
                true
            } else {
                false
            }
        }
    }

    private fun openNaverMap() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://map.naver.com"))
        startActivity(intent)
    }

    private fun performSearch() {
        val query = binding.editSearch.text.toString()
        if (query.isNotBlank()) {
            searchLocation(query)
        } else {
            Toast.makeText(requireContext(), "Please enter a destination", Toast.LENGTH_SHORT).show()
        }
    }

    private fun hideKeyboard(view: View) {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun searchLocation(query: String) {
        try {
            val geocoder = Geocoder(requireContext(), Locale.getDefault())
            val results = geocoder.getFromLocationName(query, 10)
            if (!results.isNullOrEmpty()) {
                // Log results to help debug missing place names
                results.forEach { addr ->
                    Log.d("SearchFragment", "Result: feature=${addr.featureName}, address=${addr.getAddressLine(0)}, premises=${addr.premises}")
                }
                adapter.submitList(results)
            } else {
                Toast.makeText(requireContext(), "No results found", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Error searching: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        /**
         * Logic to determine a good title for a location.
         * Tries to avoid showing just a house number if a more descriptive name or address is available.
         */
        fun getBestTitle(address: Address): String {
            val feature = address.featureName
            val addressLine = (address.getAddressLine(0) ?: "")
                .replace("대한민국 ", "")
                .replace("South Korea ", "")
                .trim()

            if (feature.isNullOrBlank()) return addressLine.ifBlank { "Selected Location" }

            // Check if feature name is likely just a house number (digits and common separators like dash or slash)
            // Or if it matches exactly the subThoroughfare (building number)
            val isHouseNumber = feature.matches(Regex("[\\d\\-\\s/]+")) || 
                               feature == address.subThoroughfare ||
                               (feature.length <= 4 && feature.any { it.isDigit() })

            return if (isHouseNumber) {
                // If feature is just a number, try premises (building name) if available
                if (!address.premises.isNullOrBlank()) {
                    address.premises
                } else if (addressLine.isNotBlank()) {
                    addressLine
                } else {
                    feature
                }
            } else {
                feature
            }
        }
    }

    private class SearchResultAdapter(private val onItemSelected: (Address) -> Unit) :
        RecyclerView.Adapter<SearchResultAdapter.ViewHolder>() {

        private var items: List<Address> = emptyList()

        fun submitList(newList: List<Address>) {
            items = newList
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_2, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.bind(item, onItemSelected)
        }

        override fun getItemCount(): Int = items.size

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val text1: TextView = view.findViewById(android.R.id.text1)
            private val text2: TextView = view.findViewById(android.R.id.text2)

            fun bind(item: Address, onItemSelected: (Address) -> Unit) {
                val title = getBestTitle(item)
                val fullAddress = (item.getAddressLine(0) ?: "")
                    .replace("대한민국 ", "")
                    .replace("South Korea ", "")
                    .trim()

                text1.text = title
                // Show address as subtitle if it's different from the title
                text2.text = if (title != fullAddress && fullAddress.isNotEmpty()) fullAddress else ""

                itemView.setOnClickListener { onItemSelected(item) }
            }
        }
    }
}
