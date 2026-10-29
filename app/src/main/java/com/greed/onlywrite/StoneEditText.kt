package com.greed.onlywrite

import android.content.Context
import android.text.InputFilter
import android.text.Spanned
import android.util.AttributeSet
import android.view.KeyEvent
import androidx.appcompat.widget.AppCompatEditText

/**
 * 石刻输入框。
 * 只允许"插入"。任何让已有文字消失的动作（退格、删除、剪切、撤销、覆盖替换）
 * 都在 InputFilter 层被丢弃 —— 不是弹提示，是根本不进缓冲区。
 */
class StoneEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = android.R.attr.editTextStyle
) : AppCompatEditText(context, attrs, defStyle) {

    /** 由 MainActivity 提供：返回 true 表示这次变更已被执行业务处理，输入框照常接受 */
    var onCarve: ((String) -> Unit)? = null
    var onRejected: ((String) -> Unit)? = null

    init {
        filters = arrayOf(NoDeleteFilter())
    }

    private inner class NoDeleteFilter : InputFilter {
        override fun filter(
            source: CharSequence, start: Int, end: Int,
            dest: Spanned, dstart: Int, dend: Int
        ): CharSequence? {
            val removed = dend - dstart
            val added = end - start
            if (removed > 0) {
                // 删除 / 剪切 / 覆盖替换 —— 一律不许
                if (added == 0) onRejected?.invoke("刻下的字不能擦。")
                else onRejected?.invoke("不能用输入覆盖已刻的字。先「划掉」，再刻新的。")
                return ""
            }
            if (added > 0) onCarve?.invoke(source.subSequence(start, end).toString())
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
