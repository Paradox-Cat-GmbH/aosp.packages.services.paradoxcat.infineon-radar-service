package com.paradoxcat.infineon.radar

fun FloatArray.argmax(): Int = indices.maxBy { this[it] }

fun FloatArray.reshapeTo3D(depth: Int, rows: Int, cols: Int): Array<Array<FloatArray>> {
    require(size == depth * rows * cols) { "Data size does not match shape size" }

    return Array(depth) { d ->
        Array(rows) { r ->
            sliceArray((d * rows * cols) + (r * cols) until (d * rows * cols) + (r + 1) * cols)
        }
    }
}