package com.example.daftarhesab

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.util.*
import kotlin.math.abs

class LedgerActivity : AppCompatActivity() {

    private var custId: Long = 0L
    private var editEntryId: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        custId = intent.getLongExtra("CUSTOMER_ID", 0L)
        renderLedger()
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

    private fun renderLedger() {
        val c = MainActivity.customers.find { it.id == custId }
        if (c == null) {
            finish()
            return
        }

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
            setOnClickListener { finish() }
        }
        rootLayout.addView(btnBack)

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
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = 8 }
        }
        val btnPayment = Button(this).apply {
            text = "دریافت وجه (تسویه)"
            background = makeRoundedCard(Color.parseColor("#176B5B"), Color.parseColor("#176B5B"), 0, 16f)
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {marginStart = 8}
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
            val custEntries = MainActivity.entries.filter { it.customerId == custId }
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
                        AlertDialog.Builder(this@LedgerActivity)
                            .setTitle("حذف تراکنش")
                            .setMessage("آیا از حذف این تراکنش مطمئن هستید؟")
                            .setPositiveButton("بله") { _, _ ->
                                MainActivity.entries.remove(e)
                                resetEntryForm()
                                MainActivity.saveData(this@LedgerActivity)
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
                MainActivity.entries.find { it.id == editEntryId }?.let { entry ->
                    entry.type = "sale"
                    entry.description = desc
                    entry.amount = amount
                }
            } else {
                MainActivity.entries.add(MainActivity.Entry(System.currentTimeMillis(), custId, "sale", desc, amount, getJalaliDate()))
            }
            resetEntryForm()
            MainActivity.saveData(this)
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
                MainActivity.entries.find { it.id == editEntryId }?.let { entry ->
                    entry.type = "payment"
                    entry.description = finalDesc
                    entry.amount = amount
                }
            } else {
                MainActivity.entries.add(MainActivity.Entry(System.currentTimeMillis(), custId, "payment", finalDesc, amount, getJalaliDate()))
            }
            resetEntryForm()
            MainActivity.saveData(this)
            updateLedgerUI()
        }

        setContentView(rootLayout)
        updateLedgerUI()
    }
}
