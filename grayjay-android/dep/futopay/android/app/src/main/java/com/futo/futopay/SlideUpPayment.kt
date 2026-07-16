package com.futo.futopay

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.animation.doOnEnd
import androidx.core.view.children
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.futo.futopay.views.PaymentMethodView
import com.futo.futopay.views.PaymentEmailView
import com.futo.futopay.views.PaymentWebView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SlideUpPayment : RelativeLayout {
    private var _container: ViewGroup? = null;
    private lateinit var _textTitle: TextView;
    private lateinit var _textCancel: TextView;
    private lateinit var _textOK: TextView;
    private lateinit var _viewBackground: View;
    private lateinit var _viewScroll: ScrollView;
    private lateinit var _viewContainer: LinearLayout;
    private lateinit var _viewOverlayContainer: LinearLayout;
    private lateinit var _viewRecycler: RecyclerView;
    private lateinit var _viewCustomContainer: FrameLayout;

    private lateinit var _layoutFilter: FrameLayout;
    private lateinit var _editFilter: EditText;
    private lateinit var _clearFilter: ImageButton;
    private var _adapter: ISlideUpGenericViewAdapter? = null;

    private var _animatorSet: AnimatorSet? = null;

    private var _isVisible = false;

    var onOK: (() -> Unit)? = null;
    var onCancel: (() -> Unit)? = null;

    constructor(paymentState: PaymentState, context: Context, parent: ViewGroup, titleText: String, okText: String?, vararg items: View): super(context){
        init(paymentState, okText);
        _container = parent;
        if(!_container!!.children.contains(this)) {
            _container!!.removeAllViews();
            _container!!.addView(this);
        }
        _textTitle.text = titleText;

        if(items.size > 0) {
            setItems(items.toList());
        }

        if(isFlagsInitialized())
            launchIO {
                initFlags(context);
            };
    }

    companion object {
        private var _productId: String? = null;
        private var _paymentMethod: PaymentConfigurations.PaymentMethodDescriptor? = null;
        private var _country: PaymentConfigurations.CountryDescriptor? = null;
        private var _email: String? = null;
        private var _onSelected: ((String, PaymentManager.PaymentRequest)->Unit)? = null;
        private var _currentSlideUpPayment: SlideUpPayment? = null;
        private val _animationDuration = 300L;
        private var _scope: CoroutineScope? = null;

        fun startPayment(paymentState: PaymentState, overlayContainer: ViewGroup, scope: CoroutineScope, productId: String, country: PaymentConfigurations.CountryDescriptor?, onSelected: (String, PaymentManager.PaymentRequest)->Unit) {
            _productId = productId;
            val enabledMethods = PaymentConfigurations.PAYMENT_METHODS.filter { !it.isDisabled }
            _paymentMethod = if (enabledMethods.size == 1) enabledMethods.first() else null;
            _country = country;
            _email = null;
            _onSelected = onSelected;
            _currentSlideUpPayment = null;
            _scope = scope;

            requestNext(paymentState, overlayContainer);
        }

        private fun launchIO(block: suspend CoroutineScope.() -> Unit) {
            val scope = _scope ?: return
            scope.launch(Dispatchers.IO, block = block)
        }

        private fun isComplete(): Boolean {
            if (_paymentMethod == null) {
                return false;
            }

            if (_paymentMethod?.id == "polar" && _email == null) {
                return false;
            }

            return true;
        }

        private fun transitionTo(slideUpPaymentFactory: () -> SlideUpPayment) {
            val currentSlideUpPayment = _currentSlideUpPayment;
            if (currentSlideUpPayment != null) {
                currentSlideUpPayment.hideKeyboard();
                currentSlideUpPayment.transition {
                    val newSlideUpPayment = slideUpPaymentFactory();
                    _currentSlideUpPayment = newSlideUpPayment;
                    return@transition newSlideUpPayment;
                };
            } else {
                val newSlideUpPayment = slideUpPaymentFactory();
                newSlideUpPayment.show();
                _currentSlideUpPayment = newSlideUpPayment;
            }
        }

        private fun hide() {
            _currentSlideUpPayment?.hide();
            _currentSlideUpPayment = null;
        }

        private fun requestNext(paymentState: PaymentState, overlayContainer: ViewGroup) {
            if (_paymentMethod == null) {
                requestPaymentMethod(paymentState, overlayContainer);
                return;
            }

            if (_paymentMethod?.id == "polar") {
                if (_email == null) {
                    requestEmail(paymentState, overlayContainer)
                    return
                }
                startPolarCheckout(paymentState, overlayContainer)
                return
            }
        }

        private fun requestEmail(paymentState: PaymentState, overlayContainer: ViewGroup) {
            transitionTo {
                SlideUpPayment(paymentState, overlayContainer.context, overlayContainer, overlayContainer.context.getString(R.string.enter_email), null,
                    PaymentEmailView(overlayContainer.context) {
                        _email = it;
                        requestNext(paymentState, overlayContainer);
                    }
                )
            }
        }

        private fun startPolarCheckout(paymentState: PaymentState, overlayContainer: ViewGroup) {
            if (_productId == null) {
                return
            }
            val email = _email ?: return

            val successUrlPrefix = "${paymentState.URL_POLAR_BASE}/payment-complete"
            var clientSecret: String? = null
            var pendingSuccessUrl: String? = null

            val polarWebView = PaymentWebView(overlayContainer.context, successUrlPrefix) { successUrl ->
                val readySecret = clientSecret
                if (readySecret != null) {
                    handlePolarSuccess(paymentState, overlayContainer, successUrl, readySecret)
                } else {
                    pendingSuccessUrl = successUrl
                }
            }

            transitionTo {
                SlideUpPayment(paymentState, overlayContainer.context, overlayContainer, overlayContainer.context.getString(R.string.checkout), null).apply {
                    setCustomView(polarWebView)
                }
            }

            launchIO {
                try {
                    val amountCents = paymentState.getPolarProductPrice()
                    val response = paymentState.createPolarCheckoutSession(amountCents, email)

                    withContext(Dispatchers.Main) {
                        clientSecret = response.clientSecret
                        polarWebView.loadCheckoutUrl(response.checkoutUrl)

                        val successUrl = pendingSuccessUrl
                        val readySecret = clientSecret
                        if (successUrl != null && readySecret != null) {
                            pendingSuccessUrl = null
                            handlePolarSuccess(paymentState, overlayContainer, successUrl, readySecret)
                        }
                    }
                } catch (ex: Throwable) {
                    Log.e("SlideUpPayment", "Polar error", ex)
                    withContext(Dispatchers.Main) {
                        val errorView = TextView(overlayContainer.context).apply {
                            text = "Error: ${ex.message}"
                            setTextColor(android.graphics.Color.WHITE)
                            setPadding(20, 20, 20, 20)
                        }
                        transitionTo {
                            SlideUpPayment(paymentState, overlayContainer.context, overlayContainer, "Error", "OK", errorView)
                        }
                    }
                }
            }
        }

        private fun handlePolarSuccess(paymentState: PaymentState, overlayContainer: ViewGroup, successUrl: String, clientSecret: String) {
            transitionTo {
                val loader = FrameLayout(overlayContainer.context).apply {
                    addView(android.widget.ProgressBar(context).apply {
                        layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, android.view.Gravity.CENTER)
                    })
                }
                SlideUpPayment(paymentState, overlayContainer.context, overlayContainer, overlayContainer.context.getString(R.string.processing_payment), null, loader)
            }

            launchIO {
                try {
                    // Poll until granted or timeout
                    var attempts = 0
                    var info: CheckoutLicenseInfo? = null
                    
                    while (attempts < 30) { // 30 attempts * 2 sec = 60 sec timeout
                        try {
                            android.util.Log.d("PolarDebug", "Polling attempt $attempts")
                            info = paymentState.fetchLicenseFromCheckoutStatus(successUrl, clientSecret)
                            break
                        } catch (e: IllegalStateException) {
                            val msg = e.message ?: ""
                            val isRetryable = msg.contains("License not granted yet") ||
                                    msg.contains("[404]") ||
                                    msg.contains("No benefits found") ||
                                    msg.contains("No benefit grant found")
                            if (isRetryable) {
                                android.util.Log.d("PolarDebug", "Retryable error, waiting... ($msg)")
                                delay(2000)
                                attempts++
                            } else {
                                android.util.Log.e("PolarDebug", "Non-retryable error during polling", e)
                                throw e
                            }
                        }
                    }

                    if (info != null) {
                        val success = paymentState.setPaymentLicense(info.licenseKey, info.activationKey)
                        withContext(Dispatchers.Main) {
                            if (success) {
                                hide()
                                _onSelected?.invoke("polar", PaymentManager.PaymentRequest(_productId ?: "", "USD", _email ?: "", "US", null))
                            } else {
                                // If setPaymentLicense returns false, it might be validation failure.
                                val errorView = TextView(overlayContainer.context).apply {
                                    text = "Verification failed: Invalid license key."
                                    setTextColor(android.graphics.Color.WHITE)
                                    setPadding(20, 20, 20, 20)
                                }
                                transitionTo {
                                    SlideUpPayment(paymentState, overlayContainer.context, overlayContainer, "Error", "OK", errorView)
                                }
                            }
                        }
                    } else {
                        throw Exception("Payment timed out or license not granted.")
                    }

                } catch (ex: Throwable) {
                    Log.e("SlideUpPayment", "Polar verification error", ex)
                    withContext(Dispatchers.Main) {
                        val errorView = TextView(overlayContainer.context).apply {
                            text = "Verification failed: ${ex.message}"
                            setTextColor(android.graphics.Color.WHITE)
                            setPadding(20, 20, 20, 20)
                        }
                        transitionTo {
                            SlideUpPayment(paymentState, overlayContainer.context, overlayContainer, "Error", "OK", errorView)
                        }
                    }
                }
            }
        }

        private fun requestPaymentMethod(paymentState: PaymentState, overlayContainer: ViewGroup) {
            transitionTo {
                SlideUpPayment(paymentState, overlayContainer.context, overlayContainer, overlayContainer.context.getString(R.string.payment_using), null,
                    *PaymentConfigurations.PAYMENT_METHODS.map { method ->
                        PaymentMethodView(overlayContainer.context, method) {
                            _paymentMethod = method;
                            requestNext(paymentState, overlayContainer);
                        }
                    }.toTypedArray()
                )
            }
        }

    }

    fun <T, V : View> setItems(items: List<T>, createView: (ViewGroup) -> V, bindView: (V, T) -> Unit, filterItems: ((List<T>, String) -> List<T>)? = null) {
        _viewRecycler.visibility = View.VISIBLE;
        _viewScroll.visibility = View.GONE;
        _viewCustomContainer.visibility = View.GONE;
        _viewContainer.removeAllViews();

        val adapter = SlideUpGenericViewAdapter(items, createView, bindView, filterItems);
        _viewRecycler.adapter = adapter;

        _adapter = null;
        if (filterItems != null) {
            _editFilter.text.clear();
            _layoutFilter.visibility = View.VISIBLE;
        } else {
            _layoutFilter.visibility = View.GONE;
        }
        _adapter = adapter;
    }

    fun setItems(vararg items: View) {
        setItems(items.toList())
    }

    fun setItems(items: List<View>) {
        _viewRecycler.adapter = null;
        _viewRecycler.visibility = View.GONE;
        _viewScroll.visibility = View.VISIBLE;
        _viewCustomContainer.visibility = View.GONE;
        _viewContainer.removeAllViews();
        _layoutFilter.visibility = View.GONE;
        _adapter = null;

        for (item in items) {
            _viewContainer.addView(item);
        }
    }

    fun setCustomView(view: View) {
        _viewRecycler.adapter = null;
        _viewRecycler.visibility = View.GONE;
        _viewScroll.visibility = View.GONE;
        _viewCustomContainer.visibility = View.VISIBLE;
        _viewContainer.removeAllViews();
        _viewCustomContainer.removeAllViews();
        _layoutFilter.visibility = View.GONE;
        _adapter = null;

        _viewCustomContainer.addView(view);
    }

    private fun hideKeyboard() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager;
        imm?.hideSoftInputFromWindow(windowToken, 0);
    }

    private fun init(paymentState: PaymentState, okText: String?){
        LayoutInflater.from(context).inflate(R.layout.payment_overlay, this, true);

        _textTitle = findViewById(R.id.overlay_slide_up_menu_title);
        _viewScroll = findViewById(R.id.overlay_slide_up_scroll);
        _viewContainer = findViewById(R.id.overlay_slide_up_menu_items);
        _viewRecycler = findViewById(R.id.overlay_recycler_menu_items);
        _viewCustomContainer = findViewById(R.id.overlay_custom_container);
        _textCancel = findViewById(R.id.overlay_slide_up_menu_cancel);
        _textOK = findViewById(R.id.overlay_slide_up_menu_ok);

        _layoutFilter = findViewById(R.id.overlay_slide_up_filter_layout);
        _editFilter = findViewById(R.id.overlay_slide_up_filter_edit);
        _clearFilter = findViewById(R.id.overlay_slide_up_filter_clear);

        setOk(okText);

        _viewBackground = findViewById(R.id.overlay_slide_up_menu_background);
        _viewOverlayContainer = findViewById(R.id.overlay_slide_up_menu_ovelay_container);

        _viewBackground.setOnClickListener {
            onCancel?.invoke();
            SlideUpPayment.hide();
        };

        _textCancel.setOnClickListener {
            onCancel?.invoke();
            SlideUpPayment.hide();
        };

        _viewRecycler.layoutManager = LinearLayoutManager(context);

        _editFilter.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = Unit
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val text = _editFilter.text.toString();
                if (text.isBlank())
                    _clearFilter.visibility = EditText.INVISIBLE;
                else
                    _clearFilter.visibility = EditText.VISIBLE;

                _adapter?.setFilter(text);
            }
        });

        _clearFilter.setOnClickListener {
            _editFilter.text.clear();
            _clearFilter.visibility = View.INVISIBLE;
        };
    }

    fun setOk(textOk: String?) {
        if (textOk == null)
            _textOK.visibility = View.GONE;
        else {
            _textOK.text = textOk;
            _textOK.setOnClickListener {
                onOK?.invoke();
            };
            _textOK.visibility = View.VISIBLE;
        }
    }

    fun show(animate: Boolean = true){
        _animatorSet?.cancel();

        _isVisible = true;
        _container?.post {
            _container?.visibility = View.VISIBLE;
            _container?.bringToFront();
        }

        if (animate) {
            _viewOverlayContainer.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
            _viewOverlayContainer.translationY = _viewOverlayContainer.measuredHeight.toFloat()
            _viewBackground.visibility = View.VISIBLE;

            val animations = arrayListOf<Animator>();
            animations.add(ObjectAnimator.ofFloat(_viewBackground, "alpha", 0.0f, 1.0f).setDuration(_animationDuration));
            animations.add(ObjectAnimator.ofFloat(_viewOverlayContainer, "translationY", _viewOverlayContainer.measuredHeight.toFloat(), 0.0f).setDuration(_animationDuration));

            val animatorSet = AnimatorSet();
            animatorSet.playTogether(animations);
            animatorSet.start();
        } else {
            _viewBackground.visibility = View.VISIBLE;
            _viewBackground.alpha = 1.0f;
            _viewOverlayContainer.translationY = 0.0f;
        }
    }

    fun slideIn(animate: Boolean = true){
        _animatorSet?.cancel();

        _isVisible = true;
        _container?.post {
            _container?.visibility = View.VISIBLE;
            _container?.bringToFront();
        }

        if (animate) {
            _viewOverlayContainer.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
            _viewOverlayContainer.translationY = _viewOverlayContainer.measuredHeight.toFloat();

            val animations = arrayListOf<Animator>();
            animations.add(ObjectAnimator.ofFloat(_viewOverlayContainer, "translationY", _viewOverlayContainer.measuredHeight.toFloat(), 0.0f).setDuration(_animationDuration));

            val animatorSet = AnimatorSet();
            animatorSet.playTogether(animations);
            animatorSet.start();
        } else {
            _viewOverlayContainer.translationY = 0.0f;
        }
    }

    fun transition(animate: Boolean = true, slideUpPaymentFactory: () -> SlideUpPayment) {
        _animatorSet?.cancel();

        _isVisible = false;
        if (animate) {
            val animations = arrayListOf<Animator>();
            animations.add(ObjectAnimator.ofFloat(_viewOverlayContainer, "translationY", 0.0f, _viewOverlayContainer.measuredHeight.toFloat()).setDuration(_animationDuration));

            val animatorSet = AnimatorSet();
            animatorSet.doOnEnd {
                slideUpPaymentFactory().slideIn(true);
            };

            animatorSet.playTogether(animations);
            animatorSet.start();
        } else {
            _viewOverlayContainer.translationY = _viewOverlayContainer.measuredHeight.toFloat();
            slideUpPaymentFactory().show(false);
        }
    }

    fun hide(animate: Boolean = true, after: (()->Unit)? = null){
        hideKeyboard();

        _animatorSet?.cancel();

        _isVisible = false;
        if (animate) {
            val animations = arrayListOf<Animator>();
            animations.add(ObjectAnimator.ofFloat(_viewBackground, "alpha", 1.0f, 0.0f).setDuration(_animationDuration));
            animations.add(ObjectAnimator.ofFloat(_viewOverlayContainer, "translationY", 0.0f, _viewOverlayContainer.measuredHeight.toFloat()).setDuration(_animationDuration));

            val animatorSet = AnimatorSet();
            animatorSet.doOnEnd {
                _container?.post {
                    _container?.visibility = View.GONE;
                    _viewBackground.visibility = View.GONE;
                }
                after?.invoke();
            };

            animatorSet.playTogether(animations);
            animatorSet.start();
        } else {
            _viewBackground.alpha = 0.0f;
            _viewBackground.visibility = View.GONE;
            _container?.visibility = View.GONE;
            _viewOverlayContainer.translationY = _viewOverlayContainer.measuredHeight.toFloat();
            after?.invoke();
        }
    }
}

interface ISlideUpGenericViewAdapter {
    fun setFilter(text: String?);
}

class SlideUpGenericViewAdapter<V : View, T>(val items: List<T>, val createView: (ViewGroup) -> V, val bindView: (V, T) -> Unit, val filterItems: ((List<T>, String) -> List<T>)? = null) : RecyclerView.Adapter<SlideUpGenericViewHolder<V, T>>(), ISlideUpGenericViewAdapter {
    private var filteredItems: List<T>;

    init {
        filteredItems = items;
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SlideUpGenericViewHolder<V, T> {
        val view = createView(parent)
        view.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        return SlideUpGenericViewHolder(view, bindView)
    }

    override fun onBindViewHolder(holder: SlideUpGenericViewHolder<V, T>, position: Int) {
        holder.bind(filteredItems[position])
    }

    override fun getItemCount(): Int {
        return filteredItems.size
    }

    override fun setFilter(text: String?) {
        val f = filterItems;
        filteredItems = if (text.isNullOrBlank() || f == null) {
            items;
        } else {
            f(items, text);
        }

        notifyDataSetChanged();
    }
}

class SlideUpGenericViewHolder<V : View, T>(
    private val view: V,
    private val bindView: (V, T) -> Unit
) : RecyclerView.ViewHolder(view) {

    fun bind(item: T) {
        bindView(view, item)
    }
}
