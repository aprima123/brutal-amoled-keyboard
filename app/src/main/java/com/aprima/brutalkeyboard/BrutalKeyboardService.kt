package com.aprima.brutalkeyboard

import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button

class BrutalKeyboardService : InputMethodService() {

    private var isShifted = false
    private var isCapsLock = false
    private var isSymbols = false
    private var lastShiftClickTime = 0L

    private lateinit var letterButtons: List<Button>
    private lateinit var shiftButton: Button
    private lateinit var symButton: Button

    private val letterIds = listOf(
        R.id.key_q, R.id.key_w, R.id.key_e, R.id.key_r, R.id.key_t,
        R.id.key_y, R.id.key_u, R.id.key_i, R.id.key_o, R.id.key_p,
        R.id.key_a, R.id.key_s, R.id.key_d, R.id.key_f, R.id.key_g,
        R.id.key_h, R.id.key_j, R.id.key_k, R.id.key_l,
        R.id.key_z, R.id.key_x, R.id.key_c, R.id.key_v, R.id.key_b,
        R.id.key_n, R.id.key_m
    )

    private val normalChars = listOf(
        "q", "w", "e", "r", "t", "y", "u", "i", "o", "p",
        "a", "s", "d", "f", "g", "h", "j", "k", "l",
        "z", "x", "c", "v", "b", "n", "m"
    )

    private val symbolChars = listOf(
        "@", "#", "$", "_", "&", "-", "+", "(", ")", "/",
        "*", "\"", "'", ":", ";", "!", "?", "~", "`",
        "%", "=", "\\", "[", "]", "<", ">"
    )

    private val numberIds = listOf(
        R.id.key_1, R.id.key_2, R.id.key_3, R.id.key_4, R.id.key_5,
        R.id.key_6, R.id.key_7, R.id.key_8, R.id.key_9, R.id.key_0
    )

    private val deleteHandler = Handler(Looper.getMainLooper())
    private val deleteRunnable = object : Runnable {
        override fun run() {
            deleteOneChar()
            deleteHandler.postDelayed(this, 60)
        }
    }

    override fun onCreateInputView(): View {
        val rootView = layoutInflater.inflate(R.layout.keyboard_view, null)

        letterButtons = letterIds.map { rootView.findViewById(it) }
        shiftButton = rootView.findViewById(R.id.key_shift)
        symButton = rootView.findViewById(R.id.key_sym)

        // Setup tombol huruf / simbol
        letterButtons.forEachIndexed { index, button ->
            button.setOnClickListener {
                vibrateKey(it)
                val text = button.text.toString()
                currentInputConnection?.commitText(text, 1)

                if (isShifted && !isCapsLock && !isSymbols) {
                    isShifted = false
                    updateKeyLabels()
                }
            }
        }

        // Setup baris angka
        numberIds.forEach { id ->
            val btn = rootView.findViewById<Button>(id)
            btn.setOnClickListener {
                vibrateKey(it)
                currentInputConnection?.commitText(btn.text.toString(), 1)
            }
        }

        // Tombol Shift & Caps Lock
        shiftButton.setOnClickListener {
            vibrateKey(it)
            if (isSymbols) return@setOnClickListener

            val now = System.currentTimeMillis()
            if (now - lastShiftClickTime < 350) {
                isCapsLock = !isCapsLock
                isShifted = isCapsLock
            } else {
                if (isCapsLock) {
                    isCapsLock = false
                    isShifted = false
                } else {
                    isShifted = !isShifted
                }
            }
            lastShiftClickTime = now
            updateKeyLabels()
        }

        // Tombol Simbol (?123 / ABC)
        symButton.setOnClickListener {
            vibrateKey(it)
            isSymbols = !isSymbols
            isShifted = false
            isCapsLock = false
            updateKeyLabels()
        }

        // Tombol Spasi, Koma, Titik
        rootView.findViewById<Button>(R.id.key_space).setOnClickListener {
            vibrateKey(it)
            currentInputConnection?.commitText(" ", 1)
        }
        rootView.findViewById<Button>(R.id.key_comma).setOnClickListener {
            vibrateKey(it)
            currentInputConnection?.commitText(",", 1)
        }
        rootView.findViewById<Button>(R.id.key_dot).setOnClickListener {
            vibrateKey(it)
            currentInputConnection?.commitText(".", 1)
        }

        // Tombol Backspace (tap sekali & tahan untuk hapus cepat)
        val delButton = rootView.findViewById<Button>(R.id.key_del)
        delButton.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    vibrateKey(view)
                    deleteOneChar()
                    deleteHandler.postDelayed(deleteRunnable, 400)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    deleteHandler.removeCallbacks(deleteRunnable)
                    true
                }
                else -> false
            }
        }

        // Tombol Enter / Search / Send
        rootView.findViewById<Button>(R.id.key_enter).setOnClickListener {
            vibrateKey(it)
            val ic = currentInputConnection ?: return@setOnClickListener
            val action = currentInputEditorInfo?.imeOptions ?: EditorInfo.IME_ACTION_NONE
            if ((action and EditorInfo.IME_MASK_ACTION) != EditorInfo.IME_ACTION_NONE) {
                ic.performEditorAction(action and EditorInfo.IME_MASK_ACTION)
            } else {
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
            }
        }

        updateKeyLabels()
        return rootView
    }

    private fun updateKeyLabels() {
        if (isSymbols) {
            symButton.text = "ABC"
            shiftButton.text = "-"
            letterButtons.forEachIndexed { index, button ->
                button.text = symbolChars[index]
            }
        } else {
            symButton.text = "?123"
            shiftButton.text = when {
                isCapsLock -> "⇪"
                isShifted -> "⬆"
                else -> "⇧"
            }
            letterButtons.forEachIndexed { index, button ->
                val char = normalChars[index]
                button.text = if (isShifted || isCapsLock) char.uppercase() else char
            }
        }
    }

    private fun deleteOneChar() {
        val ic = currentInputConnection ?: return
        val selectedText = ic.getSelectedText(0)
        if (selectedText.isNullOrEmpty()) {
            ic.deleteSurroundingText(1, 0)
        } else {
            ic.commitText("", 1)
        }
    }

    private fun vibrateKey(view: View) {
        view.performHapticFeedback(
            HapticFeedbackConstants.KEYBOARD_TAP,
            HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
        )
    }
}
