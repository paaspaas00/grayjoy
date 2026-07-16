package com.futo.futopay.views

import android.content.Context
import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.futo.futopay.R

class PaymentEmailView : ConstraintLayout {
    private var _isValid = false;

    constructor(context: Context, onSubmit: (String)->Unit): super(context) {
        inflate(context, R.layout.payment_email, this);

        val textSubtext = findViewById<TextView>(R.id.text_subtext);
        val buttonSubmit = findViewById<FrameLayout>(R.id.button_submit);
        val editEmail = findViewById<EditText>(R.id.edit_email);
        
        // Simple email regex
        val regex = android.util.Patterns.EMAIL_ADDRESS

        editEmail.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = Unit
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val text = editEmail.text.toString();

                _isValid = regex.matcher(text).matches();
                if (_isValid) {
                    textSubtext.setTextColor(Color.rgb(0x99, 0x99, 0x99));
                    textSubtext.text = context.getString(R.string.email_required_for_receipt);
                    buttonSubmit.alpha = 1.0f;
                } else {
                    textSubtext.setTextColor(Color.rgb(0xFF, 0x00, 0x00));
                    textSubtext.text = context.getString(R.string.email_is_invalid);
                    buttonSubmit.alpha = 0.4f;
                }
            }
        });

        buttonSubmit.alpha = 0.4f;
        
        buttonSubmit.setOnClickListener {
            if (!_isValid) {
                return@setOnClickListener;
            }
            onSubmit(editEmail.text.toString());
        };

    }
}
