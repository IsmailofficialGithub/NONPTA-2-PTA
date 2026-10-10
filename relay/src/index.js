require('dotenv').config();
const { WebSocketServer } = require('ws');
const { 
  Client, 
  GatewayIntentBits, 
  EmbedBuilder, 
  ActionRowBuilder, 
  ButtonBuilder, 
  ButtonStyle 
} = require('discord.js');
const { joinVoiceChannel, getVoiceConnection } = require('@discordjs/voice');
const TelegramBot = require('node-telegram-bot-api');
const AudioBridge = require('./audio_bridge');

const PORT = parseInt(process.env.PORT || '8080', 10);
const audioBridge = new AudioBridge(
  process.env.AUDIO_SOURCE_DEVICE || 'default',
  process.env.AUDIO_SINK_DEVICE || 'default'
);

let phoneSocket = null;
let activeCallInfo = null; // { number, name, startTime, discordMessageId, telegramMessageId }

// ==========================================
// 1. WEBSOCKET SERVER (Connection to Phone A)
// ==========================================
const wss = new WebSocketServer({ port: PORT });
console.log(`[WebSocket] Server running on port ${PORT}`);

wss.on('connection', (ws) => {
  console.log('[WebSocket] Phone A connected');
  phoneSocket = ws;

  ws.on('message', (message) => {
    try {
      const data = JSON.parse(message.toString());
      console.log('[WebSocket] Message from Phone A:', data);

      if (data.type === 'CALL_STATE' && data.payload) {
        handlePhoneCallEvent(data.payload);
      }
    } catch (err) {
      console.error('[WebSocket] Error parsing message:', err.message);
    }
  });

  ws.on('close', () => {
    console.log('[WebSocket] Phone A disconnected');
    if (phoneSocket === ws) phoneSocket = null;
  });

  ws.on('error', (err) => {
    console.error('[WebSocket] Error:', err.message);
  });
});

function sendToPhone(command) {
  if (phoneSocket && phoneSocket.readyState === 1) { // OPEN
    phoneSocket.send(JSON.stringify(command));
    console.log('[WebSocket] Sent to Phone A:', command);
    return true;
  } else {
    console.warn('[WebSocket] Cannot send command: Phone A is not connected');
    return false;
  }
}

// ==========================================
// 2. DISCORD BOT SETUP
// ==========================================
const discordClient = new Client({
  intents: [
    GatewayIntentBits.Guilds,
    GatewayIntentBits.GuildMessages,
    GatewayIntentBits.GuildVoiceStates
  ]
});

discordClient.on('ready', () => {
  console.log(`[Discord] Bot logged in as ${discordClient.user.tag}`);
});

discordClient.on('interactionCreate', async (interaction) => {
  if (!interaction.isButton()) return;

  const { customId } = interaction;
  await interaction.deferUpdate();

  if (customId === 'btn_answer') {
    console.log('[Discord] User clicked ANSWER');
    sendToPhone({ action: 'ANSWER' });

    // Join Discord Voice Channel
    try {
      const guild = discordClient.guilds.cache.get(process.env.DISCORD_GUILD_ID);
      const voiceChannel = guild?.channels.cache.get(process.env.DISCORD_VOICE_CHANNEL_ID);

      if (guild && voiceChannel) {
        const connection = joinVoiceChannel({
          channelId: voiceChannel.id,
          guildId: guild.id,
          adapterCreator: guild.voiceAdapterCreator,
          selfDeaf: false,
          selfMute: false
        });

        audioBridge.start(connection);
      }
    } catch (err) {
      console.error('[Discord] Voice connection failed:', err.message);
    }

    // Update Embed to "In Call"
    const inCallEmbed = new EmbedBuilder()
      .setTitle(`🟢 In Call: ${activeCallInfo?.name || activeCallInfo?.number || 'Unknown'}`)
      .setDescription(`Connected to Voice Channel <#${process.env.DISCORD_VOICE_CHANNEL_ID}>`)
      .setColor(0x10B981)
      .setTimestamp();

    const endRow = new ActionRowBuilder().addComponents(
      new ButtonBuilder()
        .setCustomId('btn_end')
        .setLabel('End Call')
        .setEmoji('🔴')
        .setStyle(ButtonStyle.Danger)
    );

    await interaction.editReply({ embeds: [inCallEmbed], components: [endRow] });

  } else if (customId === 'btn_reject') {
    console.log('[Discord] User clicked REJECT');
    sendToPhone({ action: 'REJECT' });

    const rejectEmbed = new EmbedBuilder()
      .setTitle(`❌ Call Rejected: ${activeCallInfo?.name || activeCallInfo?.number || 'Unknown'}`)
      .setColor(0xEF4444)
      .setTimestamp();

    await interaction.editReply({ embeds: [rejectEmbed], components: [] });
    activeCallInfo = null;

  } else if (customId === 'btn_end') {
    console.log('[Discord] User clicked END CALL');
    sendToPhone({ action: 'REJECT' });

    audioBridge.stop();
    const connection = getVoiceConnection(process.env.DISCORD_GUILD_ID);
    if (connection) connection.destroy();

    const endedEmbed = new EmbedBuilder()
      .setTitle(`🏁 Call Ended: ${activeCallInfo?.name || activeCallInfo?.number || 'Unknown'}`)
      .setColor(0x6B7280)
      .setTimestamp();

    await interaction.editReply({ embeds: [endedEmbed], components: [] });
    activeCallInfo = null;
  }
});

const isConfigured = (val) => val && !val.includes('your_') && val.trim() !== '';

if (isConfigured(process.env.DISCORD_BOT_TOKEN)) {
  discordClient.login(process.env.DISCORD_BOT_TOKEN).catch((err) => {
    console.error('[Discord] Login error:', err.message);
  });
} else {
  console.log('[Discord] Note: DISCORD_BOT_TOKEN contains placeholder. Update relay/.env to enable Discord Bot.');
}

// ==========================================
// 3. TELEGRAM BOT SETUP
// ==========================================
let telegramBot = null;
if (isConfigured(process.env.TELEGRAM_BOT_TOKEN)) {
  telegramBot = new TelegramBot(process.env.TELEGRAM_BOT_TOKEN, { polling: true });
  console.log('[Telegram] Bot initialized');

  telegramBot.on('callback_query', async (query) => {
    const action = query.data;
    const chatId = query.message.chat.id;
    const messageId = query.message.message_id;

    if (action === 'call_answer') {
      console.log('[Telegram] User tapped ANSWER');
      sendToPhone({ action: 'ANSWER' });
      await telegramBot.editMessageText(`🟢 <b>Call Answered</b>: ${activeCallInfo?.name || activeCallInfo?.number || ''}`, {
        chat_id: chatId,
        message_id: messageId,
        parse_mode: 'HTML'
      });
    } else if (action === 'call_reject') {
      console.log('[Telegram] User tapped REJECT');
      sendToPhone({ action: 'REJECT' });
      await telegramBot.editMessageText(`❌ <b>Call Rejected</b>: ${activeCallInfo?.name || activeCallInfo?.number || ''}`, {
        chat_id: chatId,
        message_id: messageId,
        parse_mode: 'HTML'
      });
      activeCallInfo = null;
    }

    telegramBot.answerCallbackQuery(query.id);
  });
}

// ==========================================
// 4. PHONE CALL EVENT HANDLER
// ==========================================
async function handlePhoneCallEvent(payload) {
  const { eventType, number, name, timestamp } = payload;
  const displayName = name ? `${name} (${number})` : number;

  if (eventType === 'RINGING') {
    activeCallInfo = { number, name, startTime: Date.now() };

    // Discord Alert
    if (discordClient.isReady() && process.env.DISCORD_TEXT_CHANNEL_ID) {
      const channel = discordClient.channels.cache.get(process.env.DISCORD_TEXT_CHANNEL_ID);
      if (channel) {
        const ringEmbed = new EmbedBuilder()
          .setTitle(`📞 INCOMING CALL: ${displayName}`)
          .setDescription('Status: **Ringing...**\nSelect an action below:')
          .setColor(0x2563EB)
          .setTimestamp(new Date(timestamp));

        const buttonsRow = new ActionRowBuilder().addComponents(
          new ButtonBuilder()
            .setCustomId('btn_answer')
            .setLabel('Answer & Join Voice')
            .setEmoji('✅')
            .setStyle(ButtonStyle.Success),
          new ButtonBuilder()
            .setCustomId('btn_reject')
            .setLabel('Reject Call')
            .setEmoji('❌')
            .setStyle(ButtonStyle.Danger)
        );

        const msg = await channel.send({ embeds: [ringEmbed], components: [buttonsRow] });
        activeCallInfo.discordMessageId = msg.id;
        activeCallInfo.channelId = channel.id;
      }
    }

    // Telegram Alert
    if (telegramBot && process.env.TELEGRAM_CHAT_ID) {
      const text = `📞 <b>INCOMING CALL:</b> <code>${displayName}</code>\nStatus: <i>Ringing...</i>`;
      const options = {
        parse_mode: 'HTML',
        reply_markup: {
          inline_keyboard: [
            [
              { text: '✅ Answer', callback_data: 'call_answer' },
              { text: '❌ Reject', callback_data: 'call_reject' }
            ]
          ]
        }
      };
      const tgMsg = await telegramBot.sendMessage(process.env.TELEGRAM_CHAT_ID, text, options);
      activeCallInfo.telegramMessageId = tgMsg.message_id;
    }

  } else if (eventType === 'MISSED') {
    // If missed, cleanup audio & update cards
    audioBridge.stop();
    const connection = getVoiceConnection(process.env.DISCORD_GUILD_ID);
    if (connection) connection.destroy();

    if (activeCallInfo?.discordMessageId && process.env.DISCORD_TEXT_CHANNEL_ID) {
      const channel = discordClient.channels.cache.get(process.env.DISCORD_TEXT_CHANNEL_ID);
      if (channel) {
        try {
          const msg = await channel.messages.fetch(activeCallInfo.discordMessageId);
          const missedEmbed = new EmbedBuilder()
            .setTitle(`🔴 Missed Call: ${displayName}`)
            .setColor(0xEF4444)
            .setTimestamp(new Date(timestamp));
          await msg.edit({ embeds: [missedEmbed], components: [] });
        } catch (e) {}
      }
    }

    if (telegramBot && activeCallInfo?.telegramMessageId && process.env.TELEGRAM_CHAT_ID) {
      telegramBot.editMessageText(`🔴 <b>Missed Call:</b> <code>${displayName}</code>`, {
        chat_id: process.env.TELEGRAM_CHAT_ID,
        message_id: activeCallInfo.telegramMessageId,
        parse_mode: 'HTML'
      }).catch(() => {});
    }

    activeCallInfo = null;

  } else if (eventType === 'ANSWERED') {
    console.log(`[Call] Call answered: ${displayName}`);
  }
}
