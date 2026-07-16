package com.futo.futopay

import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.PictureDrawable
import android.icu.text.NumberFormat
import android.icu.util.Currency
import com.caverock.androidsvg.SVG
import java.util.Locale
import kotlin.math.pow


private val _assetFlags = HashMap<String, Drawable>();

fun isFlagsInitialized(): Boolean {
    synchronized(_assetFlags) {
        return _assetFlags.size > 0;
    }
}
fun initFlags(context: Context)
{
    synchronized(_assetFlags) {
        if(_assetFlags.size > 0)
            return;

        val assetManager = context.getAssets();
        val flags = assetManager.list("flags");
        if (flags != null) {
            for(flag in flags.filter { it.endsWith(".svg") }) {
                try {
                    assetManager.open("flags/" + flag).use { `is` ->
                        val svg = SVG.getFromInputStream(`is`)
                        val drawable: Drawable = PictureDrawable(svg.renderToPicture());
                        _assetFlags[flag.substring(0, flag.indexOf(".svg"))] = drawable;
                    }
                } catch (e: Exception) {

                }
            }
        }
    }
}

// Currently only USD is supported by the Polar payment backend.
// This function handles multiple currencies for future use when Polar adds support.
fun formatMoney(countryCode: String, currency: String, amount: Long): String {
    fun getCurrencyDecimalPlaces(c: String): Int {
        return when (c.uppercase()) {
            "ISK", "HUF", "TWD", "UGX" -> 2
            "BIF", "CLP", "DJF", "GNF", "JPY", "KMF", "KRW", "MGA", "PYG", "RWF", "VND", "VUV", "XAF", "XOF", "XPF" -> 0
            "BHD", "JOD", "KWD", "OMR", "TND" -> 3
            else -> 2
        }
    }

    val decimalPlaces = getCurrencyDecimalPlaces(currency)
    val adjustedAmount = amount / 10.0.pow(decimalPlaces.toDouble())
    val locale = Locale(countryCode, currency)
    val currencyInstance = Currency.getInstance(currency.uppercase())
    val numberFormat = NumberFormat.getCurrencyInstance(locale).apply {
        minimumFractionDigits = decimalPlaces
        maximumFractionDigits = decimalPlaces
        this.currency = currencyInstance
    }
    val formatted = numberFormat.format(adjustedAmount)
    return formatted.replace(Regex("[.,]00$"), "")
}

fun getCountryDrawable(context: Context, name: String): Drawable? {
    val code = name.lowercase();
    initFlags(context);
    return _assetFlags[code];
}