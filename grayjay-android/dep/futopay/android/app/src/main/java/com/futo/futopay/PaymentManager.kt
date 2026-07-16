package com.futo.futopay

import android.util.Log
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class PaymentManager {
    private val _fragment: Fragment;
    private val _overlayContainer: ViewGroup;
    private val _paymentState: PaymentState;
    private val _onCompleted: (Boolean, String?, Throwable?) -> Unit;

    constructor(paymentState: PaymentState, fragment: Fragment, overlayContainer: ViewGroup, onCompleted: (success: Boolean, purchaseId: String?, exception: Throwable?)->Unit) {
        _fragment = fragment;
        _paymentState = paymentState;
        _overlayContainer = overlayContainer;
        _onCompleted = onCompleted;
    }


    fun startPayment(paymentState: PaymentState, scope: CoroutineScope, productId: String) {
        scope.launch(Dispatchers.IO){
            try{
                val country = paymentState.getPaymentCountryFromIP(true)?.let { c -> PaymentConfigurations.COUNTRIES.find { it.id.equals(c, ignoreCase = true) } };
                withContext(Dispatchers.Main) {
                    SlideUpPayment.startPayment(paymentState, _overlayContainer, scope, productId, country) { method, _ ->
                        when(method) {
                            "polar" -> _onCompleted(true, null, null);
                        }
                    };
                }
            }
            catch(ex: Throwable) {
                Log.e(TAG, "startPayment failed", ex);
                scope.launch(Dispatchers.Main){
                    UIDialogs.showGeneralErrorDialog(_fragment.requireContext(), _overlayContainer.context.getString(R.string.failed_to_get_required_payment_data), ex);
                }
            }
        }
    }

    data class PaymentRequest(
        val productId: String,
        val currency: String,
        val mail: String,
        val country: String? = null,
        val zipcode: String? = null
    );

    companion object {
        private const val TAG = "PaymentManager"
    }
}
