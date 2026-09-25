package com.digitalvision.listapp

import android.app.*
import android.os.Bundle
import android.os.Build
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
import kotlin.math.roundToInt
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.net.HttpURLConnection
import androidx.core.content.FileProvider

private class MaterialIconView(context: Context, private val kind: String) : View(context) {
    var iconColor: Int = Color.rgb(31,43,55)
        set(value) { field=value; invalidate() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap=Paint.Cap.ROUND; strokeJoin=Paint.Join.ROUND }
    private fun d(v:Float)=v*resources.displayMetrics.density
    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val w=width.toFloat(); val h=height.toFloat(); val s=minOf(w,h)/24f; val ox=(w-24f*s)/2f; val oy=(h-24f*s)/2f
        c.save(); c.translate(ox,oy); c.scale(s,s)
        paint.color=iconColor; paint.style=Paint.Style.STROKE; paint.strokeWidth=2f
        when(kind) {
            "menu" -> { c.drawLine(3f,6f,21f,6f,paint); c.drawLine(3f,12f,21f,12f,paint); c.drawLine(3f,18f,21f,18f,paint) }
            "add_circle" -> { c.drawCircle(12f,12f,9f,paint); c.drawLine(8f,12f,16f,12f,paint); c.drawLine(12f,8f,12f,16f,paint) }
            "add" -> { c.drawLine(5f,12f,19f,12f,paint); c.drawLine(12f,5f,12f,19f,paint) }
            "list" -> { paint.style=Paint.Style.FILL; c.drawCircle(5f,7f,1.5f,paint); c.drawCircle(5f,12f,1.5f,paint); c.drawCircle(5f,17f,1.5f,paint); c.drawRect(9f,6f,20f,8f,paint); c.drawRect(9f,11f,20f,13f,paint); c.drawRect(9f,16f,20f,18f,paint) }
            "edit" -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2.0f
                c.rotate(-45f, 12f, 12f)
                c.drawLine(8f, 17f, 17f, 8f, paint)
                c.drawLine(7f, 18f, 9f, 16f, paint)
                c.drawLine(15.5f, 6.5f, 17.5f, 8.5f, paint)
            }
            "delete" -> { c.drawRect(6f,7f,18f,20f,paint); c.drawLine(9f,4f,15f,4f,paint); c.drawLine(5f,6f,19f,6f,paint); c.drawLine(10f,10f,10f,17f,paint); c.drawLine(14f,10f,14f,17f,paint) }
            "save" -> { paint.style=Paint.Style.FILL; c.drawRect(4f,3f,20f,21f,paint); paint.color=Color.WHITE; c.drawRect(7f,5f,17f,10f,paint); c.drawCircle(12f,16f,3f,paint) }
            "export" -> { c.drawRect(5f,5f,15f,19f,paint); c.drawLine(11f,13f,20f,4f,paint); c.drawLine(14f,4f,20f,4f,paint); c.drawLine(20f,4f,20f,10f,paint) }
            "print" -> { c.drawRect(7f,3f,17f,8f,paint); c.drawRect(5f,8f,19f,16f,paint); c.drawRect(7f,14f,17f,21f,paint) }
            "share" -> { paint.style=Paint.Style.FILL; c.drawCircle(6f,12f,2.5f,paint); c.drawCircle(17f,6f,2.5f,paint); c.drawCircle(17f,18f,2.5f,paint); paint.style=Paint.Style.STROKE; c.drawLine(8f,11f,15f,7f,paint); c.drawLine(8f,13f,15f,17f,paint) }
            "home" -> { val p=Path(); p.moveTo(4f,11f); p.lineTo(12f,4f); p.lineTo(20f,11f); p.lineTo(18f,11f); p.lineTo(18f,20f); p.lineTo(13f,20f); p.lineTo(13f,14f); p.lineTo(11f,14f); p.lineTo(11f,20f); p.lineTo(6f,20f); p.lineTo(6f,11f); p.close(); paint.style=Paint.Style.STROKE; c.drawPath(p,paint) }
            "folder" -> { paint.style=Paint.Style.FILL; c.drawRoundRect(3f,6f,21f,19f,2f,2f,paint); c.drawRect(5f,4f,12f,8f,paint) }
            "settings" -> { paint.style=Paint.Style.STROKE; paint.strokeWidth=2.6f; c.drawCircle(12f,12f,3.5f,paint); c.drawCircle(12f,12f,8f,paint) }
        }
        c.restore()
    }
}

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
    private lateinit var bottomHome: LinearLayout
    private lateinit var bottomSaved: LinearLayout
    private lateinit var bottomSettings: LinearLayout

    private var summaryItems: TextView? = null
    private var summaryQty: TextView? = null
    private var lastExportUri: Uri? = null

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).roundToInt()

    private fun roundedBg(fill: Int, radius: Float = 16f, stroke: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(radius.toInt()).toFloat()
            if (stroke != null) setStroke(dp(1), stroke)
        }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(247, 248, 250))
        }
        mainContent = FrameLayout(this)
        root.addView(mainContent, LinearLayout.LayoutParams(-1, 0, 1f))

        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(10), dp(7), dp(10), dp(7))
            setBackgroundColor(Color.WHITE)
            elevation = dp(8).toFloat()
        }
        bottomHome = navItem("home", "Home")
        bottomSaved = navItem("folder", "Saved Files")
        bottomSettings = navItem("settings", "Settings")
        nav.addView(bottomHome, LinearLayout.LayoutParams(0, dp(66), 1f))
        nav.addView(bottomSaved, LinearLayout.LayoutParams(0, dp(66), 1f))
        nav.addView(bottomSettings, LinearLayout.LayoutParams(0, dp(66), 1f))
        root.addView(nav)
        setContentView(root)
        showHome()
    }

    private fun navItem(icon: String, label: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(0, dp(2), 0, 0)
        val iconView = MaterialIconView(this@MainActivity, icon).apply {
            iconColor = Color.rgb(31, 43, 55)
        }
        addView(iconView, LinearLayout.LayoutParams(dp(24), dp(24)))
        addView(TextView(this@MainActivity).apply {
            text = label
            textSize = 12f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT
            setTextColor(Color.rgb(31, 43, 55))
        }, LinearLayout.LayoutParams(-1, dp(22)))
        setOnClickListener {
            when (label) {
                "Home" -> showHome()
                "Saved Files" -> showSavedFiles()
                "Settings" -> showSettings()
            }
        }
        tag = iconView
    }

    private fun refreshBottomNav() {
        val active = Color.rgb(225, 31, 38)
        val inactive = Color.rgb(31, 43, 55)
        listOf(bottomHome, bottomSaved, bottomSettings).forEachIndexed { i, v ->
            val color = if (selectedTab == i) active else inactive
            (v.tag as? MaterialIconView)?.iconColor = color
            (v.getChildAt(1) as? TextView)?.setTextColor(color)
            v.background = if (selectedTab == i) roundedBg(Color.rgb(255, 232, 234), 14f) else null
        }
    }

    private fun appHeader(parent: LinearLayout) {
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), dp(2), dp(2), dp(4))
        }

        // Left menu
        val menu = MaterialIconView(this, "menu").apply {
            iconColor = Color.rgb(20, 29, 38)
        }

        header.addView(
            menu,
            LinearLayout.LayoutParams(dp(42), dp(54))
        )

        // Center logo
        val logo = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }

        val brandLine = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        brandLine.addView(
            TextView(this).apply {
                text = "DIGITAL "
                textSize = 21f
                typeface = Typeface.create("sans", Typeface.BOLD)
                setTextColor(Color.rgb(16, 24, 31))
            },
            LinearLayout.LayoutParams(-2, -2)
        )

        brandLine.addView(
            TextView(this).apply {
                text = "VISION"
                textSize = 21f
                typeface = Typeface.create("sans", Typeface.BOLD)
                setTextColor(Color.rgb(225, 31, 38))
            },
            LinearLayout.LayoutParams(-2, -2)
        )

        logo.addView(brandLine)

        logo.addView(
            TextView(this).apply {
                text = "Mobile Repair Shop"
                textSize = 10f
                setTextColor(Color.rgb(80, 88, 96))
                gravity = Gravity.CENTER
            },
            LinearLayout.LayoutParams(-2, dp(18))
        )

        header.addView(
            logo,
            LinearLayout.LayoutParams(0, dp(54), 1f)
        )

        // Right spacer keeps the logo genuinely centered.
        header.addView(
            Space(this),
            LinearLayout.LayoutParams(dp(42), dp(54))
        )

        parent.addView(
            header,
            lp(-1, dp(60))
        )
    }
    private fun showHome() {
        selectedTab = 0
        refreshBottomNav()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(247, 248, 250))
        }
        root.setOnApplyWindowInsetsListener { view, insets ->
            val topInset = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                insets.getInsets(WindowInsets.Type.statusBars()).top
            } else {
                @Suppress("DEPRECATION")
                insets.systemWindowInsetTop
            }

            view.setPadding(
                view.paddingLeft,
                topInset,
                view.paddingRight,
                view.paddingBottom
            )

            insets
        }

        root.requestApplyInsets()
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(2), dp(14), dp(10))
        }

        scroll.addView(content)

        // ─────────────────────────────
        // HEADER
        // ─────────────────────────────
        appHeader(content)

        // Keep shop name internally for exports/backups.
        shop = EditText(this).apply {
            setText("Digital Vision")
        }

        // ─────────────────────────────
        // CURRENT LIST CARD
        // ─────────────────────────────
        val currentCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = roundedBg(
                Color.WHITE,
                16f,
                Color.rgb(229, 231, 234)
            )
            elevation = dp(1).toFloat()
        }

        val currentTop = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val currentTitle = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(MaterialIconView(this@MainActivity, "list").apply { iconColor = Color.rgb(22,30,38) }, LinearLayout.LayoutParams(dp(24),dp(24)))
            addView(TextView(this@MainActivity).apply {
                text = "Current List"; textSize = 17f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.rgb(22,30,38)); gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8),0,0,0)
            }, LinearLayout.LayoutParams(-1,-1))
        }

        currentTop.addView(
            currentTitle,
            LinearLayout.LayoutParams(0, dp(40), 1f)
        )

        val summary = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
        }

        summaryItems = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.rgb(90, 98, 106))
            gravity = Gravity.END
        }

        summaryQty = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.rgb(220, 31, 38))
            gravity = Gravity.END
            typeface = Typeface.DEFAULT_BOLD
        }

        summary.addView(summaryItems)
        summary.addView(summaryQty)

        currentTop.addView(
            summary,
            LinearLayout.LayoutParams(dp(82), dp(40))
        )

        currentCard.addView(currentTop)

        // ─────────────────────────────
        // TABLE
        // ─────────────────────────────
        val table = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedBg(
                Color.rgb(244, 246, 248),
                9f
            )
        }

        header.addView(tableCell("SL", 0.65f, true))
        header.addView(tableCell("Item Name", 2.85f, true))
        header.addView(tableCell("Qty", 0.95f, true))
        header.addView(tableCell("", 0.55f, true))

        table.addView(
            header,
            lp(-1, dp(36))
        )

        container = table
        currentCard.addView(table)

        content.addView(
            currentCard,
            lp(-1, ViewGroup.LayoutParams.WRAP_CONTENT)
        )

        // ─────────────────────────────
        // ORDER TO
        // ─────────────────────────────
        orderTo = EditText(this).apply {
            hint = "Order To"
            textSize = 13f
            setSingleLine(true)
            setPadding(dp(12), 0, dp(40), 0)
            background = roundedBg(
                Color.WHITE,
                11f,
                Color.rgb(225, 228, 232)
            )
        }

        val orderBox = FrameLayout(this)

        orderBox.addView(
            orderTo,
            FrameLayout.LayoutParams(
                -1,
                dp(46)
            )
        )

        orderBox.addView(
            MaterialIconView(this, "edit").apply {
                iconColor = Color.rgb(35,45,55)
            },
            FrameLayout.LayoutParams(
                dp(28),
                dp(28),
                Gravity.END or Gravity.CENTER_VERTICAL
            ).apply {
                rightMargin = dp(12)
            }
        )

        content.addView(
            orderBox,
            lp(-1, dp(52))
        )

        // ─────────────────────────────
        // DATE
        // ─────────────────────────────
        date = TextView(this).apply {
            text = SimpleDateFormat(
                "dd/MM/yyyy",
                Locale.getDefault()
            ).format(Date())

            textSize = 12f
            setTextColor(Color.rgb(80, 88, 96))
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), 0, 0, 0)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                val current = try {
                    SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).parse(text.toString())
                } catch (_: Exception) {
                    Date()
                }

                val calendar = Calendar.getInstance().apply {
                    time = current ?: Date()
                }

                DatePickerDialog(
                    this@MainActivity,
                    { _, year, month, dayOfMonth ->
                        val selected = Calendar.getInstance().apply {
                            set(year, month, dayOfMonth)
                        }
                        date.text = SimpleDateFormat(
                            "dd/MM/yyyy",
                            Locale.getDefault()
                        ).format(selected.time)
                        saveCurrent()
                    },
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH)
                ).show()
            }
        }

        content.addView(
            date,
            lp(-1, dp(24))
        )

        // ─────────────────────────────
        // ACTION GRID
        // ─────────────────────────────
        val actionGrid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        fun gridRow(a: View, b: View) {
            val r = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(4), 0, 0)
            }

            r.addView(
                a,
                LinearLayout.LayoutParams(
                    0,
                    dp(70),
                    1f
                ).apply {
                    setMargins(0, 0, dp(3), 0)
                }
            )

            r.addView(
                b,
                LinearLayout.LayoutParams(
                    0,
                    dp(70),
                    1f
                ).apply {
                    setMargins(dp(3), 0, 0, 0)
                }
            )

            actionGrid.addView(
                r,
                LinearLayout.LayoutParams(
                    -1,
                    dp(74)
                )
            )
        }
        val save = actionCard("save", "Save List", "Auto save to history").apply {
            setOnClickListener {
                addHistorySnapshot()
                Toast.makeText(
                    this@MainActivity,
                    "List saved to history.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        val export = actionCard("export", "Export as JPG", "Save to Gallery").apply {
            setOnClickListener { exportJpg() }
        }
        val print = actionCard("print", "Print", "Share / Print").apply {
            setOnClickListener { printCurrentList() }
        }
        val share = actionCard("share", "Share", "Send via WhatsApp").apply {
            setOnClickListener { shareCurrentList(true) }
        }
        gridRow(save, export); gridRow(print, share)
        content.addView(actionGrid, lp(-1, dp(178)))

        val newList = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = roundedBg(Color.rgb(225,31,38),12f)
            addView(MaterialIconView(this@MainActivity,"add").apply { iconColor = Color.WHITE }, LinearLayout.LayoutParams(dp(22),dp(22)))
            addView(TextView(this@MainActivity).apply {
                text = "New List"; textSize = 14f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE); gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(7),0,0,0)
            }, LinearLayout.LayoutParams(-2,-1))
            setOnClickListener { startNewList() }
        }

        content.addView(
            newList,
            lp(-1, dp(46))
        )

        // Small bottom breathing room only.
        content.addView(
            Space(this),
            lp(-1, dp(4))
        )

        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                -1,
                0,
                1f
            )
        )

        mainContent.removeAllViews()

        mainContent.addView(
            root,
            FrameLayout.LayoutParams(-1, -1)
        )

        // Preserve existing state/functionality.
        loadSaved()

        if (rows.isEmpty()) {
            rows.add(Row("", 1))
        }

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
            setBackgroundColor(Color.rgb(247, 248, 250))
            setPadding(dp(14), dp(8), dp(14), 0)
        }
        appHeader(root)
        val title = TextView(this).apply {
            text = "Saved Files"
            textSize = 21f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.rgb(20,28,36)); gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6),0,0,0)
        }
        root.addView(title, lp(-1, dp(42)))
        val search = EditText(this).apply {
            hint = "⌕  Search saved lists..."; textSize = 14f; setSingleLine(true); setPadding(dp(14),0,dp(14),0)
            background = roundedBg(Color.WHITE, 14f, Color.rgb(228,230,234))
        }
        root.addView(search, lp(-1, dp(48)))
        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0,dp(8),0,dp(5)) }
        val all = pill("All Lists (${history.size})", true); val fav = pill("Favorites (0)", false); val recent = pill("Recent", false)
        tabs.addView(all, LinearLayout.LayoutParams(0, dp(42), 1f).apply { setMargins(0,0,dp(4),0) })
        tabs.addView(fav, LinearLayout.LayoutParams(0, dp(42), 1f).apply { setMargins(dp(2),0,dp(2),0) })
        tabs.addView(recent, LinearLayout.LayoutParams(0, dp(42), 1f).apply { setMargins(dp(4),0,0,0) })
        root.addView(tabs)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0,dp(4),0,dp(86)) }
        val scroll = ScrollView(this).apply { addView(list) }
        root.addView(scroll, LinearLayout.LayoutParams(-1,0,1f))
        frame.addView(root, FrameLayout.LayoutParams(-1,-1))
        val sortButton = TextView(this).apply {
            text = "⇅"; textSize = 25f; gravity = Gravity.CENTER; setTextColor(Color.WHITE); background = roundedBg(Color.rgb(225,31,38), 30f); elevation = dp(6).toFloat()
            setOnClickListener { showSortDialog { rebuildSavedFiles(list, search.text.toString()) } }
        }
        frame.addView(sortButton, FrameLayout.LayoutParams(dp(58),dp(58),Gravity.END or Gravity.BOTTOM).apply { setMargins(0,0,dp(18),dp(18)) })
        search.addTextChangedListener(object: android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start:Int,count:Int,after:Int){}
            override fun onTextChanged(s: CharSequence?, start:Int,before:Int,count:Int){ rebuildSavedFiles(list,s?.toString().orEmpty()) }
            override fun afterTextChanged(s: android.text.Editable?){}
        })
        mainContent.removeAllViews(); mainContent.addView(frame, FrameLayout.LayoutParams(-1,-1))
        rebuildSavedFiles(list, "")
    }

    private fun pill(text: String, active: Boolean): TextView = TextView(this).apply {
        this.text=text; textSize=12f; gravity=Gravity.CENTER; typeface=Typeface.DEFAULT_BOLD
        setTextColor(if(active) Color.rgb(225,31,38) else Color.rgb(55,65,75))
        background=roundedBg(if(active) Color.rgb(255,232,234) else Color.WHITE, 12f, Color.rgb(225,228,232))
    }

    private fun rebuildSavedFiles(list: LinearLayout, query: String) {
        loadHistory(); list.removeAllViews()
        val indexed = history.mapIndexed { index, raw -> index to raw }
        val ordered = if(savedSortNewestFirst) indexed else indexed.reversed()
        var shown=0
        for((index,raw) in ordered){
            try{
                val o=JSONObject(raw); val name=o.optString("historyName",o.optString("date","Saved List")); val count=o.optJSONArray("rows")?.length()?:0
                val order=o.optString("orderTo",""); val label="$name $order $count"
                if(query.isNotBlank() && !label.contains(query,true)) continue
                val card=LinearLayout(this).apply{
                    orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL; setPadding(dp(10),dp(8),dp(8),dp(8)); background=roundedBg(Color.WHITE,14f,Color.rgb(230,232,235)); elevation=dp(1).toFloat()
                    setOnClickListener{showHistoryActions(index)}
                }
                val icon=TextView(this).apply{text="▤";textSize=28f;gravity=Gravity.CENTER;setTextColor(Color.rgb(23,34,45))}
                card.addView(icon,LinearLayout.LayoutParams(dp(48),dp(70)))
                val info=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER_VERTICAL}
                info.addView(TextView(this).apply{text="List ${history.size-index}";textSize=15f;typeface=Typeface.DEFAULT_BOLD;setTextColor(Color.rgb(22,30,38))})
                info.addView(TextView(this).apply{text="${name}  •  $count items${if(order.isNotBlank()) "  •  $order" else ""}";textSize=11f;setTextColor(Color.rgb(90,98,106))})
                card.addView(info,LinearLayout.LayoutParams(0,dp(70),1f))
                val star=TextView(this).apply{text="☆";textSize=25f;gravity=Gravity.CENTER;setTextColor(Color.rgb(225,31,38))}
                val open=TextView(this).apply{text="↗";textSize=24f;gravity=Gravity.CENTER;setTextColor(Color.rgb(20,30,40));setOnClickListener{restoreHistory(index)}}
                val share=TextView(this).apply{text="●";textSize=18f;gravity=Gravity.CENTER;setTextColor(Color.rgb(20,30,40));setOnClickListener{restoreHistory(index);showHome();exportJpg()}}
                val menu=TextView(this).apply{text="⋮";textSize=25f;gravity=Gravity.CENTER;setTextColor(Color.rgb(20,30,40));setOnClickListener{showHistoryActions(index)}}
                list.addView(card,LinearLayout.LayoutParams(-1,dp(82)).apply{setMargins(0,0,0,dp(8))})
                card.addView(star,LinearLayout.LayoutParams(dp(38),dp(70))); card.addView(open,LinearLayout.LayoutParams(dp(38),dp(70))); card.addView(share,LinearLayout.LayoutParams(dp(38),dp(70))); card.addView(menu,LinearLayout.LayoutParams(dp(32),dp(70)))
                shown++
            }catch(_:Exception){}
        }
        if(shown==0) list.addView(TextView(this).apply{text=if(history.isEmpty())"No saved files yet." else "No saved files match your search.";textSize=16f;gravity=Gravity.CENTER;setTextColor(Color.GRAY);setPadding(0,dp(50),0,dp(50))},lp(-1,dp(120)))
    }

    private fun showSortDialog(onSorted: () -> Unit) {
        val options=arrayOf("New to old","Old to new"); val checked=if(savedSortNewestFirst)0 else 1
        AlertDialog.Builder(this).setTitle("Sort saved files").setSingleChoiceItems(options,checked){dialog,which->savedSortNewestFirst=which==0;prefs.edit().putBoolean("saved_sort_newest_first",savedSortNewestFirst).apply();dialog.dismiss();onSorted()}.setNegativeButton("Cancel",null).show()
    }

    private fun showSettings() {
        selectedTab=2; refreshBottomNav()
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(Color.rgb(247,248,250));setPadding(dp(14),dp(8),dp(14),dp(14))}
        val scroll=ScrollView(this); val content=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}; scroll.addView(content); appHeader(content)
        content.addView(TextView(this).apply{text="⚙  Settings";textSize=21f;typeface=Typeface.DEFAULT_BOLD;setTextColor(Color.rgb(20,28,36));setPadding(dp(6),dp(4),0,dp(8))},lp(-1,dp(44)))
        val backup=settingCard("◆","Google Drive Backup & Restore","Backup your lists, restore, and manage\nGoogle Drive connection").apply{setOnClickListener{showBackupDialog()}}
        val update=settingCard("⟳","App Update","Check for new version and update\nyour app").apply{setOnClickListener{checkForUpdates(true)}}
        val profile=settingCard("●","Profile","App Developer\nBiswajit Das").apply{setOnClickListener{showProfile()}}
        content.addView(backup,lp(-1,dp(92)));content.addView(update,lp(-1,dp(92)));content.addView(profile,lp(-1,dp(92)))
        content.addView(TextView(this).apply{text="App Preferences";textSize=15f;typeface=Typeface.DEFAULT_BOLD;setTextColor(Color.rgb(30,38,46));setPadding(dp(6),dp(15),0,dp(8))},lp(-1,dp(42)))
        content.addView(prefRow("◉","Theme","Light"),lp(-1,dp(54)))
        content.addView(prefRow("▣","Auto Save","On"),lp(-1,dp(54)))
        content.addView(prefRow("▦","Date Format","DD/MM/YYYY"),lp(-1,dp(54)))
        content.addView(prefRow("▥","Clear App Data",">"),lp(-1,dp(54)))
        content.addView(TextView(this).apply{text="About";textSize=15f;typeface=Typeface.DEFAULT_BOLD;setTextColor(Color.rgb(30,38,46));setPadding(dp(6),dp(15),0,dp(8))},lp(-1,dp(42)))
        content.addView(settingCard("ⓘ","Digital Vision List","Version ${currentVersionName()}").apply{setOnClickListener{showProfile()}},lp(-1,dp(72)))
        root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f)); mainContent.removeAllViews();mainContent.addView(root,FrameLayout.LayoutParams(-1,-1))
        // Status is kept available through the backup dialog and internal state, but the reference UI remains clean.
        backupStatusView=TextView(this); updateBackupStatusView()
    }

    private fun showProfile() {
        AlertDialog.Builder(this)
            .setTitle("App Developer")
            .setMessage("Biswajit Das")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun settingCard(icon:String,title:String,subtitle:String): LinearLayout=LinearLayout(this).apply{
        orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(12),dp(8),dp(10),dp(8));background=roundedBg(Color.WHITE,14f,Color.rgb(230,232,235));elevation=dp(1).toFloat()
        addView(TextView(this@MainActivity).apply{text=icon;textSize=27f;gravity=Gravity.CENTER;setTextColor(Color.rgb(25,38,50))},LinearLayout.LayoutParams(dp(54),-1))
        val info=LinearLayout(this@MainActivity).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER_VERTICAL}
        info.addView(TextView(this@MainActivity).apply{text=title;textSize=15f;typeface=Typeface.DEFAULT_BOLD;setTextColor(Color.rgb(25,32,40))})
        info.addView(TextView(this@MainActivity).apply{text=subtitle;textSize=11f;setTextColor(Color.rgb(88,97,106))})
        addView(info,LinearLayout.LayoutParams(0,-1,1f));addView(TextView(this@MainActivity).apply{text="›";textSize=28f;gravity=Gravity.CENTER;setTextColor(Color.rgb(30,40,50))},LinearLayout.LayoutParams(dp(28),-1))
    }

    private fun prefRow(icon:String,title:String,value:String):LinearLayout=LinearLayout(this).apply{
        orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(10),0,dp(10),0);background=roundedBg(Color.WHITE,0f,Color.rgb(232,234,237))
        addView(TextView(this@MainActivity).apply{text=icon;textSize=21f;gravity=Gravity.CENTER},LinearLayout.LayoutParams(dp(42),-1))
        addView(TextView(this@MainActivity).apply{text=title;textSize=14f;setTextColor(Color.rgb(40,48,56))},LinearLayout.LayoutParams(0,-1,1f))
        addView(TextView(this@MainActivity).apply{text=value;textSize=12f;setTextColor(Color.rgb(95,102,110));gravity=Gravity.CENTER_VERTICAL},LinearLayout.LayoutParams(dp(105),-1))
        addView(TextView(this@MainActivity).apply{text="›";textSize=24f;gravity=Gravity.CENTER},LinearLayout.LayoutParams(dp(24),-1))
    }

    private fun actionCard(icon:String,title:String,subtitle:String):LinearLayout=LinearLayout(this).apply{
        orientation=LinearLayout.HORIZONTAL
        gravity=Gravity.CENTER_VERTICAL
        setPadding(dp(12),dp(7),dp(10),dp(7))
        background=roundedBg(Color.WHITE,14f,Color.rgb(230,232,235))
        elevation=dp(1).toFloat()

        val iconBox = FrameLayout(this@MainActivity).apply {
            background = roundedBg(Color.rgb(246,247,249), 28f)
            val iv = MaterialIconView(this@MainActivity, icon)
            iv.iconColor = Color.rgb(18,82,116)
            addView(iv, FrameLayout.LayoutParams(dp(28),dp(28),Gravity.CENTER))
        }
        addView(iconBox,LinearLayout.LayoutParams(dp(56),dp(56)))

        val info=LinearLayout(this@MainActivity).apply{
            orientation=LinearLayout.VERTICAL
            gravity=Gravity.CENTER_VERTICAL
            setPadding(dp(10),0,0,0)
        }
        info.addView(TextView(this@MainActivity).apply{text=title;textSize=14f;typeface=Typeface.DEFAULT_BOLD;setTextColor(Color.rgb(25,32,40))})
        info.addView(TextView(this@MainActivity).apply{text=subtitle;textSize=11f;setTextColor(Color.rgb(95,102,110))})
        addView(info,LinearLayout.LayoutParams(0,-1,1f))
    }

    private fun tableCell(text:String,weight:Float,bold:Boolean):TextView=TextView(this).apply{
        this.text=text;textSize=12f;gravity=Gravity.CENTER;setTextColor(Color.rgb(45,53,61));if(bold)typeface=Typeface.DEFAULT_BOLD
        layoutParams=LinearLayout.LayoutParams(0,-1).apply{this.weight=weight}
    }

    private fun addRow(focusNewItem: Boolean = false) {
        rows.add(Row("", 1))
        render(if (focusNewItem) rows.lastIndex else -1)
        saveCurrent()
    }

    private fun render(focusIndex: Int = -1) {
        if (!::container.isInitialized) return

        container.removeViews(1, maxOf(0, container.childCount - 1))

        rows.forEachIndexed { index, r ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(2), 0, dp(2), 0)
                setBackgroundColor(
                    if (index % 2 == 0) Color.WHITE
                    else Color.rgb(250, 251, 252)
                )
            }

            row.addView(
                TextView(this).apply {
                    text = "${index + 1}"
                    textSize = 13f
                    gravity = Gravity.CENTER
                },
                LinearLayout.LayoutParams(0, dp(52), 0.65f)
            )

            val item = EditText(this).apply {
                setText(r.item)
                hint = "Enter item"
                textSize = 13f
                setSingleLine(false)
                maxLines = 6
                minLines = 1
                setHorizontallyScrolling(false)
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                setBackgroundColor(Color.TRANSPARENT)
                setPadding(dp(6), dp(10), dp(4), dp(10))

                inputType =
                    android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                    android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE

                imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_NEXT

                setOnFocusChangeListener { _, focus ->
                    if (!focus) {
                        r.item = text.toString()
                        saveCurrent()
                    }
                }

                var creatingNextRow = false

                addTextChangedListener(
                    object : android.text.TextWatcher {
                        override fun beforeTextChanged(
                            s: CharSequence?,
                            st: Int,
                            c: Int,
                            a: Int
                        ) {}

                        override fun onTextChanged(
                            s: CharSequence?,
                            st: Int,
                            before: Int,
                            count: Int
                        ) {
                            if (creatingNextRow) return

                            val value = s?.toString().orEmpty()

                            // Some soft keyboards insert a newline instead of
                            // sending IME_ACTION_NEXT. Treat that newline as
                            // Enter and immediately create the next row.
                            if (value.contains("\n")) {
                                creatingNextRow = true
                                val cleaned = value.replace("\n", "")
                                r.item = cleaned
                                setText(cleaned)
                                setSelection(cleaned.length)
                                saveCurrent()
                                post {
                                    creatingNextRow = false
                                    addRow(focusNewItem = true)
                                }
                                return
                            }

                            r.item = value
                            row.requestLayout()
                        }

                        override fun afterTextChanged(
                            s: android.text.Editable?
                        ) {}
                    }
                )

                setOnEditorActionListener { _, actionId, event ->
                    val enterPressed =
                        event?.keyCode == KeyEvent.KEYCODE_ENTER &&
                        event.action == KeyEvent.ACTION_DOWN

                    if (
                        actionId == android.view.inputmethod.EditorInfo.IME_ACTION_NEXT ||
                        actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE ||
                        enterPressed
                    ) {
                        r.item = text.toString()
                        saveCurrent()
                        addRow(focusNewItem = true)
                        true
                    } else {
                        false
                    }
                }

                setOnKeyListener { _, keyCode, event ->
                    if (
                        keyCode == KeyEvent.KEYCODE_ENTER &&
                        event.action == KeyEvent.ACTION_DOWN
                    ) {
                        r.item = text.toString()
                        saveCurrent()
                        addRow(focusNewItem = true)
                        true
                    } else {
                        false
                    }
                }

                setOnEditorActionListener { _, actionId, event ->
                    val enterPressed =
                        event?.keyCode == KeyEvent.KEYCODE_ENTER &&
                        event.action == KeyEvent.ACTION_DOWN

                    if (
                        actionId == android.view.inputmethod.EditorInfo.IME_ACTION_NEXT ||
                        actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE ||
                        enterPressed
                    ) {
                        r.item = text.toString()
                        saveCurrent()
                        addRow(focusNewItem = true)
                        true
                    } else {
                        false
                    }
                }

                setOnKeyListener { _, keyCode, event ->
                    if (
                        keyCode == KeyEvent.KEYCODE_ENTER &&
                        event.action == KeyEvent.ACTION_DOWN
                    ) {
                        r.item = text.toString()
                        saveCurrent()
                        addRow(focusNewItem = true)
                        true
                    } else {
                        false
                    }
                }
            }

            row.addView(
                item,
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    2.85f
                )
            )

            item.minHeight = dp(52)

            val qty = EditText(this).apply {
                setText(r.qty.toString())
                textSize = 13f
                gravity = Gravity.CENTER
                setSingleLine(true)
                setBackgroundColor(Color.TRANSPARENT)
                setPadding(0, dp(6), 0, dp(6))
                inputType = android.text.InputType.TYPE_CLASS_NUMBER

                setOnFocusChangeListener { _, focus ->
                    if (!focus) {
                        r.qty = text.toString().toIntOrNull() ?: 0
                        updateTotal()
                        saveCurrent()
                    }
                }

                addTextChangedListener(
                    object : android.text.TextWatcher {
                        override fun beforeTextChanged(
                            s: CharSequence?,
                            st: Int,
                            c: Int,
                            a: Int
                        ) {}

                        override fun onTextChanged(
                            s: CharSequence?,
                            st: Int,
                            b: Int,
                            c: Int
                        ) {
                            r.qty = s?.toString()?.toIntOrNull() ?: 0
                            updateTotal()
                        }

                        override fun afterTextChanged(
                            s: android.text.Editable?
                        ) {}
                    }
                )
            }

            row.addView(
                qty,
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    0.95f
                )
            )

            qty.minHeight = dp(52)

            val deleteCell = FrameLayout(this).apply {
                val del = MaterialIconView(this@MainActivity, "delete")
                del.iconColor = Color.rgb(210, 31, 38)

                addView(
                    del,
                    FrameLayout.LayoutParams(
                        dp(24),
                        dp(24),
                        Gravity.CENTER
                    )
                )

                setOnClickListener {
                    if (rows.size > 1) {
                        rows.removeAt(index)
                    } else {
                        rows[0] = Row("", 1)
                    }
                    render()
                    saveCurrent()
                }
            }

            row.addView(
                deleteCell,
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    0.55f
                )
            )

            deleteCell.minimumHeight = dp(52)

            row.minimumHeight = dp(52)

            container.addView(
                row,
                LinearLayout.LayoutParams(
                    -1,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )

            if (index == focusIndex) {
                item.post {
                    item.requestFocus()
                    item.setSelection(item.text.length)
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                    imm.showSoftInput(item, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                }
            }
        }

        updateTotal()
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
            if (::date.isInitialized) date.text = o.optString("date", SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date()))
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
        date.text = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
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
            setSingleLine(true)
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
            date.text=o.optString("date", SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date()))
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
            addView(message, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
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

    private fun updateTotal(){
        if(::total.isInitialized) total.text="Total Quantity: ${rows.sumOf{it.qty}}"
        summaryItems?.text = "Items: ${rows.size}"
        summaryQty?.text = "Total Qty: ${rows.sumOf{it.qty}}"
    }

    private fun lp(w:Int,h:Int,weight:Float=0f)=LinearLayout.LayoutParams(w,h).apply { this.weight=weight }

    private fun shareCurrentList(preferWhatsApp: Boolean = false) {
        // Always make sure the current editor contents are reflected in the JPG.
        exportJpg()

        val uri = lastExportUri
        if (uri == null) {
            Toast.makeText(
                this,
                "Could not create the JPG to share.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        if (preferWhatsApp) {
            val whatsapp = Intent(send).apply {
                setPackage("com.whatsapp")
            }

            val whatsappBusiness = Intent(send).apply {
                setPackage("com.whatsapp.w4b")
            }

            val initialTargets = ArrayList<Intent>(2)
            val pm = packageManager

            if (pm.resolveActivity(whatsapp, 0) != null) {
                initialTargets.add(whatsapp)
            }

            if (pm.resolveActivity(whatsappBusiness, 0) != null) {
                initialTargets.add(whatsappBusiness)
            }

            try {
                val chooser = Intent.createChooser(
                    send,
                    "Share via WhatsApp"
                )

                if (
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP &&
                    initialTargets.isNotEmpty()
                ) {
                    chooser.putExtra(
                        Intent.EXTRA_INITIAL_INTENTS,
                        initialTargets.toTypedArray()
                    )
                }

                startActivity(chooser)

            } catch (_: Exception) {
                try {
                    startActivity(
                        Intent.createChooser(send, "Share JPG")
                    )
                } catch (_: Exception) {
                    Toast.makeText(
                        this,
                        "No app is available to share this JPG.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

        } else {
            try {
                startActivity(
                    Intent.createChooser(send, "Share JPG")
                )
            } catch (_: Exception) {
                Toast.makeText(
                    this,
                    "No app is available to share this JPG.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun printCurrentList() {
        // Generate the same polished JPG used by Export/Share, then send it
        // through Android's native print framework. This keeps the printed
        // content independent from the Home-screen layout.
        exportJpg()

        val uri = lastExportUri
        if (uri == null) {
            Toast.makeText(
                this,
                "Could not prepare the list for printing.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val printManager = getSystemService(Context.PRINT_SERVICE) as? android.print.PrintManager
        if (printManager == null) {
            Toast.makeText(
                this,
                "Printing is not available on this device.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val jobName = "Digital Vision - ${date.text}"
        printManager.print(
            jobName,
            JpgPrintAdapter(this, uri),
            android.print.PrintAttributes.Builder()
                .setMediaSize(android.print.PrintAttributes.MediaSize.ISO_A4)
                .setColorMode(android.print.PrintAttributes.COLOR_MODE_COLOR)
                .setMinMargins(android.print.PrintAttributes.Margins.NO_MARGINS)
                .build()
        )
    }

    private class JpgPrintAdapter(
        private val context: Context,
        private val imageUri: Uri
    ) : android.print.PrintDocumentAdapter() {

        private var bitmap: Bitmap? = null

        override fun onLayout(
            oldAttributes: android.print.PrintAttributes?,
            newAttributes: android.print.PrintAttributes,
            cancellationSignal: android.os.CancellationSignal,
            callback: android.print.PrintDocumentAdapter.LayoutResultCallback,
            extras: Bundle?
        ) {
            if (cancellationSignal.isCanceled) {
                callback.onLayoutCancelled()
                return
            }

            try {
                if (bitmap == null) {
                    bitmap = context.contentResolver.openInputStream(imageUri)?.use {
                        BitmapFactory.decodeStream(it)
                    }
                }

                val info = android.print.PrintDocumentInfo.Builder("DigitalVisionList.jpg")
                    .setContentType(android.print.PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(1)
                    .build()

                callback.onLayoutFinished(info, false)
            } catch (e: Exception) {
                callback.onLayoutFailed(e.message ?: "Could not prepare the print job.")
            }
        }

        override fun onWrite(
            pages: Array<android.print.PageRange>,
            destination: android.os.ParcelFileDescriptor,
            cancellationSignal: android.os.CancellationSignal,
            callback: android.print.PrintDocumentAdapter.WriteResultCallback
        ) {
            val source = bitmap
            if (source == null) {
                callback.onWriteFailed("Could not load the list image.")
                return
            }

            if (cancellationSignal.isCanceled) {
                callback.onWriteCancelled()
                return
            }

            var pdf: android.graphics.pdf.PdfDocument? = null
            try {
                val pageWidth = 595
                val pageHeight = 842
                val document = android.graphics.pdf.PdfDocument()
                pdf = document

                val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(
                    pageWidth,
                    pageHeight,
                    1
                ).create()
                val page = document.startPage(pageInfo)
                val canvas = page.canvas

                val availableWidth = pageWidth.toFloat()
                val availableHeight = pageHeight.toFloat()
                val scale = minOf(
                    availableWidth / source.width.toFloat(),
                    availableHeight / source.height.toFloat()
                )
                val drawWidth = source.width * scale
                val drawHeight = source.height * scale
                val left = (availableWidth - drawWidth) / 2f
                val top = (availableHeight - drawHeight) / 2f

                val dest = RectF(left, top, left + drawWidth, top + drawHeight)
                canvas.drawColor(Color.WHITE)
                canvas.drawBitmap(source, null, dest, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                document.finishPage(page)

                FileOutputStream(destination.fileDescriptor).use { out ->
                    document.writeTo(out)
                }

                callback.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES))
            } catch (e: Exception) {
                callback.onWriteFailed(e.message ?: "Could not print the list.")
            } finally {
                pdf?.close()
            }
        }
    }

    private fun exportJpg(){
        // Sync the current editor values before exporting.
        for(i in 1 until container.childCount){
            val rowView = container.getChildAt(i) as? LinearLayout ?: continue
            val rowIndex = i - 1
            if(rowIndex < rows.size){
                val itemView = rowView.getChildAt(1) as? EditText
                val qtyView = rowView.getChildAt(2) as? EditText
                rows[rowIndex].item = itemView?.text?.toString().orEmpty()
                rows[rowIndex].qty = qtyView?.text?.toString()?.toIntOrNull() ?: 0
            }
        }
        saveCurrent()

        val width = 1080
        val left = 64f
        val right = 1016f
        val tableWidth = right - left

        // Refined column layout: slightly narrower S.No. and more room for details.
        val serialRight = 158f
        val itemLeft = 185f
        val qtyLeft = 895f
        val qtyRight = 985f
        val itemWidth = qtyLeft - itemLeft - 22f

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(210, 31, 38)
            textSize = 44f
            typeface = Typeface.DEFAULT_BOLD
        }
        val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(78, 84, 91)
            textSize = 25f
            typeface = Typeface.DEFAULT
        }
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 27f
            typeface = Typeface.DEFAULT_BOLD
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(38, 42, 46)
            textSize = 26f
            typeface = Typeface.DEFAULT
        }
        val qtyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(38, 42, 46)
            textSize = 26f
            typeface = Typeface.DEFAULT
            textAlign = Paint.Align.CENTER
        }
        val totalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(35, 39, 43)
            textSize = 31f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.RIGHT
        }

        fun wrapText(value: String, paint: Paint, maxWidth: Float): List<String> {
            if(value.isBlank()) return listOf("")
            val result = mutableListOf<String>()

            value.split("\n").forEach { paragraph ->
                if(paragraph.isEmpty()){
                    result.add("")
                    return@forEach
                }

                var remaining = paragraph.trim()
                while(remaining.isNotEmpty()){
                    var count = paint.breakText(
                        remaining,
                        true,
                        maxWidth,
                        null
                    ).coerceAtLeast(1)

                    if(count < remaining.length){
                        val breakAt = remaining.substring(0, count).lastIndexOf(' ')
                        if(breakAt > 0) count = breakAt
                    }

                    result.add(remaining.substring(0, count).trimEnd())
                    remaining = remaining.substring(count).trimStart()
                }
            }

            return if(result.isEmpty()) listOf("") else result
        }

        // Slightly more generous vertical padding keeps wrapped details visually centered.
        val itemLineHeight = 36f
        val rowVerticalPadding = 30f
        val minRowHeight = 68f
        val headerHeight = 70f

        // Compact, balanced header area.
        val titleBaseline = 84f
        val dateBaseline = 124f
        val orderBaseline = 158f
        val tableTop = if(orderTo.text.toString().trim().isBlank()) 180f else 190f

        val wrappedRows = rows.map { row ->
            wrapText(row.item, bodyPaint, itemWidth)
        }

        val rowHeights = wrappedRows.map { lines ->
            maxOf(
                minRowHeight,
                rowVerticalPadding + lines.size * itemLineHeight
            )
        }

        val footerHeight = 104f
        val totalHeight =
            tableTop +
            headerHeight +
            rowHeights.sum() +
            footerHeight

        val bmp = Bitmap.createBitmap(
            width,
            totalHeight.toInt().coerceAtLeast(600),
            Bitmap.Config.ARGB_8888
        )

        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)

        // Digital Vision header.
        val shopName = shop.text.toString().trim().ifBlank { "Digital Vision" }
        c.drawText(shopName, left, titleBaseline, titlePaint)

        c.drawText(
            "Date: ${date.text}",
            left,
            dateBaseline,
            metaPaint
        )

        val orderValue = orderTo.text.toString().trim()
        if(orderValue.isNotBlank()){
            c.drawText(
                "Order To: $orderValue",
                left,
                orderBaseline,
                metaPaint
            )
        }

        // Dark table header.
        val headerFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(45, 50, 55)
            style = Paint.Style.FILL
        }

        val headerBottom = tableTop + headerHeight
        c.drawRoundRect(
            left,
            tableTop,
            right,
            headerBottom,
            10f,
            10f,
            headerFill
        )

        val headerBaseline = tableTop + 44f

        headerPaint.textAlign = Paint.Align.CENTER
        c.drawText(
            "S.No.",
            (left + serialRight) / 2f,
            headerBaseline,
            headerPaint
        )

        headerPaint.textAlign = Paint.Align.LEFT
        c.drawText(
            "Item Name / Details",
            itemLeft,
            headerBaseline,
            headerPaint
        )

        headerPaint.textAlign = Paint.Align.CENTER
        c.drawText(
            "Qty",
            (qtyLeft + qtyRight) / 2f,
            headerBaseline,
            headerPaint
        )

        // Table rows.
        var currentTop = headerBottom

        rows.forEachIndexed { index, row ->
            val rowHeight = rowHeights[index]
            val lines = wrappedRows[index]

            val rowFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if(index % 2 == 0) {
                    Color.WHITE
                } else {
                    Color.rgb(249, 250, 251)
                }
                style = Paint.Style.FILL
            }

            c.drawRect(
                left,
                currentTop,
                right,
                currentTop + rowHeight,
                rowFill
            )

            // Very subtle divider.
            val divider = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(229, 231, 233)
                strokeWidth = 1f
            }
            c.drawLine(
                left,
                currentTop + rowHeight,
                right,
                currentTop + rowHeight,
                divider
            )

            // Serial number centered in its column.
            bodyPaint.textAlign = Paint.Align.CENTER
            val rowCenter = currentTop + rowHeight / 2f
            val serialMetrics = bodyPaint.fontMetrics
            val serialBaseline =
                rowCenter - (serialMetrics.ascent + serialMetrics.descent) / 2f

            c.drawText(
                "${index + 1}",
                (left + serialRight) / 2f,
                serialBaseline,
                bodyPaint
            )

            // Item details with dynamic wrapping and comfortable padding.
            bodyPaint.textAlign = Paint.Align.LEFT
            val blockHeight = lines.size * itemLineHeight
            var baseline =
                currentTop +
                (rowHeight - blockHeight) / 2f -
                bodyPaint.fontMetrics.ascent

            lines.forEach { line ->
                c.drawText(
                    line,
                    itemLeft,
                    baseline,
                    bodyPaint
                )
                baseline += itemLineHeight
            }

            // Quantity centered in its own column.
            qtyPaint.textAlign = Paint.Align.CENTER
            val qtyMetrics = qtyPaint.fontMetrics
            val qtyBaseline =
                rowCenter - (qtyMetrics.ascent + qtyMetrics.descent) / 2f

            c.drawText(
                row.qty.toString(),
                (qtyLeft + qtyRight) / 2f,
                qtyBaseline,
                qtyPaint
            )

            currentTop += rowHeight
        }

        // Subtle separator before the final total.
        val totalDivider = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(218, 221, 224)
            strokeWidth = 2f
        }
        c.drawLine(
            left,
            currentTop + 1f,
            right,
            currentTop + 1f,
            totalDivider
        )

        // Total quantity with balanced right margin.
        val totalLabel = "Total Quantity: ${rows.sumOf { it.qty }}"
        c.drawText(
            totalLabel,
            right,
            currentTop + 57f,
            totalPaint
        )

        val safeDate = date.text.toString()
            .ifBlank {
                SimpleDateFormat(
                    "dd/MM/yy",
                    Locale.getDefault()
                ).format(Date())
            }
            .replace("/", "-")

        val name = "DigitalVision_${safeDate}.jpg"

        val values = ContentValues().apply {
            put(
                MediaStore.Images.Media.DISPLAY_NAME,
                name
            )
            put(
                MediaStore.Images.Media.MIME_TYPE,
                "image/jpeg"
            )
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "Pictures/Digital Vision"
            )
        }

        val uri = contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            values
        )

        uri?.let {
            contentResolver.openOutputStream(it)?.use { out ->
                bmp.compress(
                    Bitmap.CompressFormat.JPEG,
                    95,
                    out
                )
            }

            lastExportUri = it

            Toast.makeText(
                this,
                "JPG saved to Pictures/Digital Vision",
                Toast.LENGTH_LONG
            ).show()
        }
    }

}