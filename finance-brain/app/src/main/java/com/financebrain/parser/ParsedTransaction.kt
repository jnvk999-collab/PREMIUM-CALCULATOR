package com.financebrain.parser

import com.financebrain.data.Direction

data class ParsedTransaction(
    val amountPaise: Long,
    val direction: Direction,
    val bank: String,
    val accountTail: String?,
    val counterparty: String,
    val channel: String,
    val reference: String?,
    val balancePaise: Long?,
    val timestamp: Long,
    val accountKind: String = "BANK",
)
