package com.example.daftarhesab

import android.app.Activity
import android.content.Intent
import android.content.Context
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

    data class Customer(
        val id: Long,
        var name: String,
        var phone: String = "",
        var address: String = ""
    )

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
                    put("phone", c.phone)
                    put("address", c.address)
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

    override fun onStop() {
        super.onStop()
        // پشتیبان‌گیری خودکار در پوشه اسناد هنگام خروج
        autoBackupAllStorages(this)

        // بررسی اینکه آیا ۷ روز از آخرین ارسال به تلگرام گذشته است یا خیر
        checkAndSuggestWeeklyTelegramBackup()
    }

    private fun checkAndSuggestWeeklyTelegramBackup() {
        val prefs = getSharedPreferences("DaftarPrefs", Context.MODE_PRIVATE)
        val lastSendTime = prefs.getLong("last_telegram_send", 0L)
        val currentTime = System.currentTimeMillis()
        val sevenDaysInMillis = 7L * 24L * 60L * 60L * 1000L

        // اگر ۷ روز (یا بیشتر) گذشته باشد
        if (currentTime - lastSendTime > sevenDaysInMillis) {
            // ثبت زمان جدید برای اینکه تا ۷ روز آینده دوباره پرسیده نشود
            prefs.edit().putLong("last_telegram_send", currentTime).apply()
            // اجرای ارسال به تلگرام
            sendBackupToTelegram()
        }
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
                customers.add(
                    Customer(
                        obj.getLong("id"),
                        obj.optString("name", ""),
                        obj.optString("phone", ""),
                        obj.optString("address", "")
                    )
                )
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
                newCusts.add(Customer(obj.getLong("id"), obj.getString("name"), obj.optString("phone", ""), obj.optString("address", "")))
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

    private fun sendBackupToTelegram() {
        try {
            val backupFile = File(cacheDir, "daftar_hesab_backup.json")
            backupFile.writeText(buildJsonString(), Charsets.UTF_8)
            val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.fileprovider", backupFile)

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                setPackage("org.telegram.messenger")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            try {
                startActivity(intent)
            } catch (e: Exception) {
                val generalIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(generalIntent, "ارسال پشتیبان هفتگی به تلگرام"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun makeRoundedCard(bgColor: Int, borderColor: Int, strokePx: Int, radiusPx: Float = 24f): GradientDrawable {
        return GradientDrawable().apply {
            setColor(bgColor)
            setStroke(strokePx, borderColor)
            cornerRadius = radiusPx
        }
    }

    private fun showCustomerDialog(existingCustomer: Customer? = null) {
        val dialogLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 32)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        val edtName = EditText(this).apply {
            hint = "نام و نام خانوادگی (اجباری)"
            setText(existingCustomer?.name ?: "")
            setPadding(28, 24, 28, 24)
            background = makeRoundedCard(Color.WHITE, Color.parseColor("#CCCCCC"), 2, 16f)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 16
            }
        }
        dialogLayout.addView(edtName)

        val edtPhone = EditText(this).apply {
            hint = "شماره تماس (اختیاری)"
            setText(existingCustomer?.phone ?: "")
            inputType = android.text.InputType.TYPE_CLASS_PHONE
            setPadding(28, 24, 28, 24)
            background = makeRoundedCard(Color.WHITE, Color.parseColor("#CCCCCC"), 2, 16f)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 16
            }
        }
        dialogLayout.addView(edtPhone)

        val edtAddress = EditText(this).apply {
            hint = "آدرس (اختیاری)"
            setText(existingCustomer?.address ?: "")
            setPadding(28, 24, 28, 24)
            background = makeRoundedCard(Color.WHITE, Color.parseColor("#CCCCCC"), 2, 16f)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        dialogLayout.addView(edtAddress)

        val titleText = if (existingCustomer == null) "افزودن مشتری جدید" else "ویرایش مشخصات مشتری"

        AlertDialog.Builder(this)
            .setTitle(titleText)
            .setView(dialogLayout)
            .setPositiveButton("ذخیره") { _, _ ->
                val name = edtName.text.toString().trim()
                val phone = edtPhone.text.toString().trim()
                val address = edtAddress.text.toString().trim()

                if (name.isEmpty()) {
                    Toast.makeText(this, "وارد کردن نام و نام خانوادگی اجباری است", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (existingCustomer == null) {
                    customers.add(Customer(System.currentTimeMillis(), name, phone, address))
                } else {
                    existingCustomer.name = name
                    existingCustomer.phone = phone
                    existingCustomer.address = address
                }

                saveData(this)
                renderHome()
            }
            .setNegativeButton("لغو", null)
            .show()
    }

    private fun renderHome() {
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            setBackgroundColor(Color.parseColor("#F5F7F6"))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 16
            }
        }

        val tvTitle = TextView(this).apply {
            text = "📔 دفتر حساب من"
            textSize = 19f
            setTextColor(Color.parseColor("#176B5B"))
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val btnMenu = Button(this).apply {
            text = "⋮"
            textSize = 22f
            setBackgroundColor(Color.TRANSPARENT)
            setTextColor(Color.parseColor("#176B5B"))
            minWidth = 0
            minimumWidth = 0
            setPadding(16, 0, 16, 0)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            setOnClickListener { view ->
                val popup = PopupMenu(this@MainActivity, view)
                popup.menu.add(0, 1, 0, "ذخیره پشتیبان")
                popup.menu.add(0, 2, 1, "بازیابی پشتیبان")
                popup.menu.add(0, 3, 2, "ارسال پشتیبان به تلگرام")
                popup.setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        1 -> {
                            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = "application/json"
                                putExtra(Intent.EXTRA_TITLE, "daftar_hesab_backup.json")
                            }
                            manualBackupLauncher.launch(intent)
                            true
                        }
                        2 -> {
                            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = "*/*"
                            }
                            restoreLauncher.launch(intent)
                            true
                        }
                        3 -> {
                            sendBackupToTelegram()
                            true
                        }
                        else -> false
                    }
                }
                popup.show()
            }
        }

        topBar.addView(tvTitle)
        topBar.addView(btnMenu)
        rootLayout.addView(topBar)

        val btnAddCust = Button(this).apply {
            text = "+ افزودن مشتری جدید"
            background = makeRoundedCard(Color.parseColor("#176B5B"), Color.parseColor("#176B5B"), 0, 16f)
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = 20
            }
            setOnClickListener {
                showCustomerDialog(null)
            }
        }
        rootLayout.addView(btnAddCust)

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
                        val options = arrayOf("ویرایش مشخصات مشتری", "حذف مشتری")
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("مدیریت مشتری: ${c.name}")
                            .setItems(options) { _, which ->
                                when (which) {
                                    0 -> {
                                        showCustomerDialog(c)
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

                val infoSubText = if (c.phone.isNotEmpty()) "${c.phone}  |  " else ""
                val tvBalance = TextView(this).apply {
                    text = when {
                        balance > 0 -> String.format(Locale.US, "$infoSubText بدهکار: %,d تومان", balance)
                        balance < 0 -> String.format(Locale.US, "$infoSubText طلبکار: %,d تومان", abs(balance))
                        else -> "${infoSubText}تسویه"
                    }
                    textSize = 13f
                    setTextColor(if (balance > 0) Color.parseColor("#B23A48") else Color.parseColor("#176B5B"))
                    setPadding(0, 6, 0, 0)
                }

                infoLayout.addView(tvName)
                infoLayout.addView(tvBalance)
                card.addView(infoLayout)
                listLayout.addView(card)
            }
        }

        setContentView(rootLayout)
        refreshListUI()
    }
}
