package com.example.shadowplayer.service

import android.content.Intent
import android.os.Bundle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands
import androidx.media3.session.SessionResult
import com.example.shadowplayer.player.AudioPlayer
import com.example.shadowplayer.player.LearningSessionPlayer
import com.example.shadowplayer.player.MediaCommandDispatcher
import com.example.shadowplayer.player.PlaybackCoordinator
import com.example.shadowplayer.player.SentencePlayer
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@androidx.annotation.OptIn(UnstableApi::class)
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject lateinit var audioPlayer: AudioPlayer
    @Inject lateinit var sentencePlayer: SentencePlayer
    @Inject lateinit var playbackCoordinator: PlaybackCoordinator

    private var mediaSession: MediaSession? = null
    private lateinit var notificationCommandDispatcher: NotificationAudioCommandDispatcher
    private val previousAudioCommand = SessionCommand(
        PlaybackNotificationActions.ACTION_PREVIOUS_AUDIO,
        Bundle.EMPTY
    )
    private val nextAudioCommand = SessionCommand(
        PlaybackNotificationActions.ACTION_NEXT_AUDIO,
        Bundle.EMPTY
    )

    override fun onCreate() {
        super.onCreate()
        setMediaNotificationProvider(ShadowMediaNotificationProvider(this, sentencePlayer))
        notificationCommandDispatcher = NotificationAudioCommandDispatcher(
            previousAudioAction = playbackCoordinator::playPreviousAudio,
            nextAudioAction = playbackCoordinator::playNextAudio
        )

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

        val previousAudioButton = CommandButton.Builder(CommandButton.ICON_PREVIOUS)
            .setSessionCommand(previousAudioCommand)
            .setDisplayName("上一条音频")
            .build()
        val nextAudioButton = CommandButton.Builder(CommandButton.ICON_NEXT)
            .setSessionCommand(nextAudioCommand)
            .setDisplayName("下一条音频")
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
                    val sessionCommands = SessionCommands.Builder()
                        .addSessionCommands(
                            MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.commands
                        )
                        .add(previousAudioCommand)
                        .add(nextAudioCommand)
                        .build()
                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailableSessionCommands(sessionCommands)
                        .build()
                }

                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand,
                    args: Bundle
                ): ListenableFuture<SessionResult> {
                    if (notificationCommandDispatcher.dispatch(customCommand.customAction)) {
                        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    }
                    return super.onCustomCommand(session, controller, customCommand, args)
                }
            })
            .setMediaButtonPreferences(
                listOf(previousAudioButton, nextAudioButton)
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
