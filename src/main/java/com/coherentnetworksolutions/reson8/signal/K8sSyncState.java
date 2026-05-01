package com.coherentnetworksolutions.reson8.signal;

import java.util.concurrent.atomic.AtomicBoolean;

import io.quarkus.logging.Log;
import io.quarkus.vertx.ConsumeEvent;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Single source of truth for the k8s-sync enabled/disabled flag.
 * <p>
 * {@link com.coherentnetworksolutions.reson8.signal.SignalManager},
 * {@link com.coherentnetworksolutions.reson8.k8s.client.K8Client}, and
 * {@link com.coherentnetworksolutions.reson8.k8s.client.ThanosMetricPoller}
 * previously each maintained an independent copy of this boolean. All three
 * now inject this bean and call {@link #isEnabled()}.
 */
@ApplicationScoped
public class K8sSyncState {

    private final AtomicBoolean enabled = new AtomicBoolean(true);

    @ConsumeEvent("k8s-sync-enable")
    public void onSyncToggle(boolean state) {
        Log.debugf("K8sSyncState: updating to [%s]", state);
        enabled.set(state);
    }

    public boolean isEnabled() {
        return enabled.get();
    }
}
