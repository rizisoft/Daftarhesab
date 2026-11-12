package com.example.daftarhesab

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Environment
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

    data class Customer(val id: Long, var name: String)
    data class Entry(
        val id: Long,
        val customerId: Long,
        var type: String,
        var description: String,
        var amount: Long,
        val date: String
    )

    companion object {
        val customers = mutableListOf<Customer>()
        val entries = mutableListOf<Entry>()
        lateinit var dbFile: File

        fun buildJsonString(): String {
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

        fun saveData(activity: AppCompatActivity) {
            try {
                dbFile.writeText(buildJsonString(), Charsets.UTF_8)
                autoBackupAllStorages(activity)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        private fun cleanOldBackups(directory: File) {
            try {
                if (!directory.exists() || !directory.isDirectory) return
                val files = directory.listFiles() ?: return
                val now = System.currentTimeMillis()
                val thirtyDaysInMillis = 30L * 24L * 60L * 60L * 1000L

                for (file in files) {
                    if (file.isFile && file.name.startsWith("DaftarHesab_") && file.name.endsWith(".json")) {
                        if (now - file.lastModified() > thirtyDaysInMillis) {
                            file.delete()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun autoBackupAllStorages(activity: AppCompatActivity) {
            val jsonContent = buildJsonString()
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
            val dateStr = String.format(Locale.US, "%04d-%02d-%02d", jy, jm, jd)
            val fileName = "DaftarHesab_$dateStr.json"

            try {
                val publicDocDir = activity.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: activity.filesDir
                val targetDir = File(publicDocDir, "DaftarHesab")
                if (!targetDir.exists()) targetDir.mkdirs()
                File(targetDir, fileName).writeText(jsonContent, Charsets.UTF_8)
                cleanOldBackups(targetDir)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private var editCustId: Long? = null
    private var backPressedTime: Long = 0L

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
                    Toast.makeText(this, "فایل پشتیبان با موفقیت ذخیره شد", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "خطا در ذخیره پشتیبان", Toast.LENGTH_SHORT).show()
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

    override fun onResume() {
        super.onResume()
        renderHome()
    }

    override fun onBackPressed() {
        if (backPressedTime + 2000 > System.currentTimeMillis()) {
            super.onBackPressed()
        } else {
            Toast.makeText(this, "برای خروج دوباره کلید بازگشت را بزنید", Toast.LENGTH_SHORT).show()
            backPressedTime = System.currentTimeMillis()
        }
    }

    private fun loadData() {
        if (!dbFile.exists()) return
        try {
            val jsonText = dbFile.readText(Charsets.UTF_8)
            val root = JSONObject(jsonText)
            val custArray = root.getJSONArray("customers")
            customers.clear()
            for (i in 0 until custArray.length()) {
                val obj = custArray.getJSONObject(i)
                customers.add(Customer(obj.getLong("id"), obj.getString("name")))
            }

            val entryArray = root.getJSONArray("entries")
            entries.clear()
            for (i in 0 until entryArray.length()) {
                val obj = entryArray.getJSONObject(i)
                entries.add(
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
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun restoreFromUri(uri: Uri) {
        try {
            val text = contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: return
            val root = JSONObject(text)
            val custArray = root.getJSONArray("customers")
            val newCusts = mutableListOf<Customer>()
            for (i in 0 until custArray.length()) {
                val obj = custArray.getJSONObject(i)
                newCusts.add(Customer(obj.getLong("id"), obj.getString("name")))
            }
            val entryArray = root.getJSONArray("entries")
            val newEntries = mutableListOf<Entry>()
            for (i in 0 until entryArray.length()) {
                val obj = entryArray.getJSONObject(i)
                newEntries.add(Entry(obj.getLong("id"), obj.getLong("customerId"), obj.getString("type"), obj.getString("description"), obj.getLong("amount"), obj.getString("date")))
            }
            customers.clear()
            customers.addAll(newCusts)
            entries.clear()
            entries.addAll(newEntries)
            saveData(this)
            renderHome()
            Toast.makeText(this, "اطلاعات بازیابی شد!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "خطا در خواندن فایل", Toast.LENGTH_SHORT).show()
        }
    }

    private fun makeRoundedCard(bgColor: Int, borderColor: Int, strokePx: Int, radiusPx: Float = 24f): GradientDrawable {
        return GradientDrawable().apply {
            setColor(bgColor)
            setStroke(strokePx, borderColor)
            cornerRadius = radiusPx
        }
    }

    private fun renderHome() {
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            setBackgroundColor(Color.parseColor("#F5F7F6"))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        val tvTitle = TextView(this).apply {
            text = "📔 دفتر حساب من"
            textSize = 21f
            setTextColor(Color.parseColor("#176B5B"))
            gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 8, 0, 24)
        }
        rootLayout.addView(tvTitle)

        val edtCustName = EditText(this).apply {
            hint = "نام مشتری جدید"
            setPadding(28, 24, 28, 24)
            background = makeRoundedCard(Color.WHITE, Color.parseColor("#CCCCCC"), 2, 16f)
        }
        rootLayout.addView(edtCustName)

        val btnAddCust = Button(this).apply {
            text = "+ افزودن مشتری جدید"
            background = makeRoundedCard(Color.parseColor("#176B5B"), Color.parseColor("#176B5B"), 0, 16f)
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 16
            }
        }
        rootLayout.addView(btnAddCust)

        val btnCancelEdit = Button(this).apply {
            text = "لغو ویرایش"
            background = makeRoundedCard(Color.parseColor("#718096"), Color.parseColor("#718096"), 0, 16f)
            setTextColor(Color.WHITE)
            visibility = if (editCustId != null) View.VISIBLE else View.GONE
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 12
            }
        }
        rootLayout.addView(btnCancelEdit)
        if (editCustId != null) {
            val c = customers.find { it.id == editCustId }
            if (c != null) edtCustName.setText(c.name)
            btnAddCust.text = "ذخیره تغییرات مشتری"
        }

        val backupRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = 20
                bottomMargin = 12
            }
        }

        val btnBackup = Button(this).apply {
            text = "ذخیره پشتیبان"
            textSize = 12f
            background = makeRoundedCard(Color.parseColor("#2C5282"), Color.parseColor("#2C5282"), 0, 16f)
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = 8 }
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
            text = "بازیابی پشتیبان"
            textSize = 12f
            background = makeRoundedCard(Color.parseColor("#718096"), Color.parseColor("#718096"), 0, 16f)
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {marginStart = 8}
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

        fun refreshListUI() {
            listLayout.removeAllViews()
            if (customers.isEmpty()) {
                val tv = TextView(this).apply {
                    text = "هنوز مشتری‌ای ثبت نشده است."
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
                    isLongClickable = true
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                        topMargin = 14
                    }
                    setOnClickListener {
                        val intent = Intent(this@MainActivity, LedgerActivity::class.java).apply {
                            putExtra("CUSTOMER_ID", c.id)
                        }
                        startActivity(intent)
                    }
                    setOnLongClickListener {
                        val options = arrayOf("ویرایش نام مشتری", "حذف مشتری")
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("مدیریت مشتری: ${c.name}")
                            .setItems(options) { _, which ->
                                when (which) {
                                    0 -> {
                                        AlertDialog.Builder(this@MainActivity)
                                            .setTitle("ویرایش نام مشتری")
                                            .setMessage("آیا برای ویرایش این مشتری مطمئن هستید؟")
                                            .setPositiveButton("بله") { _, _ ->
                                                editCustId = c.id
                                                edtCustName.setText(c.name)
                                                btnAddCust.text = "ذخیره تغییرات مشتری"
                                                btnCancelEdit.visibility = View.VISIBLE
                                            }
                                            .setNegativeButton("خیر", null)
                                            .show()
                                    }
                                    1 -> {
                                        AlertDialog.Builder(this@MainActivity)
                                            .setTitle("حذف مشتری")
                                            .setMessage("آیا برای حذف '${c.name}' و تمام تراکنش‌های او مطمئن هستید؟")
                                            .setPositiveButton("بله") { _, _ ->
                                                customers.remove(c)
                                                entries.removeAll { it.customerId == c.id }
                                                saveData(this@MainActivity)
                                                refreshListUI()
                                            }
                                            .setNegativeButton("خیر", null)
                                            .show()
                                    }
                                }
                            }
                            .show()
                        true
                    }
                }

                val infoLayout = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                }

                val tvName = TextView(this).apply {
                    text = c.name
                    textSize = 17f
                    setTextColor(Color.parseColor("#18312C"))
                    setTypeface(null, Typeface.BOLD)
                }

                val tvBalance = TextView(this).apply {
                    text = when {
                        balance > 0 -> String.format(Locale.US, "بدهکار: %,d تومان", balance)
                        balance < 0 -> String.format(Locale.US, "طلبکار: %,d تومان", abs(balance))
                        else -> "تسویه"
                    }
                    textSize = 14f
                    setTextColor(if (balance > 0) Color.parseColor("#B23A48") else Color.parseColor("#176B5B"))
                    setPadding(0, 6, 0, 0)
                }

                infoLayout.addView(tvName)
                infoLayout.addView(tvBalance)
                card.addView(infoLayout)
                listLayout.addView(card)
            }
        }

        btnAddCust.setOnClickListener {
            val name = edtCustName.text.toString().trim()
            if (name.isEmpty()) {
                Toast.makeText(this, "نام مشتری را وارد کنید", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (editCustId != null) {
                customers.find { c -> c.id == editCustId }?.name = name
                editCustId = null
                btnAddCust.text = "+ افزودن مشتری جدید"
                btnCancelEdit.visibility = View.GONE
            } else {
                customers.add(Customer(System.currentTimeMillis(), name))
            }

            edtCustName.text.clear()
            saveData(this)
            refreshListUI()
        }

        btnCancelEdit.setOnClickListener {
            editCustId = null
            edtCustName.text.clear()
            btnAddCust.text = "+ افزودن مشتری جدید"
            btnCancelEdit.visibility = View.GONE
        }

        setContentView(rootLayout)
        refreshListUI()
    }
}
