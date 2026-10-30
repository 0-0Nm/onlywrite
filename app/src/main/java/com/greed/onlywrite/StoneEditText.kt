package com.greed.onlywrite

import android.content.Context
import android.text.InputFilter
import android.text.Spanned
import android.util.AttributeSet
import android.view.KeyEvent
import androidx.appcompat.widget.AppCompatEditText

/**
 * 石刻输入框。
 *
 * 职责只有一个：**挡住删除**。
 * 记录追加一律交给 onInserted —— 它由 onTextChanged 触发，
 * 也就是字符"已经进缓冲区之后"才回调。
 *
 * 之前把落库塞在 InputFilter 里是错的：那是提交之前，缓冲区还没有那个字符，
 * 我在那里 append 时间戳，时间戳就跑到换行符前面去了 —— 屏幕上看到的错序就是这么来的。
 */
class StoneEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = android.R.attr.editTextStyle
) : AppCompatEditText(context, attrs, defStyle) {

    var onRejected: ((String) -> Unit)? = null
    var onCaretMoved: (() -> Unit)? = null

    /** 提交后的插入回调：start 为绝对下标，text 为本次插入的内容 */
    var onInserted: ((Int, String) -> Unit)? = null

    /** 由 MainActivity 在程序化改写缓冲区时关闭，避免把自己写的东西当成用户输入 */
    var recording = true

    init {
        filters = arrayOf(NoDeleteFilter())
    }

    override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) {
        super.onTextChanged(text, start, before, count)
        if (!recording) return
        if (before != 0 || count <= 0 || text == null) return
        val ins = text.subSequence(start, start + count).toString()
        onInserted?.invoke(start, ins)
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
            if (dend - dstart > 0) {
                onRejected?.invoke(
                    if (end - start == 0) "刻下的字不能擦。"
                    else "不能用输入覆盖已刻的字。先「划掉」，再刻新的。"
                )
                return ""
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
