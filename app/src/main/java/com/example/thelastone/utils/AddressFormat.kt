// app/src/main/java/.../utils/AddressFormat.kt
package com.example.thelastone.utils

/** 去掉開頭/結尾的 3~6 位數郵遞區號（也處理 123-4567 這種） */
fun stripPostalCodeIfAny(addr: String): String = addr.trim()
    .replace(Regex("^\\s*\\d{3,6}(?:-\\d{3,4})?\\s*"), "")
    .replace(Regex("\\s*\\d{3,6}(?:-\\d{3,4})?\\s*$"), "")
    .trim()

/** 去掉「台灣 / 臺灣 / 台灣省」等國別前綴 */
fun stripCountryTaiwanPrefix(addr: String): String =
    addr.replace(Regex("^\\s*(台灣|臺灣)(省)?\\s*"), "").trim()
