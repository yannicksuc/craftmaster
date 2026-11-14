package fr.lordfinn.craftmaster.compat;

import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.broadcast.Speaking;

/**
 * Simple Voice Chat plugin (loaded only when that mod is there, through its "voicechat" entrypoint): every voice
 * packet a player sends marks them as speaking, for the automatic direction ({@code Director}, {@code SpeakerCuts}).
 * Nothing is recorded or decoded: only who and when.
 */
public class VoiceActivity implements VoicechatPlugin {

    @Override
    public String getPluginId() {
        return CraftMaster.MOD_ID;
    }

    @Override
    public void initialize(VoicechatApi api) {
        Speaking.setAvailable();
        CraftMaster.LOG.info("[Réalisation] Chat vocal détecté : la réalisation automatique peut suivre qui parle.");
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(MicrophonePacketEvent.class, event -> {
            VoicechatConnection sender = event.getSenderConnection();
            if (sender != null && sender.getPlayer() != null) Speaking.heard(sender.getPlayer().getUuid());
        });
    }
}
