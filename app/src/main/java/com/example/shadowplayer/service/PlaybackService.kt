package com.example.shadowplayer.service

import android.content.Intent
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.shadowplayer.player.AudioPlayer
import com.example.shadowplayer.player.LearningSessionPlayer
import com.example.shadowplayer.player.MediaCommandDispatcher
import com.example.shadowplayer.player.SentencePlayer
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@androidx.annotation.OptIn(UnstableApi::class)
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject lateinit var audioPlayer: AudioPlayer
    @Inject lateinit var sentencePlayer: SentencePlayer

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        val commandDispatcher = MediaCommandDispatcher(
            playAction = sentencePlayer::play,
            pauseAction = sentencePlayer::pause,
            seekAction = sentencePlayer::seekTo,
            previousAction = sentencePlayer::previousSentenceOrSeek,
            nextAction = sentencePlayer::nextSentenceOrSeek,
            seekBackwardAction = sentencePlayer::seekBackward,
            seekForwardAction = sentencePlayer::seekForward
        )
        val sessionPlayer = LearningSessionPlayer(audioPlayer.player, commandDispatcher)

        val previousSentenceButton = CommandButton.Builder(CommandButton.ICON_PREVIOUS)
            .setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS)
            .setDisplayName("上一句")
            .build()
        val nextSentenceButton = CommandButton.Builder(CommandButton.ICON_NEXT)
            .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT)
            .setDisplayName("下一句")
            .build()

        mediaSession = MediaSession.Builder(this, sessionPlayer)
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): MediaSession.ConnectionResult {
                    if (controller.packageName != packageName && !controller.isTrusted) {
                        return MediaSession.ConnectionResult.reject()
                    }
                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .build()
                }
            })
            .setMediaButtonPreferences(
                listOf(previousSentenceButton, nextSentenceButton)
            )
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onUpdateNotification(
        session: MediaSession,
        startInForegroundRequired: Boolean
    ) {
        // Sentence-repeat intervals pause ExoPlayer, but the learning session is still active.
        super.onUpdateNotification(
            session,
            startInForegroundRequired || sentencePlayer.isPlaybackActive()
        )
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        sentencePlayer.syncFromPlayer()
        val hasMedia = sentencePlayer.hasLoadedMedia()
        val shouldKeepService = hasMedia && (isPlaybackOngoing() || sentencePlayer.isPlaybackActive())
        if (!shouldKeepService) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        sentencePlayer.syncFromPlayer()
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }
}
