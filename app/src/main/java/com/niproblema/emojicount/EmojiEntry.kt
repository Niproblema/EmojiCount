package com.niproblema.emojicount

import androidx.emoji2.text.EmojiSpan

class EmojiEntry (emoji: EmojiSpan, emojiText: CharSequence ){
    val Emoji : EmojiSpan = emoji
    val EmojiText : CharSequence = emojiText
    var CountOutgoing : Int = 0
    var CountIngoing : Int = 0
}