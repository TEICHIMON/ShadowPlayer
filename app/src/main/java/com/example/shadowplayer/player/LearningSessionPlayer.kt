package com.example.shadowplayer.player

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

/**
 * Small, testable command bridge between Media3 transport controls and sentence playback.
 */
internal class MediaCommandDispatcher(
    private val playAction: () -> Unit,
    private val pauseAction: () -> Unit,
    private val seekAction: (Long) -> Unit,
    private val previousAction: () -> Unit,
    private val nextAction: () -> Unit,
    private val seekBackwardAction: () -> Unit,
    private val seekForwardAction: () -> Unit
) {
    fun play() = playAction()
    fun pause() = pauseAction()
    fun setPlayWhenReady(playWhenReady: Boolean) = if (playWhenReady) play() else pause()
    fun seekTo(positionMs: Long) = seekAction(positionMs)
    fun previous() = previousAction()
    fun next() = nextAction()
    fun seekBackward() = seekBackwardAction()
    fun seekForward() = seekForwardAction()
    fun stop() = pause()
}

/**
 * MediaSession sees this Player, while AudioPlayer keeps the real ExoPlayer instance.
 * Overridden transport commands preserve ShadowPlayer's sentence/repeat semantics.
 */
@UnstableApi
internal class LearningSessionPlayer(
    player: Player,
    private val commands: MediaCommandDispatcher
) : ForwardingPlayer(player) {

    private val sentenceCommands = setOf(
        Player.COMMAND_PLAY_PAUSE,
        Player.COMMAND_STOP,
        Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
        Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
        Player.COMMAND_SEEK_TO_PREVIOUS,
        Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
        Player.COMMAND_SEEK_TO_NEXT,
        Player.COMMAND_SEEK_BACK,
        Player.COMMAND_SEEK_FORWARD
    )

    override fun getAvailableCommands(): Player.Commands = super.getAvailableCommands()
        .buildUpon()
        .add(Player.COMMAND_PLAY_PAUSE)
        .add(Player.COMMAND_STOP)
        .add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
        .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
        .add(Player.COMMAND_SEEK_TO_PREVIOUS)
        .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
        .add(Player.COMMAND_SEEK_TO_NEXT)
        .add(Player.COMMAND_SEEK_BACK)
        .add(Player.COMMAND_SEEK_FORWARD)
        .build()

    override fun isCommandAvailable(command: Int): Boolean =
        command in sentenceCommands || super.isCommandAvailable(command)

    override fun play() = commands.play()

    override fun pause() = commands.pause()

    override fun setPlayWhenReady(playWhenReady: Boolean) =
        commands.setPlayWhenReady(playWhenReady)

    override fun seekTo(positionMs: Long) = commands.seekTo(positionMs)

    override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
        if (mediaItemIndex == currentMediaItemIndex) {
            commands.seekTo(positionMs)
        }
    }

    override fun seekBack() = commands.seekBackward()

    override fun seekForward() = commands.seekForward()

    override fun seekToPrevious() = commands.previous()

    override fun seekToPreviousMediaItem() = commands.previous()

    override fun seekToNext() = commands.next()

    override fun seekToNextMediaItem() = commands.next()

    override fun stop() = commands.stop()
}
