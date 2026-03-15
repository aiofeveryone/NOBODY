package com.aoai.autobahn

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class LanguageSelectionFragment : Fragment(R.layout.fragment_language_selection) {

    private lateinit var adapter: LanguageAdapter
    private var allLanguages = LanguageOptions.options

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val sharedPrefs = requireActivity().getSharedPreferences("settings", Context.MODE_PRIVATE)
        val selectedCode = sharedPrefs.getString("language_code", "auto") ?: "auto"

        val rvLanguages = view.findViewById<RecyclerView>(R.id.rv_languages)
        val etSearch = view.findViewById<EditText>(R.id.et_search_language)

        adapter = LanguageAdapter(selectedCode) { selectedLanguage ->
            sharedPrefs.edit().putString("language_code", selectedLanguage.code).apply()
            findNavController().popBackStack()
        }

        rvLanguages.layoutManager = LinearLayoutManager(requireContext())
        rvLanguages.adapter = adapter
        adapter.submitList(allLanguages)

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val filtered = if (s.isNullOrBlank()) {
                    allLanguages
                } else {
                    allLanguages.filter { it.label.contains(s, ignoreCase = true) }
                }
                adapter.submitList(filtered)
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private class LanguageAdapter(
        private val selectedCode: String,
        private val onSelect: (LanguageOption) -> Unit
    ) : RecyclerView.Adapter<LanguageAdapter.ViewHolder>() {

        private var items: List<LanguageOption> = emptyList()

        fun submitList(newList: List<LanguageOption>) {
            items = newList
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_language, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.bind(item, item.code == selectedCode, onSelect)
        }

        override fun getItemCount(): Int = items.size

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val tvName: TextView = view.findViewById(R.id.tv_language_name)
            private val tvCheck: TextView = view.findViewById(R.id.tv_check)

            fun bind(item: LanguageOption, isSelected: Boolean, onSelect: (LanguageOption) -> Unit) {
                tvName.text = item.label
                tvCheck.visibility = if (isSelected) View.VISIBLE else View.GONE
                itemView.setOnClickListener { onSelect(item) }
            }
        }
    }
}
