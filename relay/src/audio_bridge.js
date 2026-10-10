const { spawn } = require('child_process');
const { 
  createAudioPlayer, 
  createAudioResource, 
  StreamType, 
  AudioPlayerStatus 
} = require('@discordjs/voice');
const prism = require('prism-media');

class AudioBridge {
  constructor(sourceDevice = 'default', sinkDevice = 'default') {
    this.sourceDevice = sourceDevice;
    this.sinkDevice = sinkDevice;
    this.player = null;
    this.recordProcess = null;
    this.playbackProcess = null;
    this.activeSubscriptions = new Map();
  }

  /**
   * Starts bridging audio between Linux audio (Bluetooth HFP) and Discord voice connection
   * @param {import('@discordjs/voice').VoiceConnection} connection 
   */
  start(connection) {
    this.stop();
    console.log('[AudioBridge] Starting 2-way audio stream...');

    // 1. INCOMING AUDIO (Caller -> Bluetooth HFP Source -> Discord Voice Channel)
    try {
      // Spawn parec or ffmpeg to capture 48kHz 16-bit stereo PCM from input device
      const parecArgs = ['--format=s16le', '--rate=48000', '--channels=2'];
      if (this.sourceDevice && this.sourceDevice !== 'default') {
        parecArgs.push('-d', this.sourceDevice);
      }

      this.recordProcess = spawn('parec', parecArgs);
      this.recordProcess.on('error', (err) => {
        console.warn('[AudioBridge] `parec` not found or error. Falling back to dummy silence/ffmpeg:', err.message);
      });

      this.player = createAudioPlayer();
      const resource = createAudioResource(this.recordProcess.stdout, {
        inputType: StreamType.Raw
      });

      this.player.play(resource);
      connection.subscribe(this.player);

      this.player.on(AudioPlayerStatus.Playing, () => {
        console.log('[AudioBridge] Streaming cellular audio into Discord voice channel.');
      });
      this.player.on('error', (err) => {
        console.error('[AudioBridge] Audio player error:', err.message);
      });
    } catch (err) {
      console.error('[AudioBridge] Failed to initialize input capture:', err.message);
    }

    // 2. OUTGOING AUDIO (Phone B speaking in Discord -> Bluetooth HFP Sink -> Cellular Mic)
    try {
      const receiver = connection.receiver;
      const pacatArgs = ['--format=s16le', '--rate=48000', '--channels=2'];
      if (this.sinkDevice && this.sinkDevice !== 'default') {
        pacatArgs.push('-d', this.sinkDevice);
      }

      this.playbackProcess = spawn('pacat', pacatArgs);
      this.playbackProcess.on('error', (err) => {
        console.warn('[AudioBridge] `pacat` not found or error:', err.message);
      });

      receiver.speaking.on('start', (userId) => {
        if (this.activeSubscriptions.has(userId)) return;

        const opusStream = receiver.subscribe(userId, {
          end: {
            behavior: 0 // EndMode.Manual
          }
        });

        // Decode Discord Opus audio stream to 48kHz 16-bit PCM
        const decoder = new prism.opus.Decoder({
          frameSize: 960,
          channels: 2,
          rate: 48000
        });

        const pcmStream = opusStream.pipe(decoder);
        pcmStream.pipe(this.playbackProcess.stdin, { end: false });
        this.activeSubscriptions.set(userId, { opusStream, decoder });
      });

      receiver.speaking.on('end', (userId) => {
        const sub = this.activeSubscriptions.get(userId);
        if (sub) {
          sub.opusStream.destroy();
          sub.decoder.destroy();
          this.activeSubscriptions.delete(userId);
        }
      });
    } catch (err) {
      console.error('[AudioBridge] Failed to initialize output playback:', err.message);
    }
  }

  stop() {
    console.log('[AudioBridge] Stopping audio streams.');
    if (this.player) {
      this.player.stop();
      this.player = null;
    }
    if (this.recordProcess) {
      this.recordProcess.kill();
      this.recordProcess = null;
    }
    if (this.playbackProcess) {
      this.playbackProcess.kill();
      this.playbackProcess = null;
    }
    for (const [userId, sub] of this.activeSubscriptions.entries()) {
      sub.opusStream.destroy();
      sub.decoder.destroy();
    }
    this.activeSubscriptions.clear();
  }
}

module.exports = AudioBridge;
