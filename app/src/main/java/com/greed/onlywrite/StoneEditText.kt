package com.greed.onlywrite

import android.content.Context
import android.text.InputFilter
import android.text.Spanned
import android.util.AttributeSet
import android.view.KeyEvent
import androidx.appcompat.widget.AppCompatEditText

/**
 * 石刻输入框：只允许插入。
 * 退格 / 删除 / 剪切 / 撤销 / 选中覆盖 —— 全部在 InputFilter 层被丢弃。
 */
class StoneEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = android.R.attr.editTextStyle
) : AppCompatEditText(context, attrs, defStyle) {

    var onRejected: ((String) -> Unit)? = null
    var onCarve: ((String) -> Unit)? = null
    var onCaretMoved: (() -> Unit)? = null

    init {
        filters = arrayOf(NoDeleteFilter())
        setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom)
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        onCaretMoved?.invoke()
    }

    private inner class NoDeleteFilter : InputFilter {
        override fun filter(
            source: CharSequence, start: Int, end: Int,
            dest: Spanned, dstart: Int, dend: Int
        ): CharSequence? {
            val removed = dend - dstart
            if (removed > 0) {
                onRejected?.invoke(
                    if (end - start == 0) "刻下的字不能擦。"
                    else "不能用输入覆盖已刻的字。先「划掉」，再刻新的。"
                )
                return ""
            }
            if (end > start) {
                onCarve?.invoke(source.subSequence(start, end).toString())
            }
            return null
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DEL || keyCode == KeyEvent.KEYCODE_FORWARD_DEL) {
            onRejected?.invoke("刻下的字不能擦。")
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
