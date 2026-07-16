package com.futo.futopay

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat

class UIDialogs {
    data class Action(
        val text: String,
        val action: () -> Unit,
        val style: ActionStyle = ActionStyle.NONE
    );

    enum class ActionStyle {
        NONE,
        PRIMARY,
        ACCENT,
        DANGEROUS,
        DANGEROUS_TEXT
    }

    companion object {

        fun showDialog(context: Context, icon: Int, text: String, textDetails: String? = null, defaultCloseAction: Int, vararg actions: Action) {
            val builder = AlertDialog.Builder(context);
            val view = LayoutInflater.from(context).inflate(R.layout.dialog_multi_button, null);
            builder.setView(view);

            val dialog = builder.create();
            view.findViewById<ImageView>(R.id.dialog_icon).apply {
                this.setImageResource(icon);
            }
            view.findViewById<TextView>(R.id.dialog_text).apply {
                this.text = text;
            };
            view.findViewById<TextView>(R.id.dialog_text_details).apply {
                if(textDetails == null)
                    this.visibility = View.GONE;
                else
                    this.text = textDetails;
            };
            view.findViewById<LinearLayout>(R.id.dialog_buttons).apply {
                val buttons = actions.map<Action, TextView> { act ->
                    val buttonView = TextView(context);
                    val dp10 = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10f, resources.displayMetrics).toInt();
                    val dp28 = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 28f, resources.displayMetrics).toInt();
                    buttonView.layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        if(actions.size > 1)
                            this.marginEnd = dp28;
                    };
                    buttonView.setTextColor(Color.WHITE);
                    buttonView.textSize = 14f;
                    buttonView.typeface = resources.getFont(R.font.inter_regular);
                    buttonView.text = act.text;
                    buttonView.setOnClickListener { act.action(); dialog.dismiss(); };
                    when(act.style) {
                        ActionStyle.PRIMARY -> buttonView.setBackgroundResource(R.drawable.background_button_primary);
                        ActionStyle.ACCENT -> buttonView.setBackgroundResource(R.drawable.background_button_accent);
                        ActionStyle.DANGEROUS -> buttonView.setBackgroundResource(R.drawable.background_button_pred);
                        ActionStyle.DANGEROUS_TEXT -> buttonView.setTextColor(ContextCompat.getColor(context, R.color.pastel_red))
                        else -> buttonView.setTextColor(ContextCompat.getColor(context, R.color.primary))
                    }
                    if(act.style != ActionStyle.NONE && act.style != ActionStyle.DANGEROUS_TEXT)
                        buttonView.setPadding(dp28, dp10, dp28, dp10);
                    else
                        buttonView.setPadding(dp10, dp10, dp10, dp10);

                    return@map buttonView;
                };
                if(actions.size <= 1)
                    this.gravity = Gravity.CENTER;
                else
                    this.gravity = Gravity.END;
                for(button in buttons)
                    this.addView(button);
            };
            dialog.setOnCancelListener {
                if(defaultCloseAction >= 0 && defaultCloseAction < actions.size)
                    actions[defaultCloseAction].action();
            }
            dialog.show();
        }

        fun showGeneralErrorDialog(context: Context, msg: String, ex: Throwable? = null, button: String = "Ok", onOk: (()->Unit)? = null) {
            showDialog(context,
                R.drawable.ic_error_pred,
                msg, ex?.let { "${it.message}" } ?: "",
                0,
                UIDialogs.Action(button, {
                    onOk?.invoke();
                }, UIDialogs.ActionStyle.PRIMARY)
            );
        }
    }
}