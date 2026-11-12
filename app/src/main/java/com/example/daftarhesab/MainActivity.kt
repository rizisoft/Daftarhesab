package com.example.daftarhesab

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private data class Customer(val id: Long, var name: String)
    private data class Entry(
        val id: Long,
        val customerId: Long,
        var type: String,
        var description: String,
        var amount: Long,
        val date: String
    )

    private val customers = mutableListOf<Customer>()
    private val entries = mutableListOf<Entry>()
    private var activeCustId: Long? = null
    private var editCustId: Long? = null
    private var editEntryId: Long? = null

    private lateinit var dbFile: File

    private val restoreLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                restoreFromUri(uri)
            }
        }
    }

    private val manualBackupLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                try {
                    contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(buildJsonString().toByteArray(Charsets.UTF_8))
                    }
                    Toast.makeText(this, "賮丕蹖賱 倬卮鬲蹖亘丕賳 匕禺蹖乇賴 卮丿", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "禺胤丕 丿乇 匕禺蹖乇賴 倬卮鬲蹖亘丕賳", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dbFile = File(filesDir, "daftar_hesab_db.json")
        loadData()
        renderHome()
    }

    override fun onStop() {
        super.onStop()
        autoBackupToDownloads()
    }

    private fun buildJsonString(): String {
        val root = JSONObject()
        val custArray = JSONArray()
        customers.forEach { c ->
            custArray.put(JSONObject().apply {
                put("id", c.id)
                put("name", c.name)
            })
        }
        root.put("customers", custArray)

        val entryArray = JSONArray()
        entries.forEach { e ->
            entryArray.put(JSONObject().apply {
                put("id", e.id)
                put("customerId", e.customerId)
                put("type", e.type)
                put("description", e.description)
                put("amount", e.amount)
                put("date", e.date)
            })
        }
        root.put("entries", entryArray)
        return root.toString(2)
    }

    private fun saveData() {
        dbFile.writeText(buildJsonString())
    }

    private fun autoBackupToDownloads() {
        try {
            val backupDir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: filesDir
            if (!backupDir.exists()) backupDir.mkdirs()
            val backupFile = File(backupDir, "DaftarHesab_AutoBackup.json")
            backupFile.writeText(buildJsonString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadData() {
        if (!dbFile.exists()) return
        parseJsonAndLoad(dbFile.readText())
    }

    private fun parseJsonAndLoad(jsonText: String): Boolean {
        return try {
            val root = JSONObject(jsonText)
            val custArray = root.getJSONArray("customers")
            val newCustomers = mutableListOf<Customer>()
            for (i in 0 until custArray.length()) {
                val obj = custArray.getJSONObject(i)
                newCustomers.add(Customer(obj.getLong("id"), obj.getString("name")))
            }

            val entryArray = root.getJSONArray("entries")
            val newEntries = mutableListOf<Entry>()
            for (i in 0 until entryArray.length()) {
                val obj = entryArray.getJSONObject(i)
                newEntries.add(
                    Entry(
                        obj.getLong("id"),
                        obj.getLong("customerId"),
                        obj.getString("type"),
                        obj.getString("description"),
                        obj.getLong("amount"),
                        obj.getString("date")
                    )
                )
            }
            customers.clear()
            customers.addAll(newCustomers)
            entries.clear()
            entries.addAll(newEntries)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun restoreFromUri(uri: Uri) {
        try {
            val text = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: return
            if (parseJsonAndLoad(text)) {
                saveData()
                renderHome()
                Toast.makeText(this, "丕胤賱丕毓丕鬲 亘丕 賲賵賮賯蹖鬲 亘丕夭蹖丕亘蹖 卮丿!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "賮丕蹖賱 倬卮鬲蹖亘丕賳 賲毓鬲亘乇 賳蹖爻鬲", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "禺胤丕 丿乇 禺賵丕賳丿賳 賮丕蹖賱", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getJalaliDate(): String {
        val cal = Calendar.getInstance()
        val gy = cal.get(Calendar.YEAR)
        val gm = cal.get(Calendar.MONTH) + 1
        val gd = cal.get(Calendar.DAY_OF_MONTH)
        val gdm = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        val gy2 = if (gm > 2) gy + 1 else gy
        var days = 355666 + (365 * gy) + ((gy2 + 3) / 4) - ((gy2 + 99) / 100) + ((gy2 + 399) / 400) + gd + gdm[gm - 1]
        var jy = -1595 + (33 * (days / 12053))
        days %= 12053
        jy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            jy += (days - 1) / 365
            days = (days - 1) % 365
        }
        val jm = if (days < 186) 1 + (days / 31) else 7 + ((days - 186) / 30)
        val jd = 1 + (if (days < 186) (days % 31) else ((days - 186) % 30))
        return String.format(Locale.US, "%04d/%02d/%02d", jy, jm, jd)
    }

    private fun makeRoundedCard(bgColor: Int, borderColor: Int, strokePx: Int, radiusPx: Float = 24f): GradientDrawable {
        return GradientDrawable().apply {
            setColor(bgColor)
            setStroke(strokePx, borderColor)
            cornerRadius = radiusPx
        }
    }

    private fun renderHome() {
        activeCustId = null
        editCustId = null
        editEntryId = null
        setContentView(createHomeView())
    }

    private fun createHomeView(): LinearLayout {
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            setBackgroundColor(Color.parseColor("#F5F7F6"))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        val tvTitle = TextView(this).apply {
            text = "馃摂 丿賮鬲乇 丨爻丕亘 賲賳"
            textSize = 21f
            setTextColor(Color.parseColor("#176B5B"))
            gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 8, 0, 24)
        }
        rootLayout.addView(tvTitle)

        val edtCustName = EditText(this).apply {
            hint = "賳丕賲 賲卮鬲乇蹖 噩丿蹖丿"
            setPadding(28, 24, 28, 24)
            background = makeRoundedCard(Color.WHITE, Color.parseColor("#CCCCCC"), 2, 16f)
        }
        rootLayout.addView(edtCustName)

        val btnAddCust = Button(this).apply {
            text = "+ 丕賮夭賵丿賳 賲卮鬲乇蹖 噩丿蹖丿"
            background = makeRoundedCard(Color.parseColor("#176B5B"), Color.parseColor("#176B5B"), 0, 16f)
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 16
            }
        }
        rootLayout.addView(btnAddCust)

        val btnCancelEdit = Button(this).apply {
            text = "賱睾賵 賵蹖乇丕蹖卮"
            background = makeRoundedCard(Color.parseColor("#718096"), Color.parseColor("#718096"), 0, 16f)
            setTextColor(Color.WHITE)
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 12
            }
        }
        rootLayout.addView(btnCancelEdit)

        // Backup & Restore row
        val backupRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 20
                bottomMargin = 12
            }
        }

        val btnBackup = Button(this).apply {
            text = "匕禺蹖乇賴 倬卮鬲蹖亘丕賳"
            textSize = 12f
            background = makeRoundedCard(Color.parseColor("#2C5282"), Color.parseColor("#2C5282"), 0, 16f)
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = 8
            }
            setOnClickListener {
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/json"
                    putExtra(Intent.EXTRA_TITLE, "daftar_hesab_backup.json")
                }
                manualBackupLauncher.launch(intent)
            }
        }

        val btnRestore = Button(this).apply {
            text = "亘丕夭蹖丕亘蹖 倬卮鬲蹖亘丕賳"
            textSize = 12f
            background = makeRoundedCard(Color.parseColor("#718096"), Color.parseColor("#718096"), 0, 16f)
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = 8
            }
            setOnClickListener {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                }
                restoreLauncher.launch(intent)
            }
        }
        backupRow.addView(btnBackup)
        backupRow.addView(btnRestore)
        rootLayout.addView(backupRow)

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
        }
        val listLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8, 0, 32)
        }
        scroll.addView(listLayout)
        rootLayout.addView(scroll)

        fun refreshList() {
            listLayout.removeAllViews()
            if (customers.isEmpty()) {
                val tv = TextView(this).apply {
                    text = "賴賳賵夭 賲卮鬲乇蹖鈥屫й� 孬亘鬲 賳卮丿賴 丕爻鬲."
                    gravity = Gravity.CENTER
                    setPadding(0, 60, 0, 0)
                    setTextColor(Color.parseColor("#66736F"))
                }
                listLayout.addView(tv)
                return
            }

            customers.forEach { c ->
                val custEntries = entries.filter { it.customerId == c.id }
                val balance = custEntries.fold(0L) { acc, e -> if (e.type == "sale") acc + e.amount else acc - e.amount }

                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(28, 24, 28, 24)
                    background = makeRoundedCard(Color.WHITE, Color.parseColor("#E2E8F0"), 2, 20f)
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                        topMargin = 14
                    }
                }

                val infoLayout = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    setOnClickListener { openLedger(c.id) }
                }

                val tvName = TextView(this).apply {
                    text = c.name
                    textSize = 17f
                    setTextColor(Color.parseColor("#18312C"))
                    setTypeface(null, Typeface.BOLD)
                }

                val tvBalance = TextView(this).apply {
                    text = when {
                        balance > 0 -> "亘丿賴讴丕乇: %,d 鬲賵賲丕賳".format(balance)
                        balance < 0 -> "胤賱亘讴丕乇: %,d 鬲賵賲丕賳".format(abs(balance))
                        else -> "鬲爻賵蹖賴"
                    }
                    textSize = 14f
                    setTextColor(if (balance > 0) Color.parseColor("#B23A48") else Color.parseColor("#176B5B"))
                    setPadding(0, 6, 0, 0)
                }

                infoLayout.addView(tvName)
                infoLayout.addView(tvBalance)
                card.addView(infoLayout)

                val btnEdit = Button(this).apply {
                    text = "賵蹖乇丕蹖卮"
                    textSize = 11f
                    background = makeRoundedCard(Color.parseColor("#D69E2E"), Color.parseColor("#D69E2E"), 0, 12f)
                    setTextColor(Color.WHITE)
                    minHeight = 0
                    minimumHeight = 0
                    setPadding(20, 12, 20, 12)
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                        marginEnd = 8
                    }
                    setOnClickListener {
                        editCustId = c.id
                        edtCustName.setText(c.name)
                        btnAddCust.text = "匕禺蹖乇賴 鬲睾蹖蹖乇丕鬲 賲卮鬲乇蹖"
                        btnCancelEdit.visibility = View.VISIBLE
                    }
                }
                card.addView(btnEdit)

                val btnDel = Button(this).apply {
                    text = "丨匕賮"
                    textSize = 11f
                    background = makeRoundedCard(Color.parseColor("#B23A48"), Color.parseColor("#B23A48"), 0, 12f)
                    setTextColor(Color.WHITE)
                    minHeight = 0
                    minimumHeight = 0
                    setPadding(20, 12, 20, 12)
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    setOnClickListener {
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("丨匕賮 賲卮鬲乇蹖")
                            .setMessage("鬲賲丕賲 鬲乇丕讴賳卮鈥屬囏й� 丕蹖賳 賲卮鬲乇蹖 賳蹖夭 丨匕賮 禺賵丕賴賳丿 卮丿. 丕丿丕賲賴 賲蹖鈥屫囒屫�")
                            .setPositiveButton("亘賱賴") { _, _ ->
                                customers.remove(c)
                                entries.removeAll { it.customerId == c.id }
                                saveData()
                                refreshList()
                            }
                            .setNegativeButton("禺蹖乇", null)
                            .show()
                    }
                }
                card.addView(btnDel)
                listLayout.addView(card)
            }
        }

        btnAddCust.setOnClickListener {
            val name = edtCustName.text.toString().trim()
            if (name.isEmpty()) {
                Toast.makeText(this, "賳丕賲 賲卮鬲乇蹖 乇丕 賵丕乇丿 讴賳蹖丿", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (editCustId != null) {
                customers.find { it.id == editCustId }?.name = name
                editCustId = null
                btnAddCust.text = "+ 丕賮夭賵丿賳 賲卮鬲乇蹖 噩丿蹖丿"
                btnCancelEdit.visibility = View.GONE
            } else {
                customers.add(Customer(System.currentTimeMillis(), name))
            }

            edtCustName.text.clear()
            saveData()
            refreshList()
        }

        btnCancelEdit.setOnClickListener {
            editCustId = null
            edtCustName.text.clear()
            btnAddCust.text = "+ 丕賮夭賵丿賳 賲卮鬲乇蹖 噩丿蹖丿"
            btnCancelEdit.visibility = View.GONE
        }

        refreshList()
        return rootLayout
    }

    private fun openLedger(custId: Long) {
        activeCustId = custId
        editEntryId = null
        val c = customers.find { it.id == custId } ?: return

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            setBackgroundColor(Color.parseColor("#F5F7F6"))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        val btnBack = Button(this).apply {
            text = "鈫� 亘丕夭诏卮鬲"
            textSize = 13f
            background = makeRoundedCard(Color.parseColor("#66736F"), Color.parseColor("#66736F"), 0, 14f)
            setTextColor(Color.WHITE)
            minHeight = 0
            minimumHeight = 0
            setPadding(24, 14, 24, 14)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        rootLayout.addView(btnBack)
        btnBack.setOnClickListener { renderHome() }

        val tvTitle = TextView(this).apply {
            text = c.name
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#18312C"))
            setPadding(0, 16, 0, 4)
        }
        rootLayout.addView(tvTitle)

        val tvBalance = TextView(this).apply {
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 16)
        }
        rootLayout.addView(tvBalance)

        val tvFormHeader = TextView(this).apply {
            text = "孬亘鬲 鬲乇丕讴賳卮 噩丿蹖丿 (亘乇丕蹖 賵蹖乇丕蹖卮 乇賵蹖 鬲乇丕讴賳卮 亘夭賳蹖丿)"
            textSize = 13f
            setTextColor(Color.parseColor("#66736F"))
            setPadding(0, 0, 0, 8)
        }
        rootLayout.addView(tvFormHeader)

        val edtDesc = EditText(this).apply {
            hint = "賳丕賲 讴丕賱丕 蹖丕 卮乇丨"
            setPadding(24, 20, 24, 20)
            background = makeRoundedCard(Color.WHITE, Color.parseColor("#CCCCCC"), 2, 16f)
        }
        rootLayout.addView(edtDesc)

        val edtAmount = EditText(this).apply {
            hint = "賲亘賱睾 (鬲賵賲丕賳)"
            inputType = InputType.TYPE_CLASS_NUMBER
            setPadding(24, 20, 24, 20)
            background = makeRoundedCard(Color.WHITE, Color.parseColor("#CCCCCC"), 2, 16f)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 12
            }
        }
        rootLayout.addView(edtAmount)

        val btnLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 14
            }
        }
        val btnSale = Button(this).apply {
            text = "孬亘鬲 讴丕賱丕 (亘丿賴讴丕乇)"
            background = makeRoundedCard(Color.parseColor("#B23A48"), Color.parseColor("#B23A48"), 0, 16f)
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = 8
            }
        }
        val btnPayment = Button(this).apply {
            text = "丿乇蹖丕賮鬲 賵噩賴 (鬲爻賵蹖賴)"
            background = makeRoundedCard(Color.parseColor("#176B5B"), Color.parseColor("#176B5B"), 0, 16f)
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = 8
            }
        }
        btnLayout.addView(btnSale)
        btnLayout.addView(btnPayment)
        rootLayout.addView(btnLayout)

        val btnCancelEntryEdit = Button(this).apply {
            text = "賱睾賵 賵蹖乇丕蹖卮"
            background = makeRoundedCard(Color.parseColor("#718096"), Color.parseColor("#718096"), 0, 16f)
            setTextColor(Color.WHITE)
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 10
            }
        }
        rootLayout.addView(btnCancelEntryEdit)

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT).apply {
                topMargin = 20
            }
        }
        val entryListLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 4, 0, 32)
        }
        scroll.addView(entryListLayout)
        rootLayout.addView(scroll)

        fun resetEntryForm() {
            editEntryId = null
            edtDesc.text.clear()
            edtAmount.text.clear()
            tvFormHeader.text = "孬亘鬲 鬲乇丕讴賳卮 噩丿蹖丿 (亘乇丕蹖 賵蹖乇丕蹖卮 乇賵蹖 鬲乇丕讴賳卮 亘夭賳蹖丿)"
            btnSale.text = "孬亘鬲 讴丕賱丕 (亘丿賴讴丕乇)"
            btnPayment.text = "丿乇蹖丕賮鬲 賵噩賴 (鬲爻賵蹖賴)"
            btnSale.visibility = View.VISIBLE
            btnPayment.visibility = View.VISIBLE
            btnCancelEntryEdit.visibility = View.GONE
        }

        btnCancelEntryEdit.setOnClickListener { resetEntryForm() }

        fun updateLedgerUI() {
            entryListLayout.removeAllViews()
            val custEntries = entries.filter { it.customerId == custId }
            val balance = custEntries.fold(0L) { acc, e -> if (e.type == "sale") acc + e.amount else acc - e.amount }

            tvBalance.text = when {
                balance > 0 -> "亘丿賴蹖 賲卮鬲乇蹖: %,d 鬲賵賲丕賳".format(balance)
                balance < 0 -> "胤賱亘 賲卮鬲乇蹖: %,d 鬲賵賲丕賳".format(abs(balance))
                else -> "丨爻丕亘 鬲爻賵蹖賴 丕爻鬲"
            }
            tvBalance.setTextColor(if (balance > 0) Color.parseColor("#B23A48") else Color.parseColor("#176B5B"))

            if (custEntries.isEmpty()) {
                val tv = TextView(this).apply {
                    text = "鬲乇丕讴賳卮蹖 孬亘鬲 賳卮丿賴 丕爻鬲."
                    gravity = Gravity.CENTER
                    setPadding(0, 40, 0, 0)
                    setTextColor(Color.parseColor("#66736F"))
                }
                entryListLayout.addView(tv)
                return
            }

            custEntries.reversed().forEach { e ->
                val borderCol = if (e.type == "sale") Color.parseColor("#B23A48") else Color.parseColor("#176B5B")

                // Full thick colored border around the entire entry card (10px)
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(28, 24, 28, 24)
                    background = makeRoundedCard(Color.WHITE, borderCol, 10, 22f)
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                        topMargin = 16
                    }
                }

                val infoBox = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    setOnClickListener {
                        editEntryId = e.id
                        edtDesc.setText(if (e.description == "丿乇蹖丕賮鬲 賵噩賴") "" else e.description)
                        edtAmount.setText(e.amount.toString())
                        tvFormHeader.text = "丿乇 丨丕賱 賵蹖乇丕蹖卮 鬲乇丕讴賳卮..."
                        if (e.type == "sale") {
                            btnSale.text = "匕禺蹖乇賴 鬲睾蹖蹖乇丕鬲 (亘丿賴讴丕乇)"
                            btnSale.visibility = View.VISIBLE
                            btnPayment.visibility = View.GONE
                        } else {
                            btnPayment.text = "匕禺蹖乇賴 鬲睾蹖蹖乇丕鬲 (鬲爻賵蹖賴)"
                            btnPayment.visibility = View.VISIBLE
                            btnSale.visibility = View.GONE
                        }
                        btnCancelEntryEdit.visibility = View.VISIBLE
                    }
                }

                val titlePrefix = if (e.type == "sale") "禺乇蹖丿: " else "倬乇丿丕禺鬲: "
                val tvEntryTitle = TextView(this).apply {
                    text = "$titlePrefix${e.description}"
                    textSize = 15f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.parseColor("#18312C"))
                }

                val tvEntrySub = TextView(this).apply {
                    text = "${e.date} 鈥� %,d 鬲賵賲丕賳".format(e.amount)
                    textSize = 14f
                    setTextColor(Color.parseColor("#4A5568"))
                    setPadding(0, 6, 0, 0)
                }

                infoBox.addView(tvEntryTitle)
                infoBox.addView(tvEntrySub)
                card.addView(infoBox)

                // Compact small delete button
                val btnDelEntry = Button(this).apply {
                    text = "丨匕賮"
                    textSize = 10f
                    background = makeRoundedCard(Color.parseColor("#B23A48"), Color.parseColor("#B23A48"), 0, 10f)
                    setTextColor(Color.WHITE)
                    minHeight = 0
                    minimumHeight = 0
                    minWidth = 0
                    minimumWidth = 0
                    setPadding(18, 8, 18, 8)
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                        marginStart = 12
                    }
                    setOnClickListener {
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("丨匕賮 鬲乇丕讴賳卮")
                            .setMessage("丕蹖賳 鬲乇丕讴賳卮 丨匕賮 卮賵丿責")
                            .setPositiveButton("亘賱賴") { _, _ ->
                                entries.remove(e)
                                resetEntryForm()
                                saveData()
                                updateLedgerUI()
                            }
                            .setNegativeButton("禺蹖乇", null)
                            .show()
                    }
                }
                card.addView(btnDelEntry)
                entryListLayout.addView(card)
            }
        }

        btnSale.setOnClickListener {
            val desc = edtDesc.text.toString().trim()
            val amount = edtAmount.text.toString().toLongOrNull() ?: 0L
            if (amount <= 0 || desc.isEmpty()) {
                Toast.makeText(this, "賳丕賲 讴丕賱丕 賵 賲亘賱睾 賲毓鬲亘乇 賵丕乇丿 讴賳蹖丿", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (editEntryId != null) {
                entries.find { it.id == editEntryId }?.apply {
                    type = "sale"
                    description = desc
                    this.amount = amount
                }
            } else {
                entries.add(Entry(System.currentTimeMillis(), custId, "sale", desc, amount, getJalaliDate()))
            }
            resetEntryForm()
            saveData()
            updateLedgerUI()
        }

        btnPayment.setOnClickListener {
            val desc = edtDesc.text.toString().trim()
            val amount = edtAmount.text.toString().toLongOrNull() ?: 0L
            if (amount <= 0) {
                Toast.makeText(this, "賲亘賱睾 賲毓鬲亘乇 賵丕乇丿 讴賳蹖丿", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val finalDesc = if (desc.isEmpty()) "丿乇蹖丕賮鬲 賵噩賴" else desc
            if (editEntryId != null) {
                entries.find { it.id == editEntryId }?.apply {
                    type = "payment"
                    description = finalDesc
                    this.amount = amount
                }
            } else {
                entries.add(Entry(System.currentTimeMillis(), custId, "payment", finalDesc, amount, getJalaliDate()))
            }
            resetEntryForm()
            saveData()
            updateLedgerUI()
        }

        setContentView(rootLayout)
        updateLedgerUI()
    }
}
