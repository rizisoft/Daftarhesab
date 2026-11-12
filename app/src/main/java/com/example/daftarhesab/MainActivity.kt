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
                    Toast.makeText(this, "\u0641\u0627\u06CC\u0644 \u067E\u0634\u062A\u06CC\u0628\u0627\u0646 \u0628\u0627 \u0645\u0648\u0641\u0642\u06CC\u062A \u0630\u062E\u06CC\u0631\u0647 \u0634\u062F", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "\u062E\u0637\u0627 \u062F\u0631 \u0630\u062E\u06CC\u0631\u0647 \u067E\u0634\u062A\u06CC\u0628\u0627\u0646", Toast.LENGTH_SHORT).show()
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
        autoBackupAllStorages()
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
        dbFile.writeText(buildJsonString(), Charsets.UTF_8)
        autoBackupAllStorages()
    }

    private fun autoBackupAllStorages() {
        val jsonContent = buildJsonString()
        
        try {
            val publicDocDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val targetDir = File(publicDocDir, "DaftarHesab")
            if (!targetDir.exists()) targetDir.mkdirs()
            File(targetDir, "DaftarHesab_AutoBackup.json").writeText(jsonContent, Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            val internalDir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: filesDir
            if (!internalDir.exists()) internalDir.mkdirs()
            File(internalDir, "DaftarHesab_AutoBackup.json").writeText(jsonContent, Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            val externalDirs = getExternalFilesDirs(Environment.DIRECTORY_DOCUMENTS)
            if (externalDirs != null && externalDirs.size > 1) {
                for (i in 1 until externalDirs.size) {
                    val sdDir = externalDirs[i]
                    if (sdDir != null && Environment.getExternalStorageState(sdDir) == Environment.MEDIA_MOUNTED) {
                        if (!sdDir.exists()) sdDir.mkdirs()
                        File(sdDir, "DaftarHesab_SD_Backup.json").writeText(jsonContent, Charsets.UTF_8)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadData() {
        if (!dbFile.exists()) return
        parseJsonAndLoad(dbFile.readText(Charsets.UTF_8))
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
            val text = contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: return
            if (parseJsonAndLoad(text)) {
                saveData()
                renderHome()
                Toast.makeText(this, "\u0627\u0637\u0644\u0627\u0639\u0627\u062A \u0628\u0627 \u0645\u0648\u0641\u0642\u06CC\u062A \u0628\u0627\u0632\u06CC\u0627\u0628\u06CC \u0634\u062F!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "\u0641\u0627\u06CC\u0644 \u067E\u0634\u062A\u06CC\u0628\u0627\u0646 \u0645\u0639\u062A\u0628\u0631 \u0646\u06CC\u0633\u062A", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "\u062E\u0637\u0627 \u062F\u0631 \u062E\u0648\u0627\u0646\u062F\u0646 \u0641\u0627\u06CC\u0644", Toast.LENGTH_SHORT).show()
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
            text = "\uD83D\uDCD4 \u062F\u0641\u062A\u0631 \u062D\u0633\u0627\u0628 \u0645\u0646"
            textSize = 21f
            setTextColor(Color.parseColor("#176B5B"))
            gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 8, 0, 24)
        }
        rootLayout.addView(tvTitle)

        val edtCustName = EditText(this).apply {
            hint = "\u0646\u0627\u0645 \u0645\u0634\u062A\u0631\u06CC \u062C\u062F\u06CC\u062F"
            setPadding(28, 24, 28, 24)
            background = makeRoundedCard(Color.WHITE, Color.parseColor("#CCCCCC"), 2, 16f)
        }
        rootLayout.addView(edtCustName)

        val btnAddCust = Button(this).apply {
            text = "+ \u0627\u0641\u0632\u0648\u062F\u0646 \u0645\u0634\u062A\u0631\u06CC \u062C\u062F\u06CC\u062F"
            background = makeRoundedCard(Color.parseColor("#176B5B"), Color.parseColor("#176B5B"), 0, 16f)
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 16
            }
        }
        rootLayout.addView(btnAddCust)

        val btnCancelEdit = Button(this).apply {
            text = "\u0644\u063A\u0648 \u0648\u06CC\u0631\u0627\u06CC\u0634"
            background = makeRoundedCard(Color.parseColor("#718096"), Color.parseColor("#718096"), 0, 16f)
            setTextColor(Color.WHITE)
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 12
            }
        }
        rootLayout.addView(btnCancelEdit)

        val backupRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 20
                bottomMargin = 12
            }
        }

        val btnBackup = Button(this).apply {
            text = "\u0630\u062E\u06CC\u0631\u0647 \u067E\u0634\u062A\u06CC\u0628\u0627\u0646"
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
            text = "\u0628\u0627\u0632\u06CC\u0627\u0628\u06CC \u067E\u0634\u062A\u06CC\u0628\u0627\u0646"
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
                    text = "\u0647\u0646\u0648\u0632 \u0645\u0634\u062A\u0631\u06CC\u200C\u0627\u06CC \u062B\u0628\u062A \u0646\u0634\u062F\u0647 \u0627\u0633\u062A."
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
                        balance > 0 -> String.format(Locale.US, "\u0628\u062F\u0647\u06A9\u0627\u0631: %,d \u062A\u0648\u0645\u0627\u0646", balance)
                        balance < 0 -> String.format(Locale.US, "\u0637\u0644\u0628\u06A9\u0627\u0631: %,d \u062A\u0648\u0645\u0627\u0646", abs(balance))
                        else -> "\u062A\u0633\u0648\u06CC\u0647"
                    }
                    textSize = 14f
                    setTextColor(if (balance > 0) Color.parseColor("#B23A48") else Color.parseColor("#176B5B"))
                    setPadding(0, 6, 0, 0)
                }

                infoLayout.addView(tvName)
                infoLayout.addView(tvBalance)
                card.addView(infoLayout)

                val btnEdit = Button(this).apply {
                    text = "\u0648\u06CC\u0631\u0627\u06CC\u0634"
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
                        btnAddCust.text = "\u0630\u062E\u06CC\u0631\u0647 \u062A\u063A\u06CC\u06CC\u0631\u0627\u062A \u0645\u0634\u062A\u0631\u06CC"
                        btnCancelEdit.visibility = View.VISIBLE
                    }
                }
                card.addView(btnEdit)

                val btnDel = Button(this).apply {
                    text = "\u062D\u0630\u0641"
                    textSize = 11f
                    background = makeRoundedCard(Color.parseColor("#B23A48"), Color.parseColor("#B23A48"), 0, 12f)
                    setTextColor(Color.WHITE)
                    minHeight = 0
                    minimumHeight = 0
                    setPadding(20, 12, 20, 12)
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    setOnClickListener {
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("\u062D\u0630\u0641 \u0645\u0634\u062A\u0631\u06CC")
                            .setMessage("\u062A\u0645\u0627\u0645 \u062A\u0631\u0627\u06A9\u0646\u0634\u200C\u0647\u0627\u06CC \u0627\u06CC\u0646 \u0645\u0634\u062A\u0631\u06CC \u0646\u06CC\u0632 \u062D\u0630\u0641 \u062E\u0648\u0627\u0647\u0646\u062F \u0634\u062F. \u0627\u062F\u0627\u0645\u0647 \u0645\u06CC\u200C\u062F\u0647\u06CC\u062F\u061F")
                            .setPositiveButton("\u0628\u0644\u0647") { _, _ ->
                                customers.remove(c)
                                entries.removeAll { it.customerId == c.id }
                                saveData()
                                refreshList()
                            }
                            .setNegativeButton("\u062E\u06CC\u0631", null)
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
                Toast.makeText(this, "\u0646\u0627\u0645 \u0645\u0634\u062A\u0631\u06CC \u0631\u0627
