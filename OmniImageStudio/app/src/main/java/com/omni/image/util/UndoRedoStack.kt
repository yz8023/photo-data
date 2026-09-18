package com.omni.image.util

import android.graphics.Bitmap
import java.util.ArrayDeque

class UndoRedoStack(private val maxSize: Int = 10) {
    private val undo = ArrayDeque<Bitmap>()
    private val redo = ArrayDeque<Bitmap>()

    fun push(state: Bitmap) {
        if (undo.size >= maxSize) undo.removeLast()
        undo.addFirst(state)
        redo.clear()
    }

    fun canUndo() = undo.isNotEmpty()
    fun canRedo() = redo.isNotEmpty()

    fun undo(current: Bitmap): Bitmap {
        if (undo.isEmpty()) return current
        redo.addFirst(current)
        return undo.removeFirst()
    }

    fun redo(current: Bitmap): Bitmap {
        if (redo.isEmpty()) return current
        undo.addFirst(current)
        return redo.removeFirst()
    }

    fun clear() {
        undo.clear()
        redo.clear()
    }
}
