package com.example.shadowplayer.service

import android.content.Context
import android.os.Bundle
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import com.example.shadowplayer.player.SentencePlayer
import com.google.common.collect.ImmutableList

@UnstableApi
class ShadowMediaNotificationProvider(
    context: Context,
    private val sentencePlayer: SentencePlayer
) : DefaultMediaNotificationProvider(context) {

    override fun getNotificationContentText(metadata: MediaMetadata): CharSequence? {
        return sentencePlayer.state.value.currentSentence?.text ?: metadata.artist
    }

    override fun getMediaButtons(
        session: MediaSession,
        playerCommands: Player.Commands,
        mediaButtonPreferences: ImmutableList<CommandButton>,
        showPauseButton: Boolean
    ): ImmutableList<CommandButton> {
        val buttons = ImmutableList.builder<CommandButton>()
        buttons.add(
            customAudioButton(
                icon = CommandButton.ICON_PREVIOUS,
                action = PlaybackNotificationActions.ACTION_PREVIOUS_AUDIO,
                label = "上一条音频",
                compactIndex = 0,
                enabled = sentencePlayer.canPlayPrevious()
            )
        )
        if (playerCommands.contains(Player.COMMAND_PLAY_PAUSE)) {
            buttons.add(
                CommandButton.Builder(
                    if (showPauseButton) CommandButton.ICON_PAUSE else CommandButton.ICON_PLAY
                )
                    .setPlayerCommand(Player.COMMAND_PLAY_PAUSE)
                    .setDisplayName(if (showPauseButton) "暂停" else "播放")
                    .setExtras(compactExtras(1))
                    .build()
            )
        }
        buttons.add(
            customAudioButton(
                icon = CommandButton.ICON_NEXT,
                action = PlaybackNotificationActions.ACTION_NEXT_AUDIO,
                label = "下一条音频",
                compactIndex = 2,
                enabled = sentencePlayer.canPlayNext()
            )
        )
        return buttons.build()
    }

    private fun customAudioButton(
        icon: Int,
        action: String,
        label: String,
        compactIndex: Int,
        enabled: Boolean
    ): CommandButton {
        return CommandButton.Builder(icon)
            .setSessionCommand(SessionCommand(action, Bundle.EMPTY))
            .setDisplayName(label)
            .setExtras(compactExtras(compactIndex))
            .setEnabled(enabled)
            .build()
    }

    private fun compactExtras(index: Int): Bundle {
        return Bundle().apply {
            putInt(COMMAND_KEY_COMPACT_VIEW_INDEX, index)
        }
    }
}
