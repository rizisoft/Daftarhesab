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
import androidx.activity.OnBackPressedCallback
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

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (activeCustId != null) {
                    renderHome()
                } else {
                    if (backPressedTime + 2000 > System.currentTimeMillis()) {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    } else {
                        Toast.makeText(this@MainActivity, "برای خروج دوباره کلید بازگشت را بزنید", Toast.LENGTH_SHORT).show()
                        backPressedTime = System.currentTimeMillis()
                    }
                }
            }
        })
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

    private fun cleanOldBackups(directory: File) {
        try {
            if (!directory.exists() || !directory.isDirectory) return
            val files = directory.listFiles() ?: return
            val now = System.currentTimeMillis()
            val thirtyDaysInMillis = 30L * 24L * 60L * 60L * 1000L

            for (file in files) {
                if (file.isFile && file.name.startsWith("DaftarHesab_") && file.name.endsWith(".json")) {
                    val lastModified = file.lastModified()
                    if (now - lastModified > thirtyDaysInMillis) {
                        file.delete()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun autoBackupAllStorages() {
        val jsonContent = buildJsonString()
        val dateStr = getJalaliDate().replace("/", "-")
        val fileName = "DaftarHesab_$dateStr.json"

        try {
            val publicDocDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val targetDir = File(publicDocDir, "DaftarHesab")
            if (!targetDir.exists()) targetDir.mkdirs()
            File(targetDir, fileName).writeText(jsonContent, Charsets.UTF_8)
            cleanOldBackups(targetDir)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            val externalDirs = getExternalFilesDirs(Environment.DIRECTORY_DOCUMENTS)
            if (externalDirs != null && externalDirs.size > 1) {
                for (i in 1 until externalDirs.size) {
                    val sdDir = externalDirs[i]
                    if (sdDir != null && Environment.getExternalStorageState(sdDir) == Environment.MEDIA_MOUNTED) {
                        val rootSd = sdDir.absolutePath.substringBefore("/Android")
                        val simpleSdFolder = File(rootSd, "Documents/DaftarHesab")
                        if (!simpleSdFolder.exists()) simpleSdFolder.mkdirs()
                        File(simpleSdFolder, fileName).writeText(jsonContent, Charsets.UTF_8)
                        cleanOldBackups(simpleSdFolder)
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
                Toast.makeText(this, "اطلاعات با موفقیت بازیابی شد!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "فایل پشتیبان معتبر نیست", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "خطا در خواندن فایل", Toast.LENGTH_SHORT).show()
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
            text = "ذخیره پشتیبان"
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
            text = "بازیابی پشتیبان"
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
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                        topMargin = 14
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
                                                saveData()
                                                refreshList()
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
                customers.find { it.id == editCustId }?.name = name
                editCustId = null
                btnAddCust.text = "+ افزودن مشتری جدید"
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
            btnAddCust.text = "+ افزودن مشتری جدید"
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
            text = "← بازگشت"
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
            text = "ثبت تراکنش جدید (برای ویرایش روی تراکنش بزنید)"
            textSize = 13f
            setTextColor(Color.parseColor("#66736F"))
            setPadding(0, 0, 0, 8)
        }
        rootLayout.addView(tvFormHeader)

        val edtDesc = EditText(this).apply {
            hint = "نام کالا یا شرح"
            setPadding(24, 20, 24, 20)
            background = makeRoundedCard(Color.WHITE, Color.parseColor("#CCCCCC"), 2, 16f)
        }
        rootLayout.addView(edtDesc)

        val edtAmount = EditText(this).apply {
            hint = "مبلغ (تومان)"
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
            text = "ثبت کالا (بدهکار)"
            background = makeRoundedCard(Color.parseColor("#B23A48"), Color.parseColor("#B23A48"), 0, 16f)
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = 8
            }
        }
        val btnPayment = Button(this).apply {
            text = "دریافت وجه (تسویه)"
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
            text = "لغو ویرایش"
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
            tvFormHeader.text = "ثبت تراکنش جدید (برای ویرایش روی تراکنش بزنید)"
            btnSale.text = "ثبت کالا (بدهکار)"
            btnPayment.text = "دریافت وجه (تسویه)"
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
                balance > 0 -> String.format(Locale.US, "بدهی مشتری: %,d تومان", balance)
                balance < 0 -> String.format(Locale.US, "طلب مشتری: %,d تومان", abs(balance))
                else -> "حساب تسویه است"
            }
            tvBalance.setTextColor(if (balance > 0) Color.parseColor("#B23A48") else Color.parseColor("#176B5B"))

            if (custEntries.isEmpty()) {
                val tv = TextView(this).apply {
                    text = "تراکنشی ثبت نشده است."
                    gravity = Gravity.CENTER
                    setPadding(0, 40, 0, 0)
                    setTextColor(Color.parseColor("#66736F"))
                }
                entryListLayout.addView(tv)
                return
            }

            val defaultPayDesc = "دریافت وجه"
            val prefixSale = "خرید: "
            val prefixPay = "پرداخت: "

            custEntries.reversed().forEach { e ->
                val borderCol = if (e.type == "sale") Color.parseColor("#B23A48") else Color.parseColor("#176B5B")

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
                        edtDesc.setText(if (e.description == defaultPayDesc) "" else e.description)
                        edtAmount.setText(e.amount.toString())
                        tvFormHeader.text = "در حال ویرایش تراکنش..."
                        if (e.type == "sale") {
                            btnSale.text = "ذخیره تغییرات (بدهکار)"
                            btnSale.visibility = View.VISIBLE
                            btnPayment.visibility = View.GONE
                        } else {
                            btnPayment.text = "ذخیره تغییرات (تسویه)"
                            btnPayment.visibility = View.VISIBLE
                            btnSale.visibility = View.GONE
                        }
                        btnCancelEntryEdit.visibility = View.VISIBLE
                    }
                }

                val titlePrefix = if (e.type == "sale") prefixSale else prefixPay
                val tvEntryTitle = TextView(this).apply {
                    text = titlePrefix + e.description
                    textSize = 15f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.parseColor("#18312C"))
                }

                val tvEntrySub = TextView(this).apply {
                    text = String.format(Locale.US, "%s — %,d تومان", e.date, e.amount)
                    textSize = 14f
                    setTextColor(Color.parseColor("#4A5568"))
                    setPadding(0, 6, 0, 0)
                }

                infoBox.addView(tvEntryTitle)
                infoBox.addView(tvEntrySub)
                card.addView(infoBox)

                val btnDelEntry = Button(this).apply {
                    text = "حذف"
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
                            .setTitle("حذف تراکنش")
                            .setMessage("آیا از حذف این تراکنش مطمئن هستید؟")
                            .setPositiveButton("بله") { _, _ ->
                                entries.remove(e)
                                resetEntryForm()
                                saveData()
                                updateLedgerUI()
                            }
                            .setNegativeButton("خیر", null)
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
                Toast.makeText(this, "نام کالا و مبلغ معتبر وارد کنید", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (editEntryId != null) {
                entries.find { it.id == editEntryId }?.let { entry ->
                    entry.type = "sale"
                    entry.description = desc
                    entry.amount = amount
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
                Toast.makeText(this, "مبلغ معتبر وارد کنید", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val finalDesc = if (desc.isEmpty()) "دریافت وجه" else desc
            if (editEntryId != null) {
                entries.find { it.id == editEntryId }?.let { entry ->
                    entry.type = "payment"
                    entry.description = finalDesc
                    entry.amount = amount
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
