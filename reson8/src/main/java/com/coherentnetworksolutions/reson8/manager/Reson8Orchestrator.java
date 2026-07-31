package com.coherentnetworksolutions.reson8.manager;
import com.coherentnetworksolutions.reson8.audio.mixer.Mixer;
import com.coherentnetworksolutions.reson8.audio.input.InputChannelFactory;
import com.coherentnetworksolutions.reson8.audio.output.from.ClientChannelFactory;
import com.coherentnetworksolutions.reson8.audio.output.from.MixerOutputToClientManagerChannel;
import com.coherentnetworksolutions.reson8.k8s.client.K8sClient;
import com.coherentnetworksolutions.reson8.audio.sound.SoundDefinitionRegistry;
import com.coherentnetworksolutions.reson8.manager.config.Reson8Config;
import com.coherentnetworksolutions.reson8.signal.SignalManager;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class Reson8Orchestrator {

    @Inject Reson8Config config;

    /** Reflects the actual effective value of {@code quarkus.oidc.enabled} after all config sources are resolved. */
    @ConfigProperty(name = "quarkus.oidc.enabled", defaultValue = "false")
    boolean quarkusOidcEnabled;

    @Inject InputChannelFactory channelFactory;

    @Inject Mixer mixer;
    @Inject ClientChannelFactory outputChannelFactory;
    @Inject SoundDefinitionRegistry soundRegistry;
    @Inject SignalManager signalManager;
    @Inject K8sClient k8sClient;

    void onStart(@Observes StartupEvent ev) {
        Log.info("Reson8 Engine Starting...");

        MixerOutputToClientManagerChannel browserOutput = outputChannelFactory.create("browser", "browser-out", 1.0);
        mixer.addOutputChannel(browserOutput);

        mixer.start();

        Log.info("Orchestrator: Audio Pipeline is now LIVE.");

        logSecuritySummary();

        Log.info("Reson8 Soundscape is LIVE");
    }

    private void logSecuritySummary() {
        Log.infof("Security: OIDC=%s, endpoint-authorization=%s, login-available=%s, dev-tier-cookie=%s",
                quarkusOidcEnabled,
                config.security().endpointAuthorizationEnabled(),
                config.security().loginAvailable(),
                config.security().devTierCookieEnabled());

        int adminCount = config.security().adminGroups().map(groups -> groups != null ? groups.size() : 0).orElse(0);
        int viewerCount = config.security().viewerGroups().map(groups -> groups != null ? groups.size() : 0).orElse(0);
        java.util.List<String> streamGroups = config.security().streamGroups().orElse(java.util.List.of());
        String sentinel = config.security().anonymousStreamSentinel();
        boolean anonStream = streamGroups.contains(sentinel);

        Log.infof("Security tiers: admin-groups=%d, viewer-groups=%d, stream-groups=%d (anonymous-stream=%s)",
                adminCount, viewerCount, streamGroups.size(), anonStream);

        Log.infof("Audio path: %s", config.audioPath());
    }

    

}

