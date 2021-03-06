/*
 * Copyright 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.ui.core.input

import android.content.Context
import android.text.Editable
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import androidx.ui.input.EditorState
import androidx.ui.input.TextInputService

/**
 * Provide Android specific input service with the Operating System.
 */
internal class TextInputServiceAndroid(val view: View) : TextInputService {
    /** True if the currently editable widget has connected */
    private var editorHasFocus = false

    /**
     *  The following three observers are set when the editable widget has initiated the input
     *  session
     */
    private var onUpdateEditorState: (EditorState) -> Unit = {}
    private var onEditorActionPerformed: (Any) -> Unit = {}
    private var onKeyEventForwarded: (Any) -> Unit = {}

    /**
     * The editable buffer used for BaseInputConnection.
     */
    private var editable = Editable.Factory.getInstance().newEditable("")
    private val imm =
        view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

    /**
     * Creates new input connection.
     */
    fun createInputConnection(outAttrs: EditorInfo): InputConnection? {
        if (!editorHasFocus) {
            return null
        }
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN

        return CraneInputConnection(
            view=view,
            onUpdateEditorState = onUpdateEditorState,
            onEditorActionPerformed = onEditorActionPerformed,
            onKeyEventForwarded = onKeyEventForwarded,
            mEditable = editable)
    }

    /**
     * Returns true if some editable component is focused.
     */
    fun isEditorFocused(): Boolean = editorHasFocus

    override fun startInput(
        initState: EditorState,
        onUpdateEditorState: (EditorState) -> Unit,
        onEditorActionPerformed: (Any) -> Unit,
        onKeyEventForwarded: (Any) -> Unit
    ) {
        editorHasFocus = true
        this.onUpdateEditorState = onUpdateEditorState
        this.onEditorActionPerformed = onEditorActionPerformed
        this.onKeyEventForwarded = onKeyEventForwarded
        this.editable = initState.toEditable()

        view.requestFocus()
        view.post {
            imm.restartInput(view)
            imm.showSoftInput(view, 0)
        }
    }

    override fun stopInput() {
        editorHasFocus = false
        onUpdateEditorState = {}
        onEditorActionPerformed = {}
        onKeyEventForwarded = {}

        imm.restartInput(view)
    }
}