package com.futo.futopay

import android.util.Log
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

abstract class PaymentState {
    val REGEX_KEY_FORMAT = Regex("[a-zA-Z0-9-]{4}-[a-zA-Z0-9-]{4}-[a-zA-Z0-9-]{4}-[a-zA-Z0-9-]{4}-[a-zA-Z0-9-]{4}-[a-zA-Z0-9-]{4}-[a-zA-Z0-9-]{4}-[a-zA-Z0-9-]{4}");

    val URL_POLAR_BASE = if(!isTesting) "https://pay2.futo.org" else "https://staging-pay2.futo.org";

    protected val polarOrgSlug get() = if(!isTesting) PaymentConfigurations.PolarConfig.ORG_SLUG else PaymentConfigurations.PolarConfigTesting.ORG_SLUG;
    protected val polarProductSlug get() = if(!isTesting) PaymentConfigurations.PolarConfig.PRODUCT_SLUG else PaymentConfigurations.PolarConfigTesting.PRODUCT_SLUG;
    protected val polarProductId get() = if(!isTesting) PaymentConfigurations.PolarConfig.PRODUCT_ID else PaymentConfigurations.PolarConfigTesting.PRODUCT_ID;
    private val URL_CURRENCIES = "${URL_POLAR_BASE}/api/v1/payment/currencies";
    private val URL_PRICES = "${URL_POLAR_BASE}/api/v1/payment/prices";
    private val URL_ACTIVATION_URL = "${URL_POLAR_BASE}/api/v1/activate/";

    private val _currencyCache = HashMap<String, List<String>>();
    private val _priceCache = HashMap<String, HashMap<String, Long>>();
    private var _validator: LicenseValidator

    var hasPaid: Boolean = false;
    var hasPaidChanged = Event1<Boolean>();

    protected open val isTesting get() = false;

    constructor(validationPublicKey: String) {
        _validator = LicenseValidator(normalizePublicKey(validationPublicKey))
    }

    fun initialize() {
        val license = getPaymentKey();
        if(_validator.validate(license.first, license.second)) {
            hasPaid = true;
            //Initial load does not send change event
        }
    }

    fun clearLicenses() {
        savePaymentKey("", "");
        hasPaid = false;
        hasPaidChanged.emit(false);
    }

    fun setPaymentLicense(anyKey: String): Boolean {
        return (REGEX_KEY_FORMAT.matches(anyKey) && setPaymentLicenseKey(anyKey)) ||
            setPaymentLicenseUrl(anyKey);
    }

    fun setPaymentLicenseKey(key: String): Boolean {
        val activationKeyResponse = httpGET(URL_ACTIVATION_URL + key);
        if(activationKeyResponse.isSuccessful)
            return setPaymentLicenseUrl("${key}/${activationKeyResponse.body!!}");
        else
            throw IllegalStateException("Request failed [${activationKeyResponse.code}]\n" + activationKeyResponse.body);
    }

    fun setPaymentLicenseUrl(url: String): Boolean {
        var urlToUse = if(url.startsWith("grayjay://", true))
            url.substring("grayjay://".length);
        else
            url;
        if(urlToUse.startsWith("license/", true))
            urlToUse = urlToUse.substring("license/".length);

        val parts = urlToUse.split("/");
        if(parts.size != 2)
            return false;

        val licenseKey = parts[0];
        val activationKey = parts[1];

        return setPaymentLicense(licenseKey, activationKey);
    }
    fun setPaymentLicense(licenseKey: String, activationKey: String): Boolean {
        Log.d("PolarDebug", "Attempting to store license=$licenseKey")
        if(validateAndStoreLicense(licenseKey, activationKey, source = "initial")) {
            return true;
        }

        if (refreshPolarValidator() && validateAndStoreLicense(licenseKey, activationKey, source = "refreshed")) {
            return true;
        }

        return false;
    }

    private fun validateAndStoreLicense(licenseKey: String, activationKey: String, source: String): Boolean {
        Log.d("PolarDebug", "Validating license ($source) - key=$licenseKey, activationLen=${activationKey.length}")
        val isValid = _validator.validate(licenseKey, activationKey)
        if(isValid) {
            Log.d("PolarDebug", "License validation succeeded ($source)")
            savePaymentKey(licenseKey, activationKey);
            hasPaid = true;
            hasPaidChanged.emit(true);
            return true;
        } else {
            Log.w("PolarDebug", "License validation FAILED ($source)")
            return false;
        }
    }

    fun getAvailableCurrencies(productId: String): List<String> {
        synchronized(_currencyCache) {
            if(_currencyCache.containsKey(productId))
                return _currencyCache[productId]!!;
        }
        val url = URL_CURRENCIES + "?productId=" + productId;
        val result = httpGET(url);
        if(!result.isSuccessful)
            throw IllegalStateException("Could not get currencies [${result.code}]:\n" + result.body);
        if(result.body == null)
            throw IllegalStateException("Could not get currencies:\nEmpty response");

        val listResult = _json.decodeFromString<List<String>>(result.body);
        synchronized(_currencyCache) {
            _currencyCache[productId] = listResult;
            return _currencyCache[productId]!!;
        }
    }
    fun getAvailableCurrencyPrices(productId: String): Map<String, Long> {
        synchronized(_priceCache) {
            if(_priceCache.containsKey(productId))
                return _priceCache[productId]!!;
        }
        val url = URL_PRICES + "?productId=" + productId;
        val result = httpGET(url);
        if(!result.isSuccessful)
            throw IllegalStateException("Could not get currencies [${result.code}]:\n" + result.body);
        if(result.body == null)
            throw IllegalStateException("Could not get currencies:\nEmpty response");

        val listResult = _json.decodeFromString<HashMap<String, Long>>(result.body);
        synchronized(_priceCache) {
            _priceCache[productId] = listResult;
            return _priceCache[productId]!!;
        }
    }
    fun getPaymentCountryFromIP(allowFail: Boolean = false): String? {
        try {
            val urlString = "https://freeipapi.com/api/json"

            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"

            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            val response = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                response.append(line)
            }
            reader.close()
            val json = response.toString();

            val ipInfoObj = JsonParser.parseString(json) as JsonObject;
            if (ipInfoObj.has("countryCode"))
                return ipInfoObj.get("countryCode").asString;
            return null;
        }
        catch(ex: Throwable) {
            if(allowFail)
                return null;
            throw ex;
        }
    }

    fun createPolarCheckoutSession(amountCents: Long, customerEmail: String): PolarCheckoutResponse {
        val orgSlug = polarOrgSlug
        val productSlug = polarProductSlug
        val productId = polarProductId
        val url = "${URL_POLAR_BASE}/checkout/polar/$orgSlug/$productSlug/create-checkout"
        android.util.Log.d("PolarDebug", "Creating checkout session at $url with amount=$amountCents, email=$customerEmail")

        val body = JSONObject().apply {
            put("polar_product_id", productId)
            put("product_id", productId)
            put("amount", amountCents)
            put("customer_email", customerEmail)
            put("allow_discount_codes", true)
            put("require_billing_address", false)
            put("theme", "dark")
            put("success_url", "${URL_POLAR_BASE}/payment-complete?app_session_id={CHECKOUT_ID}")
        }

        val result = httpPOST(url, body.toString())
        android.util.Log.d("PolarDebug", "Create checkout response code: ${result.code}, body: ${result.body}")
        if (!result.isSuccessful) {
            throw IllegalStateException("Polar checkout failed [${result.code}]: ${result.body}")
        }

        val json = JsonParser.parseString(result.body).asJsonObject
        return PolarCheckoutResponse(
            checkoutUrl = json.get("checkout_url").asString,
            clientSecret = json.get("client_secret").asString
        )
    }

    fun fetchLicenseFromCheckoutStatus(statusUrl: String, clientSecret: String): CheckoutLicenseInfo {
        val uri = android.net.Uri.parse(statusUrl)
        val appSessionId = uri.getQueryParameter("app_session_id")
            ?: throw IllegalStateException("No app_session_id in success URL: $statusUrl")

        val apiUrl = "${URL_POLAR_BASE}/api/checkout-status/$appSessionId?client_secret=$clientSecret"
        android.util.Log.d("PolarDebug", "Fetching license status from $apiUrl")
        
        val result = httpGET(apiUrl)
        android.util.Log.d("PolarDebug", "License status response code: ${result.code}, body: ${result.body}")

        if (!result.isSuccessful) {
            throw IllegalStateException("Failed to fetch checkout status [${result.code}]: ${result.body}")
        }
        
        val json = _json.decodeFromString<CreateKeyResponse>(result.body!!)
        
        if (json.status != "granted" && json.status != "valid") {
             throw IllegalStateException("License not granted yet. Status: ${json.status}")
        }

        return CheckoutLicenseInfo(json.license_key, json.activation_key)
    }

    fun getPolarProductPrice(): Long {
        val orgSlug = polarOrgSlug
        val productSlug = polarProductSlug
        val url = "${URL_POLAR_BASE}/checkout/polar/$orgSlug/$productSlug/price"
        Log.d("PolarDebug", "Fetching price from: $url")
        val result = httpGET(url)
        Log.d("PolarDebug", "Price response [${result.code}]: ${result.body}")
        if (!result.isSuccessful) {
            throw IllegalStateException("Failed to get price [${result.code}]: ${result.body}")
        }

        try {
            val json = _json.decodeFromString<ProductPriceResponse>(result.body!!)
            val pricing = json.pricing.firstOrNull() ?: throw IllegalStateException("No pricing found")
            return pricing.amount_cents ?: pricing.preset_amount_cents ?: 0L
        } catch (e: Exception) {
            Log.e("PolarDebug", "Failed to parse price response: ${e.message}\nBody: ${result.body}")
            throw IllegalStateException("Failed to parse price response: ${e.message}\nBody: ${result.body}")
        }
    }

    fun getPolarProductPrice(@Suppress("UNUSED_PARAMETER") productSlug: String): Long {
        return getPolarProductPrice()
    }

    fun createPaymentPortal(productSlug: String, successUrl: String): String {
        val url = "${URL_POLAR_BASE}/api/PaymentPortal/?product=$productSlug&success=${successUrl}"
        return url
    }

    private fun httpPOST(urlStr: String, jsonBody: String): HttpResp {
        val url = URL(urlStr)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true

        val outputStream = connection.outputStream
        val writer = java.io.OutputStreamWriter(outputStream)
        writer.write(jsonBody)
        writer.flush()
        writer.close()

        val stream = if (connection.responseCode < 400) connection.inputStream else connection.errorStream
        val reader = BufferedReader(InputStreamReader(stream ?: connection.inputStream))
        val response = StringBuilder()
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            response.append(line)
        }
        reader.close()
        return HttpResp(connection.responseCode, response.toString())
    }

    private fun httpGET(urlStr: String): HttpResp {
        val url = URL(urlStr);
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"

        val stream = if (connection.responseCode < 400) connection.inputStream else connection.errorStream
        val reader = BufferedReader(InputStreamReader(stream ?: connection.inputStream))
        val response = StringBuilder()
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            response.append(line)
        }
        reader.close()
        return HttpResp(connection.responseCode, response.toString());
    }
    
    private fun refreshPolarValidator(): Boolean {
        return try {
            val orgSlug = polarOrgSlug
            val url = "${URL_POLAR_BASE}/checkout/polar/$orgSlug/activation/public-key"
            val response = httpGET(url)
            if (!response.isSuccessful || response.body.isNullOrBlank()) {
                Log.e("PaymentState", "Failed to fetch Polar public key [${response.code}] ${response.body}")
                return false
            }
            _validator = LicenseValidator(normalizePublicKey(response.body))
            Log.d("PolarDebug", "Refreshed Polar public key from server")
            true
        } catch (ex: Throwable) {
            Log.e("PaymentState", "Unable to refresh Polar public key: " + ex.message)
            false
        }
    }

    private fun normalizePublicKey(raw: String): String {
        return raw
            .replace("-----BEGIN PUBLIC KEY-----", "", ignoreCase = true)
            .replace("-----END PUBLIC KEY-----", "", ignoreCase = true)
            .replace("\\s".toRegex(), "")
            .trim()
    }

    abstract fun savePaymentKey(licenseKey: String, licenseActivation: String);
    abstract fun getPaymentKey(): Pair<String, String>;

    companion object {
        private val _json = Json { ignoreUnknownKeys = true };
    }

    private class HttpResp(
        val code: Int,
        val body: String?
    )
    {
        val isSuccessful get() = code >= 200 && code < 300;
    }
}

@Serializable
data class CheckoutLicenseInfo(
    val licenseKey: String,
    val activationKey: String
)

@Serializable
data class PolarCheckoutResponse(
    val checkoutUrl: String,
    val clientSecret: String
)

@Serializable
data class CreateKeyResponse(
    val license_key: String,
    val activation_key: String,
    val status: String,
    val validate_url: String,
    val status_code: Int = 200
)

@Serializable
data class ProductPriceResponse(
    val product_id: String,
    val product_name: String,
    val product_description: String? = null,
    val organization_id: String,
    val is_recurring: Boolean,
    val recurring_interval: String? = null,
    val metadata: Map<String, String> = emptyMap(),
    val pricing: List<ProductPricing>
)

@Serializable
data class ProductPricing(
    val price_id: String,
    val price_type: String,
    val currency: String? = null,
    val amount_cents: Long? = null,
    val minimum_amount_cents: Long? = null,
    val maximum_amount_cents: Long? = null,
    val preset_amount_cents: Long? = null,
    val is_custom_amount: Boolean = false
)
