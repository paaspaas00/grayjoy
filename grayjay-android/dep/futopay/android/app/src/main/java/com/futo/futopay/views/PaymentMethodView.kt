package com.futo.futopay.views

import android.content.Context
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.futo.futopay.PaymentConfigurations
import com.futo.futopay.R

class PaymentMethodView: ConstraintLayout {
    private val _image: ImageView
    private val _name: TextView
    private val _description: TextView

    constructor(context: Context): super(context) {
        inflate(context, R.layout.payment_method, this);
        _image = findViewById(R.id.image_method);
        _name = findViewById(R.id.text_method_name);
        _description = findViewById(R.id.text_method_description);
    }
    constructor(context: Context, paymentMethod: PaymentConfigurations.PaymentMethodDescriptor, onClick: (String)->Unit): super(context) {
        inflate(context, R.layout.payment_method, this);
        _image = findViewById(R.id.image_method);
        _name = findViewById(R.id.text_method_name);
        _description = findViewById(R.id.text_method_description);

        bind(paymentMethod, onClick);
    }

    fun bind(paymentMethod: PaymentConfigurations.PaymentMethodDescriptor, onClick: (String)->Unit) {
        _image.setImageResource(paymentMethod.image);
        _name.text = context.getString(paymentMethod.name);
        _description.text = context.getString(paymentMethod.description);

        if(!paymentMethod.isDisabled) {
            setOnClickListener {
                onClick(paymentMethod.id);
            }
        } else {
            this.alpha = 0.5f;
            setOnClickListener(null);
        }
    }

    fun bind(image: Int, name: String, description: String, id: String, onClick: (String)->Unit) {
        _image.setImageResource(image);
        _name.text = name;
        _description.text = description;

        setOnClickListener {
            onClick(id);
        }
    }
}