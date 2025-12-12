// File: advanced_searchbar/src/main/java/com/ext/advanced_searchbar/AdvancedSearchBar.kt
package com.ext.advanced_searchbar

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.speech.RecognizerIntent
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.res.ResourcesCompat
import kotlinx.coroutines.*

class AdvancedSearchBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private lateinit var containerCard: CardView
    private lateinit var searchEditText: EditText
    private lateinit var searchIcon: ImageView
    private lateinit var voiceIcon: ImageView
    private lateinit var clearIcon: ImageView

    private var searchJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var allItems: List<String> = emptyList()

    private var onSearchListener: ((String) -> Unit)? = null
    private var onTextChangedListener: ((String) -> Unit)? = null
    private var onSuggestionsFilteredListener: ((List<String>) -> Unit)? = null
    private var onVoiceResultListener: ((String) -> Unit)? = null

    private var voiceSearchLauncher: ActivityResultLauncher<Intent>? = null
    private var debounceTime: Long = 300L

    // Customization properties
    private var showSearchIcon: Boolean = true
    private var showVoiceIcon: Boolean = true
    private var searchBarBackgroundColor: Int = Color.WHITE
    private var searchBarCornerRadius: Float = 24f
    private var searchBarElevation: Float = 4f
    private var searchBarPadding: Int = 16
    private var searchBarMargin: Int = 0
    private var hintText: String = "Search..."
    private var textColor: Int = Color.BLACK
    private var hintColor: Int = Color.GRAY
    private var textSize: Float = 16f
    private var fontFamilyResId: Int = -1
    private var iconTint: Int = Color.GRAY
    private var iconSize: Int = 48
    private var searchIconSize: Int = -1
    private var voiceIconSize: Int = -1
    private var clearIconSize: Int = -1
    private var editTextPadding: Int = 16
    private var editTextPaddingStart: Int = -1
    private var editTextPaddingEnd: Int = -1

    init {
        loadAttributes(attrs)
        setupView()
        setupListeners()
    }

    private fun loadAttributes(attrs: AttributeSet?) {
        attrs?.let {
            val typedArray = context.obtainStyledAttributes(it, R.styleable.AdvancedSearchBar)

            // Icon visibility
            showSearchIcon =
                typedArray.getBoolean(R.styleable.AdvancedSearchBar_showSearchIcon, true)
            showVoiceIcon = typedArray.getBoolean(R.styleable.AdvancedSearchBar_showVoiceIcon, true)

            // Search bar style
            searchBarBackgroundColor = typedArray.getColor(
                R.styleable.AdvancedSearchBar_searchBarBackgroundColor,
                Color.WHITE
            )
            searchBarCornerRadius = typedArray.getDimension(
                R.styleable.AdvancedSearchBar_searchBarCornerRadius,
                dpToPx(24f)
            )
            searchBarElevation = typedArray.getDimension(
                R.styleable.AdvancedSearchBar_searchBarElevation,
                dpToPx(4f)
            )
            searchBarPadding = typedArray.getDimensionPixelSize(
                R.styleable.AdvancedSearchBar_searchBarPadding,
                dpToPx(16f).toInt()
            )
            searchBarMargin = typedArray.getDimensionPixelSize(
                R.styleable.AdvancedSearchBar_searchBarMargin,
                0
            )

            // Text style
            hintText = typedArray.getString(R.styleable.AdvancedSearchBar_hintText) ?: "Search..."
            textColor = typedArray.getColor(R.styleable.AdvancedSearchBar_textColor, Color.BLACK)
            hintColor = typedArray.getColor(R.styleable.AdvancedSearchBar_hintColor, Color.GRAY)
            textSize = typedArray.getDimension(
                R.styleable.AdvancedSearchBar_textSize,
                spToPx(16f)
            )

            if (typedArray.hasValue(R.styleable.AdvancedSearchBar_fontFamily)) {
                fontFamilyResId =
                    typedArray.getResourceId(R.styleable.AdvancedSearchBar_fontFamily, -1)
            }

            // Icon style
            iconTint = typedArray.getColor(R.styleable.AdvancedSearchBar_iconTint, Color.GRAY)
            iconSize = typedArray.getDimensionPixelSize(
                R.styleable.AdvancedSearchBar_iconSize,
                dpToPx(48f).toInt()
            )
            searchIconSize = typedArray.getDimensionPixelSize(
                R.styleable.AdvancedSearchBar_searchIconSize,
                -1
            )
            voiceIconSize = typedArray.getDimensionPixelSize(
                R.styleable.AdvancedSearchBar_voiceIconSize,
                -1
            )
            clearIconSize = typedArray.getDimensionPixelSize(
                R.styleable.AdvancedSearchBar_clearIconSize,
                -1
            )

            // EditText padding
            editTextPadding = typedArray.getDimensionPixelSize(
                R.styleable.AdvancedSearchBar_editTextPadding,
                dpToPx(16f).toInt()
            )
            editTextPaddingStart = typedArray.getDimensionPixelSize(
                R.styleable.AdvancedSearchBar_editTextPaddingStart,
                -1
            )
            editTextPaddingEnd = typedArray.getDimensionPixelSize(
                R.styleable.AdvancedSearchBar_editTextPaddingEnd,
                -1
            )

            // Functionality
            debounceTime =
                typedArray.getInt(R.styleable.AdvancedSearchBar_debounceTime, 300).toLong()

            typedArray.recycle()
        }
    }

    private fun setupView() {
        // Main container
        containerCard = CardView(context).apply {
            val lp = LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(searchBarMargin, searchBarMargin, searchBarMargin, searchBarMargin)
            }
            layoutParams = lp
            radius = searchBarCornerRadius
            cardElevation = searchBarElevation
            setCardBackgroundColor(searchBarBackgroundColor)
        }

        // Inner container for horizontal layout
        val innerLayout = LinearLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                searchBarPadding,
                searchBarPadding / 2,
                searchBarPadding,
                searchBarPadding / 2
            )
        }

        // Get individual icon sizes or use default
        val searchIconSizeFinal = if (searchIconSize > 0) searchIconSize else iconSize
        val voiceIconSizeFinal = if (voiceIconSize > 0) voiceIconSize else iconSize
        val clearIconSizeFinal = if (clearIconSize > 0) clearIconSize else iconSize

        // Search icon
        searchIcon = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(searchIconSizeFinal, searchIconSizeFinal)
            setImageResource(android.R.drawable.ic_menu_search)
            setColorFilter(iconTint)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            visibility = if (showSearchIcon) VISIBLE else GONE
        }

        // Search EditText
        val paddingStart = if (editTextPaddingStart > 0) editTextPaddingStart else editTextPadding
        val paddingEnd = if (editTextPaddingEnd > 0) editTextPaddingEnd else editTextPadding

        searchEditText = EditText(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
            hint = hintText
            setHintTextColor(hintColor)
            setTextColor(textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize)
            background = null
            setPadding(paddingStart, 0, paddingEnd, 0)
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            maxLines = 1

            // Apply font family if provided
            if (fontFamilyResId != -1) {
                try {
                    typeface = ResourcesCompat.getFont(context, fontFamilyResId)
                } catch (e: Exception) {
                    // Fallback to default
                }
            }
        }

        // Clear icon
        clearIcon = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(clearIconSizeFinal, clearIconSizeFinal)
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setColorFilter(iconTint)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            visibility = GONE
        }

        // Voice search icon
        voiceIcon = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(voiceIconSizeFinal, voiceIconSizeFinal)
            setImageResource(android.R.drawable.ic_btn_speak_now)
            setColorFilter(iconTint)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            visibility = if (showVoiceIcon) VISIBLE else GONE
        }

        innerLayout.addView(searchIcon)
        innerLayout.addView(searchEditText)
        innerLayout.addView(clearIcon)
        innerLayout.addView(voiceIcon)
        containerCard.addView(innerLayout)
        addView(containerCard)
    }

    private fun setupListeners() {
        // Text change with debounce
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString()
                clearIcon.visibility = if (query.isNotEmpty()) VISIBLE else GONE

                searchJob?.cancel()
                searchJob = scope.launch {
                    delay(debounceTime)
                    filterSuggestions(query)
                    onTextChangedListener?.invoke(query)
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        // Search action
        searchEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch()
                true
            } else false
        }

        // Clear button
        clearIcon.setOnClickListener {
            searchEditText.text.clear()
            onSuggestionsFilteredListener?.invoke(emptyList())
        }

        // Voice search
        voiceIcon.setOnClickListener {
            startVoiceSearch()
        }

        // Search icon click
        searchIcon.setOnClickListener {
            performSearch()
        }
    }

    private fun filterSuggestions(query: String) {
        if (query.isEmpty()) {
            onSuggestionsFilteredListener?.invoke(emptyList())
            return
        }

        val filteredItems = allItems.filter {
            it.contains(query, ignoreCase = true)
        }.take(10)

        onSuggestionsFilteredListener?.invoke(filteredItems)
    }

    private fun performSearch() {
        val query = searchEditText.text.toString()
        if (query.isNotEmpty()) {
            onSuggestionsFilteredListener?.invoke(emptyList())
            onSearchListener?.invoke(query)
        }
    }

    private fun startVoiceSearch() {
        voiceSearchLauncher?.let {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to search")
            }
            it.launch(intent)
        }
    }

    fun handleVoiceResult(resultCode: Int, data: Intent?) {
        if (resultCode == AppCompatActivity.RESULT_OK && data != null) {
            val results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            results?.firstOrNull()?.let { result ->
                searchEditText.setText(result)
                searchEditText.setSelection(result.length)
                onVoiceResultListener?.invoke(result)
                performSearch()
            }
        }
    }

    // Public API methods
    fun setSuggestions(items: List<String>) {
        allItems = items
    }

    fun setOnSearchListener(listener: (String) -> Unit) {
        onSearchListener = listener
    }

    fun setOnTextChangedListener(listener: (String) -> Unit) {
        onTextChangedListener = listener
    }

    fun setOnSuggestionsFilteredListener(listener: (List<String>) -> Unit) {
        onSuggestionsFilteredListener = listener
    }

    fun setOnVoiceResultListener(listener: (String) -> Unit) {
        onVoiceResultListener = listener
    }

    fun setVoiceSearchLauncher(launcher: ActivityResultLauncher<Intent>) {
        voiceSearchLauncher = launcher
    }

    fun setDebounceTime(millis: Long) {
        debounceTime = millis
    }

    fun setShowSearchIcon(show: Boolean) {
        showSearchIcon = show
        searchIcon.visibility = if (show) VISIBLE else GONE
    }

    fun setShowVoiceIcon(show: Boolean) {
        showVoiceIcon = show
        voiceIcon.visibility = if (show) VISIBLE else GONE
    }

    fun setSearchBarBackgroundColor(color: Int) {
        searchBarBackgroundColor = color
        containerCard.setCardBackgroundColor(color)
    }

    fun setSearchBarCornerRadius(radius: Float) {
        searchBarCornerRadius = radius
        containerCard.radius = radius
    }

    fun setSearchBarElevation(elevation: Float) {
        searchBarElevation = elevation
        containerCard.cardElevation = elevation
    }

    fun setSearchBarPadding(padding: Int) {
        searchBarPadding = padding
        val innerLayout = containerCard.getChildAt(0) as? LinearLayout
        innerLayout?.setPadding(padding, padding / 2, padding, padding / 2)
    }

    fun setHintText(hint: String) {
        hintText = hint
        searchEditText.hint = hint
    }

    fun setTextColor(color: Int) {
        textColor = color
        searchEditText.setTextColor(color)
    }

    fun setTextSize(size: Float) {
        textSize = spToPx(size)
        searchEditText.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize)
    }

    fun setFontFamily(fontResId: Int) {
        try {
            searchEditText.typeface = ResourcesCompat.getFont(context, fontResId)
        } catch (e: Exception) {
            // Fallback to default
        }
    }

    fun setFontFamily(typeface: Typeface) {
        searchEditText.typeface = typeface
    }

    fun setIconTint(color: Int) {
        iconTint = color
        searchIcon.setColorFilter(color)
        voiceIcon.setColorFilter(color)
        clearIcon.setColorFilter(color)
    }

    fun setIconSize(size: Int) {
        iconSize = size
        val sizePx = dpToPx(size.toFloat()).toInt()

        searchIcon.layoutParams = (searchIcon.layoutParams as LinearLayout.LayoutParams).apply {
            width = sizePx
            height = sizePx
        }
        voiceIcon.layoutParams = (voiceIcon.layoutParams as LinearLayout.LayoutParams).apply {
            width = sizePx
            height = sizePx
        }
        clearIcon.layoutParams = (clearIcon.layoutParams as LinearLayout.LayoutParams).apply {
            width = sizePx
            height = sizePx
        }
    }

    fun setEditTextPadding(start: Int, end: Int) {
        searchEditText.setPadding(start, 0, end, 0)
    }

    fun getSearchText(): String = searchEditText.text.toString()

    fun setSearchText(text: String) {
        searchEditText.setText(text)
        searchEditText.setSelection(text.length)
    }

    fun clearSearch() {
        searchEditText.text.clear()
        onSuggestionsFilteredListener?.invoke(emptyList())
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        scope.cancel()
    }

    // Helper functions
    private fun dpToPx(dp: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        )
    }

    private fun spToPx(sp: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            sp,
            context.resources.displayMetrics
        )
    }
}