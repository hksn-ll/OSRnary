package com.example.gabai

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import io.noties.markwon.Markwon
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryActivity : AppCompatActivity() {

    private val pageSize: Long = 10L
    private val historyItems = mutableListOf<HistoryItem>()
    private val favWordSet = mutableSetOf<String>()
    private var lastVisibleSnapshot: DocumentSnapshot? = null
    private var isLoading = false
    private var hasMorePages = true
    private var lastClickTime = 0L

    private var activeCardLoading: GabAiLoadingView? = null

    private lateinit var adapter: HistoryAdapter
    private lateinit var markwon: Markwon
    private lateinit var rvHistory: RecyclerView
    private lateinit var loadingContainer: View
    private lateinit var emptyContainer: ViewGroup

    data class HistoryItem(
        val docId: String,
        val word: String,
        val explanation: String,
        val originalContext: String,
        val partOfSpeech: String,
        val phonetics: String,
        val inSentenceRole: String,
        val timestamp: Long,
        val rawData: Map<String, Any>?
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        markwon = Markwon.create(this)

        // Setup Frosted Header Backdrop Blur
        val blurHeader = findViewById<FastBlurView>(R.id.blur_header_history)
        val blurTarget = findViewById<FastBlurTarget>(R.id.blur_target_history)
        GabAIUtils.setupBlurView(blurHeader, blurTarget)

        // Insets handling for header
        val header = findViewById<View>(R.id.history_header)
        ViewCompat.setOnApplyWindowInsetsListener(header) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, systemBars.top + 12, v.paddingRight, v.paddingBottom)
            insets
        }

        // Back button with instant tap
        val btnBack = findViewById<ImageButton>(R.id.btn_back)
        btnBack?.setOnClickListener {
            GabAIUtils.performHaptic(it, HapticFeedbackConstants.CLOCK_TICK)
            finish()
        }

        // View references
        rvHistory = findViewById(R.id.rv_history)
        loadingContainer = findViewById(R.id.history_loading_container)
        emptyContainer = findViewById(R.id.history_empty_container)

        // Setup RecyclerView
        val layoutManager = LinearLayoutManager(this)
        rvHistory.layoutManager = layoutManager
        adapter = HistoryAdapter()
        rvHistory.adapter = adapter

        // Pagination Scroll Listener (10 cards at a time)
        rvHistory.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val totalCount = layoutManager.itemCount
                val lastPos = layoutManager.findLastVisibleItemPosition()
                if (!isLoading && hasMorePages && lastPos >= totalCount - 2) {
                    loadNextPage()
                }
            }
        })

        loadInitialData()

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            FirebaseFirestore.getInstance().collection("users").document(uid)
                .update("quests_completed", FieldValue.arrayUnion("history"))
        }
    }

    override fun onResume() {
        super.onResume()
        // Instantly reset loading indicator on the clicked card without re-rendering all items
        activeCardLoading?.visibility = View.GONE
        activeCardLoading = null
    }

    private fun loadInitialData() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            showEmptyState()
            return
        }

        historyItems.clear()
        lastVisibleSnapshot = null
        hasMorePages = true
        isLoading = true

        loadingContainer.visibility = View.VISIBLE
        emptyContainer.visibility = View.GONE
        rvHistory.visibility = View.GONE

        val db = FirebaseFirestore.getInstance()

        // Fetch favorites in background to populate star icons as soon as ready
        db.collection("users").document(uid).collection("favorites").get()
            .addOnSuccessListener { favDocs ->
                favWordSet.clear()
                for (doc in favDocs) {
                    favWordSet.add(doc.id)
                }
                adapter.notifyDataSetChanged()
            }

        // Fetch first 10 items of scan history immediately in parallel
        db.collection("users").document(uid).collection("history")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(pageSize)
            .get()
            .addOnSuccessListener { historyDocs ->
                isLoading = false
                loadingContainer.visibility = View.GONE

                if (historyDocs.isEmpty) {
                    hasMorePages = false
                    showEmptyState()
                    return@addOnSuccessListener
                }

                for (doc in historyDocs) {
                    historyItems.add(parseHistoryItem(doc))
                }

                lastVisibleSnapshot = historyDocs.documents.lastOrNull()
                hasMorePages = historyDocs.size() >= pageSize

                rvHistory.visibility = View.VISIBLE
                emptyContainer.visibility = View.GONE
                adapter.notifyDataSetChanged()
            }
            .addOnFailureListener { e ->
                isLoading = false
                loadingContainer.visibility = View.GONE
                GabAIUtils.showSnackbar(this, "Failed to load history: ${e.message}")
                if (historyItems.isEmpty()) showEmptyState()
            }
    }

    private fun loadNextPage() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val lastSnap = lastVisibleSnapshot ?: return
        if (isLoading || !hasMorePages) return

        isLoading = true
        val db = FirebaseFirestore.getInstance()

        db.collection("users").document(uid).collection("history")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .startAfter(lastSnap)
            .limit(pageSize)
            .get()
            .addOnSuccessListener { docs ->
                isLoading = false
                if (docs.isEmpty) {
                    hasMorePages = false
                    adapter.notifyDataSetChanged()
                    return@addOnSuccessListener
                }

                val startPos = historyItems.size
                for (doc in docs) {
                    historyItems.add(parseHistoryItem(doc))
                }

                lastVisibleSnapshot = docs.documents.lastOrNull()
                hasMorePages = docs.size() >= pageSize

                adapter.notifyItemRangeInserted(startPos, docs.size())
            }
            .addOnFailureListener {
                isLoading = false
                hasMorePages = false
                adapter.notifyDataSetChanged()
            }
    }

    private fun parseHistoryItem(doc: DocumentSnapshot): HistoryItem {
        return HistoryItem(
            docId = doc.id,
            word = doc.getString("word") ?: "",
            explanation = doc.getString("explanation") ?: "",
            originalContext = doc.getString("originalContext") ?: (doc.getString("word") ?: ""),
            partOfSpeech = doc.getString("partOfSpeech") ?: "",
            phonetics = doc.getString("phonetics") ?: "",
            inSentenceRole = doc.getString("inSentenceRole") ?: "",
            timestamp = doc.getLong("timestamp") ?: 0L,
            rawData = doc.data
        )
    }

    private fun showEmptyState() {
        rvHistory.visibility = View.GONE
        emptyContainer.removeAllViews()
        val emptyView = layoutInflater.inflate(R.layout.view_empty_state, emptyContainer, false)
        emptyView.findViewById<ImageView>(R.id.iv_empty_icon).setImageResource(R.drawable.ic_insights)
        emptyView.findViewById<TextView>(R.id.tv_empty_title).text = "No Reading History Yet"
        emptyView.findViewById<TextView>(R.id.tv_empty_subtitle).text =
            "Words and sentences you scan and explore with GabAI will appear here for spaced repetition."
        emptyContainer.addView(emptyView)
        emptyContainer.visibility = View.VISIBLE
    }

    private fun formatDate(timestamp: Long): String {
        if (timestamp <= 0L) return ""
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        return when {
            diff < 60_000L -> "Just now"
            diff < 3600_000L -> "${diff / 60_000L}m ago"
            diff < 86400_000L -> "${diff / 3600_000L}h ago"
            diff < 172800_000L -> "Yesterday"
            else -> {
                val sdf = SimpleDateFormat("MMM d", Locale.getDefault())
                sdf.format(Date(timestamp))
            }
        }
    }

    // =========================================================================
    // RECYCLERVIEW ADAPTER WITH PAGINATED FOOTER
    // =========================================================================
    private inner class HistoryAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private val viewTypeItem = 0
        private val viewTypeLoading = 1

        override fun getItemViewType(position: Int): Int {
            return if (hasMorePages && position == historyItems.size) viewTypeLoading else viewTypeItem
        }

        override fun getItemCount(): Int {
            return if (hasMorePages && historyItems.isNotEmpty()) historyItems.size + 1 else historyItems.size
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (viewType == viewTypeLoading) {
                val v = LayoutInflater.from(parent.context).inflate(R.layout.item_list_loading_footer, parent, false)
                LoadingViewHolder(v)
            } else {
                val v = LayoutInflater.from(parent.context).inflate(R.layout.item_history, parent, false)
                ItemViewHolder(v)
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            if (holder is ItemViewHolder) {
                holder.bind(historyItems[position])
            }
        }

        inner class LoadingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

        inner class ItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val clickArea: View = itemView.findViewById(R.id.history_click_area)
            private val tvWord: TextView = itemView.findViewById(R.id.history_text)
            private val tvPosBadge: TextView = itemView.findViewById(R.id.history_pos_badge)
            private val tvDate: TextView = itemView.findViewById(R.id.history_date)
            private val tvSnippet: TextView = itemView.findViewById(R.id.history_snippet)
            private val roleContainer: View = itemView.findViewById(R.id.history_role_container)
            private val tvRole: TextView = itemView.findViewById(R.id.history_in_sentence_role)
            private val btnFav: ImageButton = itemView.findViewById(R.id.btn_add_to_fav)
            private val btnRemove: ImageButton = itemView.findViewById(R.id.btn_remove_history)
            private val cardLoading: GabAiLoadingView = itemView.findViewById(R.id.history_card_loading)

            fun bind(item: HistoryItem) {
                tvWord.text = item.word
                cardLoading.visibility = View.GONE

                // POS Badge
                if (item.partOfSpeech.isNotBlank()) {
                    tvPosBadge.text = item.partOfSpeech.uppercase()
                    tvPosBadge.visibility = View.VISIBLE
                } else {
                    tvPosBadge.visibility = View.GONE
                }

                // Date badge
                val formattedDate = formatDate(item.timestamp)
                if (formattedDate.isNotBlank()) {
                    tvDate.text = formattedDate
                    tvDate.visibility = View.VISIBLE
                } else {
                    tvDate.visibility = View.GONE
                }

                // Definition snippet (ensure clicks pass through to the card)
                markwon.setMarkdown(tvSnippet, item.explanation)
                tvSnippet.movementMethod = null
                tvSnippet.isClickable = false
                tvSnippet.isLongClickable = false
                tvSnippet.isFocusable = false

                // In-Sentence Role snippet
                val isAllSelected = item.word.trim().equals(item.originalContext.trim(), ignoreCase = true) ||
                        item.word.split(Regex("\\s+")).size > 6
                if (!isAllSelected && item.inSentenceRole.isNotBlank()) {
                    tvRole.text = item.inSentenceRole
                    roleContainer.visibility = View.VISIBLE
                } else {
                    roleContainer.visibility = View.GONE
                }

                // Star Button: Toggle Favorite Status directly
                val isFav = favWordSet.contains(item.word)
                btnFav.setImageResource(if (isFav) R.drawable.ic_star_filled else R.drawable.ic_star_outline)

                btnFav.setOnClickListener {
                    GabAIUtils.performHaptic(btnFav, HapticFeedbackConstants.CLOCK_TICK)
                    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@setOnClickListener
                    val db = FirebaseFirestore.getInstance()
                    val currentlyFav = favWordSet.contains(item.word)

                    if (currentlyFav) {
                        db.collection("users").document(uid).collection("favorites").document(item.word)
                            .delete()
                            .addOnSuccessListener {
                                favWordSet.remove(item.word)
                                btnFav.setImageResource(R.drawable.ic_star_outline)
                                GabAIUtils.showSnackbar(this@HistoryActivity, "Removed \"${item.word}\" from Favorites")
                            }
                    } else {
                        val favEntry = hashMapOf(
                            "word" to item.word,
                            "definition" to item.explanation,
                            "timestamp" to System.currentTimeMillis(),
                            "originalContext" to item.originalContext,
                            "partOfSpeech" to item.partOfSpeech,
                            "phonetics" to item.phonetics,
                            "inSentenceRole" to item.inSentenceRole
                        )
                        db.collection("users").document(uid).collection("favorites").document(item.word)
                            .set(favEntry)
                            .addOnSuccessListener {
                                favWordSet.add(item.word)
                                btnFav.setImageResource(R.drawable.ic_star_filled)
                                GabAIUtils.performHaptic(btnFav, HapticFeedbackConstants.CONFIRM)
                                GabAIUtils.showSnackbar(this@HistoryActivity, "Saved \"${item.word}\" to Favorites! ⭐")
                                QuestManager.addProgress(this@HistoryActivity, QuestManager.QUEST_SAVE)
                                db.collection("users").document(uid).update(
                                    "quests_completed",
                                    FieldValue.arrayUnion("save")
                                )
                            }
                    }
                }

                // Satisfying bouncy spring press effect on entire card
                val openOverviewAction = {
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastClickTime >= 600) {
                        lastClickTime = now

                        GabAIUtils.performHaptic(itemView, HapticFeedbackConstants.VIRTUAL_KEY)
                        cardLoading.visibility = View.VISIBLE
                        activeCardLoading = cardLoading

                        val intent = Intent(this@HistoryActivity, OverviewActivity::class.java).apply {
                            putExtra("SELECTED_TEXT", item.word)
                            putExtra("SURROUNDING_SENTENCE", item.originalContext)
                            putExtra("PRELOADED_EXPLANATION", item.explanation)
                            putExtra("PART_OF_SPEECH", item.partOfSpeech)
                            putExtra("PHONETICS", item.phonetics)
                            putExtra("IN_SENTENCE_ROLE", item.inSentenceRole)
                            putExtra("IS_FAVORITE", favWordSet.contains(item.word))
                        }
                        startActivity(intent)
                    }
                }

                GabAIUtils.addSpringPressEffect(itemView) { openOverviewAction() }
                clickArea.isClickable = false
                clickArea.isFocusable = false
                tvSnippet.isClickable = false
                tvSnippet.isFocusable = false

                // Delete from History with 4-second Undo SnackBar
                btnRemove.setOnClickListener {
                    GabAIUtils.performHaptic(btnRemove, HapticFeedbackConstants.CLOCK_TICK)
                    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@setOnClickListener
                    val db = FirebaseFirestore.getInstance()
                    val pos = adapterPosition
                    if (pos < 0 || pos >= historyItems.size) return@setOnClickListener

                    val removedItem = historyItems[pos]
                    historyItems.removeAt(pos)
                    notifyItemRemoved(pos)

                    val docRef = db.collection("users").document(uid).collection("history").document(removedItem.docId)
                    docRef.delete().addOnSuccessListener {
                        GabAIUtils.showUndoSnackbar(rvHistory, "Removed \"${removedItem.word}\" from history", "Undo") {
                            if (removedItem.rawData != null) {
                                docRef.set(removedItem.rawData).addOnSuccessListener {
                                    loadInitialData()
                                    GabAIUtils.showSnackbar(this@HistoryActivity, "Restored \"${removedItem.word}\"")
                                }
                            }
                        }
                    }

                    if (historyItems.isEmpty()) {
                        showEmptyState()
                    }
                }
            }
        }
    }
}