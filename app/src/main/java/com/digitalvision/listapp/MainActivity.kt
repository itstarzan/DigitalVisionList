package com.digitalvision.listapp

import android.app.*
import android.os.Bundle
import android.os.Environment
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.content.*
import android.net.Uri
import android.provider.MediaStore
import android.provider.DocumentsContract
import java.io.BufferedReader
import java.io.InputStreamReader
import android.view.*
import org.json.JSONArray
import org.json.JSONObject
import android.widget.*
import java.text.SimpleDateFormat
import java.util.*
import java.io.File
import androidx.core.content.FileProvider

data class Row(var item:String, var qty:Int)

class MainActivity : Activity() {
    private val rows = mutableListOf<Row>()
    private val prefs by lazy { getSharedPreferences("digital_vision_data", MODE_PRIVATE) }
    private val history = mutableListOf<String>()
    private lateinit var container: LinearLayout
    private lateinit var total: TextView
    private lateinit var shop: EditText
    private lateinit var orderTo: EditText
    private lateinit var date: TextView
    private lateinit var backupStatusView: TextView
    private var backupProgressDialog: AlertDialog? = null
    private val updateManifestUrl = "https://YOUR-GITHUB-USERNAME.github.io/YOUR-REPO/update.json"
    private val updateApkFileName = "DigitalVisionList-latest.apk"


    private val backupFolderKey = "backup_folder_uri"
    private val autoBackupKey = "auto_backup_enabled"
    private val lastBackupKey = "last_backup_time"
    private val lastBackupSuccessKey = "last_backup_success"
    private val pickBackupFolderCode = 4101
    private val pickBackupFileCode = 4102

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        loadHistory()
        buildUi()
        loadSaved()
        if (rows.isEmpty()) addRow() else render()
        performAutoBackupIfDue()
    }

    private var selectedTab = 0
    private var savedSortNewestFirst = true
    private lateinit var mainContent: FrameLayout
    private lateinit var bottomHome: TextView
    private lateinit var bottomSaved: TextView
    private lateinit var bottomSettings: TextView

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }
        mainContent = FrameLayout(this)
        root.addView(mainContent, LinearLayout.LayoutParams(-1, 0, 1f))

        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.WHITE)
            setPadding(8, 6, 8, 8)
            elevation = 10f
        }
        bottomHome = navItem("⌂", "Home")
        bottomSaved = navItem("▣", "Saved Files")
        bottomSettings = navItem("⚙", "Settings")
        nav.addView(bottomHome, LinearLayout.LayoutParams(0, 68, 1f))
        nav.addView(bottomSaved, LinearLayout.LayoutParams(0, 68, 1f))
        nav.addView(bottomSettings, LinearLayout.LayoutParams(0, 68, 1f))
        root.addView(nav)

        setContentView(root)
        showHome()
    }

    private fun navItem(icon: String, label: String): TextView {
        return TextView(this).apply {
            text = "$icon\n$label"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(Color.DKGRAY)
            setOnClickListener {
                when (label) {
                    "Home" -> showHome()
                    "Saved Files" -> showSavedFiles()
                    "Settings" -> showSettings()
                }
            }
        }
    }

    private fun refreshBottomNav() {
        val active = Color.rgb(190, 25, 25)
        val inactive = Color.DKGRAY
        bottomHome.setTextColor(if (selectedTab == 0) active else inactive)
        bottomSaved.setTextColor(if (selectedTab == 1) active else inactive)
        bottomSettings.setTextColor(if (selectedTab == 2) active else inactive)
    }

    private fun showHome() {
        selectedTab = 0
        refreshBottomNav()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 18, 24, 18)
            setBackgroundColor(Color.WHITE)
        }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content)

        val title = TextView(this).apply {
            text = "DIGITAL VISION LIST"
            textSize = 23f
            setTextColor(Color.rgb(180,25,25))
            gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
        }
        content.addView(title, lp(-1,55))

        shop = EditText(this).apply { setText("Digital Vision"); hint="Shop name"; textSize=17f }
        content.addView(shop, lp(-1,55))

        orderTo = EditText(this).apply {
            hint = "Order To"
            textSize = 16f
            singleLine = true
            setPadding(12, 0, 12, 0)
        }
        content.addView(orderTo, lp(-1,52))

        date = TextView(this).apply {
            text = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
            textSize=15f
            setTextColor(Color.DKGRAY)
            gravity=Gravity.CENTER_VERTICAL
        }
        content.addView(date, lp(-1,40))

        val header = LinearLayout(this)
        listHeader(header, "S.No.", 0.18f); listHeader(header,"Item",0.52f); listHeader(header,"Qty",0.30f)
        content.addView(header, lp(-1,48))

        container = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        content.addView(container, lp(-1, WRAP_CONTENT))

        total = TextView(this).apply { textSize=18f; setTypeface(null,Typeface.BOLD); gravity=Gravity.CENTER_VERTICAL }
        content.addView(total, lp(-1,52))

        val buttons = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        val add=button("+ Add Item"); add.setOnClickListener{addRow()}
        val clear=button("Clear"); clear.setOnClickListener{rows.clear(); addRow()}
        val export=button("Export JPG"); export.setOnClickListener{exportJpg()}
        buttons.addView(add, LinearLayout.LayoutParams(0,52,1f))
        buttons.addView(clear, LinearLayout.LayoutParams(0,52,1f))
        buttons.addView(export, LinearLayout.LayoutParams(0,52,1f))
        content.addView(buttons)

        val newList = button("New List")
        newList.setOnClickListener { startNewList() }
        content.addView(newList, lp(-1,52))

        root.addView(scroll, LinearLayout.LayoutParams(-1,0,1f))
        mainContent.removeAllViews()
        mainContent.addView(root, FrameLayout.LayoutParams(-1,-1))
        loadSaved()
        if (rows.isEmpty()) rows.add(Row("",1))
        render()
    }

    private fun showSavedFiles() {
        selectedTab = 1
        refreshBottomNav()
        loadHistory()
        savedSortNewestFirst = prefs.getBoolean("saved_sort_newest_first", true)

        val frame = FrameLayout(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24,18,24,18)
            setBackgroundColor(Color.WHITE)
        }
        val title = TextView(this).apply {
            text = "Saved Files"
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.rgb(180,25,25))
            gravity = Gravity.CENTER
        }
        root.addView(title, lp(-1,55))

        val search = EditText(this).apply {
            hint = "Search saved files..."
            singleLine = true
            setPadding(16,0,16,0)
        }
        root.addView(search, lp(-1,52))

        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(list) }
        root.addView(scroll, LinearLayout.LayoutParams(-1,0,1f))
        frame.addView(root, FrameLayout.LayoutParams(-1,-1))

        val sortButton = TextView(this).apply {
            text = "⇅"
            textSize = 27f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(190,25,25))
            }
            elevation = 8f
            setOnClickListener { showSortDialog { rebuildSavedFiles(list, search.text.toString()) } }
        }
        val fabLp = FrameLayout.LayoutParams(62,62,Gravity.END or Gravity.BOTTOM).apply { setMargins(0,0,22,22) }
        frame.addView(sortButton, fabLp)

        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                rebuildSavedFiles(list, s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        mainContent.removeAllViews()
        mainContent.addView(frame, FrameLayout.LayoutParams(-1,-1))
        rebuildSavedFiles(list, "")
    }

    private fun rebuildSavedFiles(list: LinearLayout, query: String) {
        loadHistory()
        list.removeAllViews()
        val indexed = history.mapIndexed { index, raw -> index to raw }
        val ordered = if (savedSortNewestFirst) indexed else indexed.reversed()
        var shown = 0
        for ((index, raw) in ordered) {
            try {
                val o = JSONObject(raw)
                val name = o.optString("historyName", o.optString("date", "Saved List"))
                val shopName = o.optString("shop", "Digital Vision")
                val order = o.optString("orderTo", "")
                val count = o.optJSONArray("rows")?.length() ?: 0
                val label = "$name $shopName $order"
                if (query.isNotBlank() && !label.contains(query, true)) continue

                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(14,10,8,10)
                    background = GradientDrawable().apply {
                        setColor(Color.rgb(248,248,248))
                        cornerRadius = 18f
                        setStroke(1, Color.rgb(225,225,225))
                    }
                    setOnClickListener { showHistoryActions(index) }
                }
                val info = TextView(this).apply {
                    text = "$name\n$count items${if (order.isNotBlank()) "  •  $order" else ""}"
                    textSize = 16f
                    setTextColor(Color.DKGRAY)
                    setPadding(8,0,8,0)
                }
                card.addView(info, LinearLayout.LayoutParams(0,72,1f))
                val menu = Button(this).apply {
                    text = "⋮"
                    textSize = 22f
                    setOnClickListener { showHistoryActions(index) }
                }
                card.addView(menu, LinearLayout.LayoutParams(54,58))
                list.addView(card, LinearLayout.LayoutParams(-1,82).apply { setMargins(0,0,0,10) })
                shown++
            } catch (_: Exception) {}
        }
        if (shown == 0) {
            list.addView(TextView(this).apply {
                text = if (history.isEmpty()) "No saved files yet." else "No saved files match your search."
                textSize = 16f
                gravity = Gravity.CENTER
                setTextColor(Color.GRAY)
                setPadding(10,40,10,40)
            }, lp(-1,120))
        }
    }

    private fun showSortDialog(onSorted: () -> Unit) {
        val options = arrayOf("New to old", "Old to new")
        val checked = if (savedSortNewestFirst) 0 else 1
        AlertDialog.Builder(this)
            .setTitle("Sort saved files")
            .setSingleChoiceItems(options, checked) { dialog, which ->
                savedSortNewestFirst = which == 0
                prefs.edit().putBoolean("saved_sort_newest_first", savedSortNewestFirst).apply()
                dialog.dismiss()
                onSorted()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showSettings() {
        selectedTab = 2
        refreshBottomNav()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24,18,24,18)
            setBackgroundColor(Color.WHITE)
        }
        val title = TextView(this).apply {
            text = "Settings"
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.rgb(180,25,25))
            gravity = Gravity.CENTER
        }
        root.addView(title, lp(-1,55))

        val backup = button("Google Drive Backup & Restore")
        backup.setOnClickListener { showBackupDialog() }
        root.addView(backup, lp(-1,56))

        val backupNowButton = button("☁  Backup Now")
        backupNowButton.setOnClickListener {
            val folder = prefs.getString(backupFolderKey, null)
            if (folder.isNullOrBlank()) chooseBackupFolder() else backupNow()
        }
        root.addView(backupNowButton, lp(-1,56))

        backupStatusView = TextView(this).apply {
            textSize = 14f
            setPadding(12, 8, 12, 12)
        }
        root.addView(backupStatusView, lp(-1,WRAP_CONTENT))
        updateBackupStatusView()

        val updateButton = button("App Update")
        updateButton.setOnClickListener { checkForUpdates(true) }
        root.addView(updateButton, lp(-1,56))

        val profile = button("Profile\nApp Developer: Biswajit Das")
        profile.setOnClickListener { showProfile() }
        root.addView(profile, lp(-1,72))

        mainContent.removeAllViews()
        mainContent.addView(root, FrameLayout.LayoutParams(-1,-1))
    }

    private fun showProfile() {
        AlertDialog.Builder(this)
            .setTitle("Profile")
            .setMessage("App Developer\n\nBiswajit Das")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun addRow() {
        rows.add(Row("", 1))
        render()
        saveCurrent()
    }

    private fun render() {
        if (!::container.isInitialized) return
        container.removeAllViews()

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(8, 8, 8, 8)
            setBackgroundColor(Color.rgb(245, 245, 245))
        }
        header.addView(cellText("SL", 0.8f, true))
        header.addView(cellText("Item", 3.8f, true))
        header.addView(cellText("Qty", 1.4f, true))
        header.addView(cellText("", 0.8f, true))
        container.addView(header)

        rows.forEachIndexed { index, r ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(4, 2, 4, 2)
            }

            val sl = TextView(this).apply {
                text = (index + 1).toString()
                gravity = Gravity.CENTER
                textSize = 15f
            }
            row.addView(sl, lp(0, 48, 0.8f))

            val item = EditText(this).apply {
                setText(r.item)
                hint = "Enter item"
                textSize = 15f
                singleLine = true
                inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                setPadding(8, 0, 8, 0)
                setOnFocusChangeListener { _, hasFocus ->
                    if (!hasFocus) {
                        r.item = text.toString()
                        saveCurrent()
                    }
                }
            }
            row.addView(item, lp(0, 48, 3.8f))

            val qty = EditText(this).apply {
                setText(r.qty.toString())
                hint = "0"
                textSize = 15f
                gravity = Gravity.CENTER
                singleLine = true
                inputType = android.text.InputType.TYPE_CLASS_NUMBER
                setPadding(4, 0, 4, 0)
                setOnFocusChangeListener { _, hasFocus ->
                    if (!hasFocus) {
                        r.qty = text.toString().toIntOrNull() ?: 0
                        if (text.toString().isBlank()) setText("0")
                        updateTotal()
                        saveCurrent()
                    }
                }
                addTextChangedListener(object : android.text.TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                        r.qty = s?.toString()?.toIntOrNull() ?: 0
                        updateTotal()
                    }
                    override fun afterTextChanged(s: android.text.Editable?) {}
                })
            }
            row.addView(qty, lp(0, 48, 1.4f))

            val delete = Button(this).apply {
                text = "×"
                textSize = 20f
                setPadding(0, 0, 0, 0)
                setOnClickListener {
                    if (rows.size > 1) rows.removeAt(index)
                    else rows[0] = Row("", 1)
                    render()
                    saveCurrent()
                }
            }
            row.addView(delete, lp(0, 48, 0.8f))
            container.addView(row)
        }
        updateTotal()
    }

    private fun cellText(textValue: String, weight: Float, bold: Boolean): TextView {
        return TextView(this).apply {
            text = textValue
            gravity = Gravity.CENTER
            textSize = 14f
            if (bold) setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, 44).apply { this.weight = weight }
        }
    }


    private fun saveCurrent() {
        if (!::shop.isInitialized || !::orderTo.isInitialized || !::date.isInitialized) return

        val oldRaw = prefs.getString("current", null)
        val oldObj = try { oldRaw?.let { JSONObject(it) } } catch (_: Exception) { null }

        val o = JSONObject()
        o.put("shop", shop.text.toString())
        o.put("orderTo", orderTo.text.toString())
        o.put("date", date.text.toString())

        val a = JSONArray()
        rows.forEach { r ->
            a.put(JSONObject().apply {
                put("item", r.item)
                put("qty", r.qty)
            })
        }
        o.put("rows", a)

        fun contentSignature(x: JSONObject?): String {
            if (x == null) return ""
            val sig = JSONObject()
            sig.put("shop", x.optString("shop", ""))
            sig.put("orderTo", x.optString("orderTo", ""))
            sig.put("date", x.optString("date", ""))
            sig.put("rows", x.optJSONArray("rows") ?: JSONArray())
            return sig.toString()
        }

        val unchanged = oldObj != null && contentSignature(oldObj) == contentSignature(o)
        val name = if (unchanged) {
            prefs.getString("current_history_name", oldObj?.optString("historyName", "") ?: "")
                ?.takeIf { it.isNotBlank() }
                ?: SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(Date())
        } else {
            SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(Date())
        }

        o.put("historyName", name)
        prefs.edit()
            .putString("current", o.toString())
            .putString("current_history_name", name)
            .apply()
    }

    private fun loadSaved() {
        val raw = prefs.getString("current", null) ?: return
        try {
            val o = JSONObject(raw)
            prefs.edit().putString("current_history_name", o.optString("historyName", o.optString("date",""))).apply()
            val a = o.optJSONArray("rows") ?: JSONArray()
            rows.clear()
            for (i in 0 until a.length()) {
                val r = a.getJSONObject(i)
                rows.add(Row(r.optString("item",""), r.optInt("qty",1)))
            }
            if (::shop.isInitialized) shop.setText(o.optString("shop","Digital Vision"))
            if (::orderTo.isInitialized) orderTo.setText(o.optString("orderTo",""))
            if (::date.isInitialized) date.text = o.optString("date", SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date()))
        } catch (_: Exception) { rows.clear() }
    }

    private fun loadHistory() {
        history.clear()
        try {
            val a = JSONArray(prefs.getString("history", "[]") ?: "[]")
            for (i in 0 until a.length()) history.add(a.getString(i))
        } catch (_: Exception) {}
    }

    private fun addHistorySnapshot() {
        saveCurrent()
        val raw = prefs.getString("current", null) ?: return
        history.add(0, raw)
        while (history.size > 100) history.removeAt(history.lastIndex)
        val a = JSONArray()
        history.forEach { a.put(it) }
        prefs.edit().putString("history", a.toString()).apply()
    }

    private fun startNewList() {
        if (rows.any { it.item.isNotBlank() }) addHistorySnapshot()
        rows.clear()
        shop.setText("Digital Vision")
        orderTo.setText("")
        date.text = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
        prefs.edit().remove("current_history_name").apply()
        addRow()
        Toast.makeText(this, "New list started. Previous list saved to History.", Toast.LENGTH_SHORT).show()
    }

    private fun showHistory() {
        loadHistory()
        if (history.isEmpty()) {
            AlertDialog.Builder(this).setTitle("History").setMessage("No previous lists yet.")
                .setPositiveButton("OK", null).show()
            return
        }

        val labels = history.mapIndexed { i, raw ->
            try {
                val o=JSONObject(raw)
                "${i+1}. ${o.optString("historyName", o.optString("date",""))} — ${o.optString("shop","Digital Vision")} — ${o.optJSONArray("rows")?.length() ?: 0} items"
            } catch (_: Exception) { "${i+1}. Previous list" }
        }.toTypedArray()

        AlertDialog.Builder(this).setTitle("Previous Lists")
            .setItems(labels) { _, which -> showHistoryActions(which) }
            .setNegativeButton("Close", null)
            .setNeutralButton("Select & Delete") { _, _ -> showSelectiveDelete() }
            .show()
    }

    private fun showHistoryActions(index: Int) {
        val options = arrayOf("Restore this list", "Delete this list")
        AlertDialog.Builder(this)
            .setTitle("Saved List")
            .setItems(options) { _, choice ->
                if (choice == 0) restoreHistory(index) else confirmDeleteHistory(index)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDeleteHistory(index: Int) {
        AlertDialog.Builder(this)
            .setTitle("Delete saved list?")
            .setMessage("This will permanently remove this list from History.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                if (index in history.indices) {
                    history.removeAt(index)
                    saveHistory()
                    Toast.makeText(this, "List deleted.", Toast.LENGTH_SHORT).show()
                    showHistory()
                }
            }.show()
    }

    private fun saveHistory() {
        val a = JSONArray()
        history.forEach { a.put(it) }
        prefs.edit().putString("history", a.toString()).apply()
    }

    private fun showSelectiveDelete() {
        if (history.isEmpty()) {
            Toast.makeText(this, "No saved lists.", Toast.LENGTH_SHORT).show()
            return
        }

        val search = EditText(this).apply {
            hint = "Search saved lists"
            singleLine = true
            setPadding(20, 10, 20, 10)
        }
        val list = ListView(this)
        val selected = BooleanArray(history.size)

        fun labelsFor(query: String): Array<String> {
            return history.mapIndexed { i, raw ->
                try {
                    val o=JSONObject(raw)
                    val label="${i+1}. ${o.optString("historyName", o.optString("date",""))} — ${o.optString("shop","Digital Vision")} — ${o.optString("orderTo","")} — ${o.optJSONArray("rows")?.length() ?: 0} items"
                    if (query.isBlank() || label.contains(query, true)) label else null
                } catch (_: Exception) { null }
            }.filterNotNull().toTypedArray()
        }

        fun rebuild(query: String) {
            val labels=labelsFor(query)
            val adapter=ArrayAdapter(this, android.R.layout.simple_list_item_multiple_choice, labels)
            list.adapter=adapter
            list.choiceMode=ListView.CHOICE_MODE_MULTIPLE
        }

        rebuild("")
        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { rebuild(s?.toString().orEmpty()) }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        list.setOnItemClickListener { _, _, _, _ ->
            // Selection is maintained by visible search results below when Delete is pressed.
        }

        val box=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(20,0,20,0)
            addView(search, LinearLayout.LayoutParams(-1,60))
            addView(list, LinearLayout.LayoutParams(-1,0,1f))
        }

        AlertDialog.Builder(this)
            .setTitle("Select lists to delete")
            .setView(box)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete Selected") { _, _ ->
                // Determine selected rows currently visible in the filtered list.
                val query=search.text.toString()
                val visible=history.mapIndexedNotNull { i, raw ->
                    try {
                        val o=JSONObject(raw)
                        val label="${i+1}. ${o.optString("historyName", o.optString("date",""))} — ${o.optString("shop","Digital Vision")} — ${o.optString("orderTo","")} — ${o.optJSONArray("rows")?.length() ?: 0} items"
                        if (query.isBlank() || label.contains(query,true)) i else null
                    } catch (_: Exception) { null }
                }
                val selectedIndices=visible.filterIndexed { pos, _ -> list.isItemChecked(pos) }
                if (selectedIndices.isEmpty()) {
                    Toast.makeText(this, "Select at least one list.", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                AlertDialog.Builder(this)
                    .setTitle("Delete ${selectedIndices.size} list(s)?")
                    .setMessage("The selected saved lists will be permanently removed from History.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Delete") { _, _ ->
                        selectedIndices.sortedDescending().forEach { history.removeAt(it) }
                        saveHistory()
                        Toast.makeText(this, "${selectedIndices.size} list(s) deleted.", Toast.LENGTH_SHORT).show()
                        showHistory()
                    }.show()
            }.show()
    }

    private fun restoreHistory(index: Int) {
        try {
            val o=JSONObject(history[index])
            rows.clear()
            val a=o.optJSONArray("rows") ?: JSONArray()
            for(i in 0 until a.length()) {
                val r=a.getJSONObject(i)
                rows.add(Row(r.optString("item",""), r.optInt("qty",1)))
            }
            shop.setText(o.optString("shop","Digital Vision"))
            orderTo.setText(o.optString("orderTo",""))
            date.text=o.optString("date", SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date()))
            prefs.edit().putString("current_history_name", o.optString("historyName", o.optString("date",""))).apply()
            render()
            saveCurrent()
            Toast.makeText(this, "Previous list restored.", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(this, "Could not restore this list.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateBackupStatusView() {
        if (!::backupStatusView.isInitialized) return

        val folder = prefs.getString(backupFolderKey, null)
        val connected = !folder.isNullOrBlank()
        val last = prefs.getLong(lastBackupKey, 0L)
        val success = prefs.getBoolean(lastBackupSuccessKey, false)

        backupStatusView.text = when {
            !connected -> "Backup status: Google Drive NOT CONNECTED"
            last == 0L -> "Backup status: Google Drive CONNECTED • No backup yet"
            success -> "Backup status: Google Drive CONNECTED • Last backup SUCCESS • " +
                    SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(last))
            else -> "Backup status: Google Drive CONNECTED • Last backup FAILED • " +
                    SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(last))
        }
    }

    private fun currentVersionCode(): Int {
        return try {
            packageManager.getPackageInfo(packageName, 0).longVersionCode.toInt()
        } catch (_: Exception) {
            1
        }
    }

    private fun currentVersionName(): String {
        return try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0"
        } catch (_: Exception) {
            "1.0"
        }
    }

    private fun checkForUpdates(showNoUpdate: Boolean) {
        AlertDialog.Builder(this)
            .setTitle("Checking for updates")
            .setMessage("Checking the latest Digital Vision List version…")
            .setView(ProgressBar(this).apply { isIndeterminate = true })
            .setCancelable(false)
            .create()
            .also { dialog ->
                dialog.show()
                Thread {
                    var latestCode = -1
                    var latestName = ""
                    var apkUrl = ""
                    var notes = ""
                    var error = ""

                    try {
                        val conn = URL(updateManifestUrl).openConnection() as HttpURLConnection
                        conn.connectTimeout = 8000
                        conn.readTimeout = 8000
                        conn.requestMethod = "GET"
                        conn.connect()
                        if (conn.responseCode !in 200..299) {
                            throw Exception("Update server returned HTTP ${conn.responseCode}.")
                        }
                        val body = conn.inputStream.bufferedReader().use { it.readText() }
                        val json = JSONObject(body)
                        latestCode = json.optInt("versionCode", -1)
                        latestName = json.optString("versionName", "")
                        apkUrl = json.optString("apkUrl", "")
                        notes = json.optString("releaseNotes", "")
                        if (latestCode < 0 || latestName.isBlank() || apkUrl.isBlank()) {
                            throw Exception("The update information is incomplete.")
                        }
                    } catch (e: Exception) {
                        error = e.message ?: "Could not check for updates."
                    }

                    runOnUiThread {
                        dialog.dismiss()

                        if (error.isNotBlank()) {
                            AlertDialog.Builder(this)
                                .setTitle("Update Check Failed")
                                .setMessage(
                                    "$error\n\n" +
                                    "Your current app is still working normally.\n" +
                                    "Internet connection and update-server settings can be checked later."
                                )
                                .setPositiveButton("OK", null)
                                .show()
                            return@runOnUiThread
                        }

                        if (latestCode <= currentVersionCode()) {
                            if (showNoUpdate) {
                                AlertDialog.Builder(this)
                                    .setTitle("You're up to date")
                                    .setMessage(
                                        "Digital Vision List ${currentVersionName()} is the latest available version."
                                    )
                                    .setPositiveButton("OK", null)
                                    .show()
                            }
                            return@runOnUiThread
                        }

                        val msg = buildString {
                            append("New version: $latestName\n")
                            append("Current version: ${currentVersionName()}\n\n")
                            if (notes.isNotBlank()) {
                                append("What's new:\n$notes\n\n")
                            }
                            append("Download and install the update?")
                        }

                        AlertDialog.Builder(this)
                            .setTitle("Update Available")
                            .setMessage(msg)
                            .setNegativeButton("Later", null)
                            .setPositiveButton("Update Now") { _, _ ->
                                downloadAndInstallUpdate(apkUrl)
                            }
                            .show()
                    }
                }.start()
            }
    }

    private fun downloadAndInstallUpdate(apkUrl: String) {
        try {
            val request = DownloadManager.Request(Uri.parse(apkUrl))
                .setTitle("Digital Vision List update")
                .setDescription("Downloading the latest app version…")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(
                    this,
                    Environment.DIRECTORY_DOWNLOADS,
                    updateApkFileName
                )
                .setMimeType("application/vnd.android.package-archive")
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)

            val manager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
            val id = manager.enqueue(request)

            Toast.makeText(
                this,
                "Update download started. Android will show it when ready.",
                Toast.LENGTH_LONG
            ).show()

            window.decorView.postDelayed({
                val query = DownloadManager.Query().setFilterById(id)
                val cursor = manager.query(query)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                        if (status == DownloadManager.STATUS_SUCCESSFUL) {
                            val localUri = Uri.parse(
                                it.getString(it.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
                            )
                            installApk(localUri)
                        } else if (status == DownloadManager.STATUS_FAILED) {
                            val reason = it.getInt(
                                it.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)
                            )
                            AlertDialog.Builder(this)
                                .setTitle("Update Download Failed")
                                .setMessage("Android could not download the update. Error code: $reason")
                                .setPositiveButton("OK", null)
                                .show()
                        } else {
                            window.decorView.postDelayed({ checkDownload(manager, id) }, 1000)
                        }
                    }
                }
            }, 1000)
        } catch (e: Exception) {
            AlertDialog.Builder(this)
                .setTitle("Update Error")
                .setMessage(e.message ?: "Could not start the update download.")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun checkDownload(manager: DownloadManager, id: Long) {
        val cursor = manager.query(DownloadManager.Query().setFilterById(id))
        cursor?.use {
            if (!it.moveToFirst()) return
            val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    val localUri = Uri.parse(
                        it.getString(it.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
                    )
                    installApk(localUri)
                }
                DownloadManager.STATUS_FAILED -> {
                    val reason = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                    AlertDialog.Builder(this)
                        .setTitle("Update Download Failed")
                        .setMessage("Android could not download the update. Error code: $reason")
                        .setPositiveButton("OK", null)
                        .show()
                }
                else -> window.decorView.postDelayed({ checkDownload(manager, id) }, 1000)
            }
        }
    }

    private fun installApk(downloadUri: Uri) {
        try {
            val fileUri = if (downloadUri.scheme == "file") {
                FileProvider.getUriForFile(
                    this,
                    "${BuildConfig.APPLICATION_ID}.fileprovider",
                    File(downloadUri.path!!)
                )
            } else {
                downloadUri
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(fileUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (e: Exception) {
            AlertDialog.Builder(this)
                .setTitle("Cannot Install Update")
                .setMessage(
                    "Android could not open the downloaded update.\n\n" +
                    "If prompted, allow this app to install updates from this source, then try again."
                )
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun showBackupDialog() {
        val folder = prefs.getString(backupFolderKey, null)
        val auto = prefs.getBoolean(autoBackupKey, true)
        val last = prefs.getLong(lastBackupKey, 0L)
        val success = prefs.getBoolean(lastBackupSuccessKey, false)

        val connected = !folder.isNullOrBlank()
        val statusText = when {
            !connected -> "Google Drive: NOT CONNECTED"
            last == 0L -> "Google Drive: CONNECTED\nLast backup: No backup yet"
            success -> "Google Drive: CONNECTED\nLast backup: SUCCESS\n" +
                    SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(last))
            else -> "Google Drive: CONNECTED\nLast backup: FAILED\n" +
                    SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(last))
        }

        val status = TextView(this).apply {
            textSize = 16f
            setPadding(30, 18, 30, 18)
            text = statusText
        }

        val autoSwitch = Switch(this).apply {
            text = "Automatic daily backup"
            textSize = 16f
            isChecked = auto
            setPadding(30, 5, 30, 5)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean(autoBackupKey, checked).apply()
            }
        }

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(status)
            addView(autoSwitch)
        }

        AlertDialog.Builder(this)
            .setTitle("Google Drive Backup")
            .setView(box)
            .setPositiveButton("Backup Now") { _, _ ->
                if (folder.isNullOrBlank()) chooseBackupFolder() else backupNow()
            }
            .setNeutralButton("Choose Folder") { _, _ -> chooseBackupFolder() }
            .setNegativeButton("Restore Backup") { _, _ -> chooseBackupFile() }
            .show()
    }

    private fun chooseBackupFolder() {
        val intent=Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply { addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) }
        startActivityForResult(intent,pickBackupFolderCode)
    }

    private fun chooseBackupFile() {
        val intent=Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type="application/json"; addCategory(Intent.CATEGORY_OPENABLE); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) }
        startActivityForResult(intent,pickBackupFileCode)
    }

    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(resultCode!=RESULT_OK || data?.data==null) return
        val uri=data.data!!
        try {
            if(requestCode==pickBackupFolderCode) {
                val takeFlags=data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                contentResolver.takePersistableUriPermission(uri,takeFlags)
                prefs.edit().putString(backupFolderKey,uri.toString()).apply()
                Toast.makeText(this,"Google Drive backup folder connected.",Toast.LENGTH_LONG).show()
                backupNow()
            } else if(requestCode==pickBackupFileCode) restoreFromUri(uri)
        } catch (_:Exception) { Toast.makeText(this,"Could not connect to the selected location.",Toast.LENGTH_LONG).show() }
    }

    private fun buildBackupJson():JSONObject {
        saveCurrent()
        return JSONObject().apply {
            put("format","DigitalVisionListBackup"); put("version",1)
            put("backedUpAt",SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.getDefault()).format(Date()))
            put("current",JSONObject(prefs.getString("current","{}") ?: "{}"))
            put("history",JSONArray(prefs.getString("history","[]") ?: "[]"))
        }
    }

    private fun backupNow() {
        val folderString = prefs.getString(backupFolderKey, null)
        if (folderString.isNullOrBlank()) {
            Toast.makeText(
                this,
                "Google Drive is not connected. Tap Choose Folder first.",
                Toast.LENGTH_LONG
            ).show()
            chooseBackupFolder()
            return
        }

        if (backupProgressDialog?.isShowing == true) return

        val progress = ProgressBar(this).apply {
            isIndeterminate = true
        }

        val message = TextView(this).apply {
            text = "Preparing backup…"
            textSize = 16f
            setPadding(35, 20, 35, 20)
        }

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(20, 5, 20, 5)
            addView(progress, LinearLayout.LayoutParams(55, 55))
            addView(message, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        }

        backupProgressDialog = AlertDialog.Builder(this)
            .setTitle("Backing up to Google Drive")
            .setView(box)
            .setCancelable(false)
            .create()
        backupProgressDialog?.show()

        Thread {
            var success = false
            var errorMessage = "Unknown backup error."
            try {
                runOnUiThread { message.text = "Preparing your list and history…" }
                val backup = buildBackupJson()

                runOnUiThread { message.text = "Creating backup file…" }
                val treeUri = Uri.parse(folderString)
                val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date())
                val fileName = "DigitalVision_Backup_$stamp.json"

                val fileUri = DocumentsContract.createDocument(
                    contentResolver,
                    treeUri,
                    "application/json",
                    fileName
                ) ?: throw Exception(
                    "Google Drive could not create the backup file. " +
                    "Make sure the selected folder is still available and writable."
                )

                runOnUiThread { message.text = "Uploading backup…" }
                contentResolver.openOutputStream(fileUri)?.use { out ->
                    out.write(backup.toString(2).toByteArray(Charsets.UTF_8))
                    out.flush()
                } ?: throw Exception(
                    "Google Drive did not allow the backup file to be opened for writing."
                )

                success = true
            } catch (e: SecurityException) {
                errorMessage = "Google Drive permission was lost. Please choose the backup folder again."
            } catch (e: java.io.IOException) {
                errorMessage = "Could not write to Google Drive. Check your internet connection and available Drive storage."
            } catch (e: Exception) {
                errorMessage = e.message?.takeIf { it.isNotBlank() }
                    ?: "The backup could not be completed."
            }

            runOnUiThread {
                val now = System.currentTimeMillis()
                prefs.edit()
                    .putLong(lastBackupKey, now)
                    .putBoolean(lastBackupSuccessKey, success)
                    .apply()
                updateBackupStatusView()

                backupProgressDialog?.dismiss()
                backupProgressDialog = null

                if (success) {
                    val time = SimpleDateFormat(
                        "dd/MM/yyyy HH:mm:ss",
                        Locale.getDefault()
                    ).format(Date(now))
                    AlertDialog.Builder(this)
                        .setTitle("Backup Successful")
                        .setMessage("Your Digital Vision data was backed up to Google Drive successfully.\n\nLast backup: $time")
                        .setPositiveButton("OK", null)
                        .show()
                } else {
                    AlertDialog.Builder(this)
                        .setTitle("Backup Failed")
                        .setMessage(
                            "$errorMessage\n\n" +
                            "Your local data has not been deleted or changed."
                        )
                        .setPositiveButton("OK", null)
                        .setNeutralButton("Choose Folder") { _, _ ->
                            chooseBackupFolder()
                        }
                        .show()
                }
            }
        }.start()
    }

    private fun performAutoBackupIfDue() {
        if(!prefs.getBoolean(autoBackupKey,true)) return
        if((prefs.getString(backupFolderKey,null) ?: "").isBlank()) return
        val last=prefs.getLong(lastBackupKey,0L); val dayMs=24L*60L*60L*1000L
        if(System.currentTimeMillis()-last < dayMs) return
        window.decorView.post { backupNow() }
    }

    private fun restoreFromUri(uri:Uri) {
        try {
            val text=contentResolver.openInputStream(uri)?.use { BufferedReader(InputStreamReader(it,Charsets.UTF_8)).readText() } ?: throw Exception()
            val backup=JSONObject(text)
            if(backup.optString("format")!="DigitalVisionListBackup") throw Exception()
            val current=backup.optJSONObject("current") ?: JSONObject()
            val historyArray=backup.optJSONArray("history") ?: JSONArray()
            prefs.edit().putString("current",current.toString()).putString("history",historyArray.toString()).putString("current_history_name",current.optString("historyName",current.optString("date",""))).apply()
            rows.clear(); val a=current.optJSONArray("rows") ?: JSONArray()
            for(i in 0 until a.length()){ val r=a.getJSONObject(i); rows.add(Row(r.optString("item",""),r.optInt("qty",1))) }
            shop.setText(current.optString("shop","Digital Vision")); orderTo.setText(current.optString("orderTo",""))
            date.text=current.optString("date",SimpleDateFormat("dd-MM-yyyy",Locale.getDefault()).format(Date()))
            loadHistory(); render()
            Toast.makeText(this,"Backup restored successfully.",Toast.LENGTH_LONG).show()
        } catch (_:Exception) { Toast.makeText(this,"Restore failed. Please select a valid Digital Vision backup.",Toast.LENGTH_LONG).show() }
    }

    private fun updateTotal(){ total.text="Total Quantity: ${rows.sumOf{it.qty}}" }

    private fun listHeader(l:LinearLayout,s:String,w:Float){
        val t=TextView(this).apply{text=s; textSize=16f; setTypeface(null,Typeface.BOLD); gravity=Gravity.CENTER; setTextColor(Color.WHITE); setBackgroundColor(Color.rgb(45,45,45))}
        l.addView(t,LinearLayout.LayoutParams(0,-1,w))
    }
    private fun button(s:String)=Button(this).apply{text=s; textSize=12f}

    private fun lp(w:Int,h:Int)=LinearLayout.LayoutParams(w,h)

    private fun exportJpg(){
        // Sync current editor values
        for(i in 1 until container.childCount){
            val row=container.getChildAt(i) as LinearLayout
            val rowIndex = i - 1
            if (rowIndex < rows.size) {
                rows[rowIndex].item=(row.getChildAt(1) as EditText).text.toString()
                rows[rowIndex].qty=(row.getChildAt(2) as EditText).text.toString().toIntOrNull()?:0
            }
        }
        saveCurrent()
        val width=1080
        val rowH=88
        val height=290 + maxOf(rows.size,1)*rowH
        val bmp=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
        val c=Canvas(bmp); c.drawColor(Color.WHITE)
        val p=Paint(Paint.ANTI_ALIAS_FLAG)
        p.color=Color.rgb(180,25,25); p.textSize=46f; p.typeface=Typeface.DEFAULT_BOLD
        c.drawText(shop.text.toString().ifBlank{"Digital Vision"},50f,70f,p)
        p.color=Color.DKGRAY; p.textSize=30f; p.typeface=Typeface.DEFAULT
        c.drawText("Date: ${date.text}",50f,115f,p)
        c.drawText("Order To: ${orderTo.text}",50f,150f,p)
        p.color=Color.rgb(45,45,45); c.drawRect(35f,175f,1045f,250f,p)
        p.color=Color.WHITE; p.textSize=30f; p.typeface=Typeface.DEFAULT_BOLD
        c.drawText("S.No.",65f,225f,p); c.drawText("Item",250f,225f,p); c.drawText("Qty",900f,225f,p)
        p.color=Color.BLACK; p.typeface=Typeface.DEFAULT; p.textSize=29f
        rows.forEachIndexed { i,r ->
            val y=305f+i*rowH
            c.drawText("${i+1}",80f,y,p); c.drawText(r.item.take(38),250f,y,p); c.drawText("${r.qty}",920f,y,p)
            p.color=0xFFE0E0E0.toInt(); c.drawRect(35f,y+20f,1045f,y+21f,p); p.color=Color.BLACK
        }
        p.typeface=Typeface.DEFAULT_BOLD; p.textSize=34f
        c.drawText("Total Quantity: ${rows.sumOf{it.qty}}",700f,(height-35).toFloat(),p)

        val safeDate = prefs.getString("current_history_name", SimpleDateFormat("dd/MM/yy",Locale.getDefault()).format(Date()))
            ?.replace("/", "-") ?: SimpleDateFormat("dd-MM-yy",Locale.getDefault()).format(Date())
        val name="DigitalVision_${safeDate}.jpg"
        val values=ContentValues().apply{put(MediaStore.Images.Media.DISPLAY_NAME,name);put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg");put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/Digital Vision")}
        val uri=contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values)
        uri?.let{
            contentResolver.openOutputStream(it)?.use{out->bmp.compress(Bitmap.CompressFormat.JPEG,95,out)}
            Toast.makeText(this,"JPG saved to Pictures/Digital Vision",Toast.LENGTH_LONG).show()
            val send=Intent(Intent.ACTION_SEND).apply{type="image/jpeg";putExtra(Intent.EXTRA_STREAM,it);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)}
            startActivity(Intent.createChooser(send,"Share JPG"))
        }
    }
}