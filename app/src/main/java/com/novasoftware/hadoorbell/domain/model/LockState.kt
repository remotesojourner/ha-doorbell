package com.novasoftware.hadoorbell.domain.model

enum class LockState {
    Locked,
    Unlocked,
    Locking,
    Unlocking,
    Jammed,
    Unknown;

    companion object {
        fun fromString(state: String?): LockState {
            if (state == null) return Unknown
            return when (state.lowercase()) {
                "locked" -> Locked
                "unlocked", "open" -> Unlocked
                "locking" -> Locking
                "unlocking", "opening" -> Unlocking
                "jammed" -> Jammed
                else -> Unknown
            }
        }
    }
}
