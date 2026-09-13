package com.tencent.kuiklybase.table.model

import com.tencent.kuiklybase.table.theme.TableTheme

/** Immutable data exposed to the empty-content slot. */
data class TableEmptyContext(
    val text: String,
    val theme: TableTheme,
)
