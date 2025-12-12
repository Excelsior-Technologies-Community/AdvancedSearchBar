package com.ext.advancedsearchbar

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ext.advanced_searchbar.AdvancedSearchBar

class MainActivity : AppCompatActivity() {

    private lateinit var searchBar: AdvancedSearchBar
    private lateinit var suggestionsRecyclerView: RecyclerView
    private lateinit var suggestionsAdapter: SuggestionsAdapter
    private lateinit var suggestionsCard: View
    private lateinit var emptyHint: View

    // Voice search launcher
    private val voiceSearchLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        searchBar.handleVoiceResult(result.resultCode, result.data)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupViews()
        setupSearchBar()
        setupSuggestionsRecyclerView()

        // Show all suggestions initially
        showAllSuggestions()
    }

    private fun setupViews() {
        suggestionsCard = findViewById(R.id.suggestionsCard)
        emptyHint = findViewById(R.id.emptyHint)
    }

    private fun setupSearchBar() {
        searchBar = findViewById(R.id.advancedSearchBar)

        val sampleItems = listOf(
            "Android Development",
            "Android Studio",
            "Android Jetpack",
            "Android Architecture Components",
            "Kotlin Programming",
            "Kotlin Coroutines",
            "Kotlin Flow",
            "Java Programming",
            "JavaScript",
            "Python",
            "Python Django",
            "React Native",
            "React JS",
            "Flutter",
            "Flutter Widgets",
            "Mobile Development",
            "Web Development",
            "Machine Learning",
            "Artificial Intelligence",
            "Data Science",
            "Cloud Computing",
            "DevOps",
            "Docker",
            "Kubernetes",
            "Firebase",
            "REST API",
            "GraphQL",
            "MongoDB",
            "PostgreSQL",
            "MySQL"
        )

        searchBar.setSuggestions(sampleItems)
        searchBar.setVoiceSearchLauncher(voiceSearchLauncher)
        searchBar.setDebounceTime(300)

        // This listener will be called on every text change (after debounce) and initially
        searchBar.setOnSuggestionsFilteredListener { suggestions ->
            if (searchBar.getSearchText().isEmpty()) {
                // When query is empty → show all
                showAllSuggestions()
            } else {
                // When typing → show filtered results
                if (suggestions.isEmpty()) {
                    hideSuggestions()
                    emptyHint.visibility = View.VISIBLE
                } else {
                    showFilteredSuggestions(suggestions)
                    emptyHint.visibility = View.GONE
                }
            }
        }

        searchBar.setOnSearchListener {
            hideSuggestions()
            emptyHint.visibility = View.GONE
            // Perform actual search
        }

        searchBar.setOnTextChangedListener { text ->
            // Optional: extra handling
        }
    }

    private fun setupSuggestionsRecyclerView() {
        suggestionsRecyclerView = findViewById(R.id.suggestionsRecyclerView)
        suggestionsAdapter = SuggestionsAdapter { selectedItem ->
            searchBar.setSearchText(selectedItem)
            hideSuggestions()
            emptyHint.visibility = View.GONE
        }

        suggestionsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = suggestionsAdapter
        }
    }

    private fun showAllSuggestions() {
        val allItems = (searchBar as? AdvancedSearchBar)?.let {
            // Access internal allItems via reflection not needed — we know them from setup
            // But since setSuggestions triggers filter with empty query, it's already handled
            // Just ensure UI is visible
            suggestionsAdapter.updateSuggestions(getCurrentAllSuggestions())
        }
        suggestionsCard.visibility = View.VISIBLE
        emptyHint.visibility = View.GONE
        suggestionsRecyclerView.alpha = 1f
    }

    private fun showFilteredSuggestions(suggestions: List<String>) {
        suggestionsAdapter.updateSuggestions(suggestions)
        suggestionsCard.visibility = View.VISIBLE
        emptyHint.visibility = View.GONE

        if (suggestionsRecyclerView.alpha < 1f) {
            suggestionsRecyclerView.alpha = 0f
            suggestionsRecyclerView.animate().alpha(1f).setDuration(200).start()
        }
    }

    private fun hideSuggestions() {
        suggestionsCard.visibility = View.GONE
        suggestionsAdapter.clearSuggestions()
    }

    // Helper: get current full list (you can also store it in a variable if preferred)
    private fun getCurrentAllSuggestions(): List<String> {
        // Since we don't expose allItems publicly, we can keep a local copy
        // Alternatively, add a getter in AdvancedSearchBar if needed
        return listOf(
            "Android Development",
            "Android Studio",
            "Android Jetpack",
            "Android Architecture Components",
            "Kotlin Programming",
            "Kotlin Coroutines",
            "Kotlin Flow",
            "Java Programming",
            "JavaScript",
            "Python",
            "Python Django",
            "React Native",
            "React JS",
            "Flutter",
            "Flutter Widgets",
            "Mobile Development",
            "Web Development",
            "Machine Learning",
            "Artificial Intelligence",
            "Data Science",
            "Cloud Computing",
            "DevOps",
            "Docker",
            "Kubernetes",
            "Firebase",
            "REST API",
            "GraphQL",
            "MongoDB",
            "PostgreSQL",
            "MySQL"
        )
    }
}