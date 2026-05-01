package com.coherentnetworksolutions.reson8.k8s.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.freedesktop.gstreamer.Gst;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.coherentnetworksolutions.reson8.audio.mixer.Mixer;
import com.coherentnetworksolutions.reson8.manager.config.Reson8Config.ClusterMetric;
import com.coherentnetworksolutions.reson8.signal.K8sSyncState;
import com.coherentnetworksolutions.reson8.signal.SignalBucket;
import com.coherentnetworksolutions.reson8.signal.SignalManager;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentSpec;
import io.fabric8.kubernetes.api.model.apps.DeploymentStatus;
import io.fabric8.kubernetes.api.model.apps.DeploymentList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.*;

@ExtendWith(MockitoExtension.class)
class K8sClientMetricsTest {

    @Mock
    KubernetesClient client;

    @Mock
    Mixer mixer;

    @Mock
    SignalManager signalManager;

    @Mock
    K8sSyncState k8sSyncState;

    @Mock
    K8sAuthTokenProvider authTokenProvider;

    @Mock
    io.vertx.core.Vertx vertx;

    @Mock
    io.vertx.mutiny.core.eventbus.EventBus eventBus;

    @InjectMocks
    K8sClient k8sClient;

    @BeforeEach
    void assertNotNativeGst() {
        assertFalse(Gst.isInitialized(),
                "Toolkit abstraction leak detected in K8sClientMetricsTest.");
    }

    @BeforeEach
    void setFlowing() throws Exception {
        var f = K8sClient.class.getDeclaredField("isFlowing");
        f.setAccessible(true);
        ((AtomicBoolean) f.get(k8sClient)).set(true);
        lenient().when(k8sSyncState.isEnabled()).thenReturn(true);
    }

    // ─── pullCapacity ─────────────────────────────────────────────────

    @Test
    @DisplayName("pullCapacity: skips update when cluster has zero nodes")
    @SuppressWarnings("unchecked")
    void pullCapacity_zeroNodes_skipsUpdate() throws Exception {
        NonNamespaceOperation<Node, NodeList, ?> nodeOp = mock(NonNamespaceOperation.class);
        NodeList nodeList = mock(NodeList.class);
        when(nodeList.getItems()).thenReturn(List.of());
        when(nodeOp.list()).thenReturn(nodeList);
        doReturn(nodeOp).when(client).nodes();

        k8sClient.pullCapacity();

        // gotCapacity should remain false — no interaction with clusterCapacity
        var gotCap = K8sClient.class.getDeclaredField("gotCapacity");
        gotCap.setAccessible(true);
        assertFalse((boolean) gotCap.get(k8sClient));
    }

    @Test
    @DisplayName("pullCapacity: skips when k8s sync is disabled")
    void pullCapacity_syncDisabled_skips() throws Exception {
        when(k8sSyncState.isEnabled()).thenReturn(false);

        k8sClient.pullCapacity();

        verify(client, never()).nodes();
    }

    // ─── computeDeploymentHealthPct ───────────────────────────────────

    @Test
    @DisplayName("computeDeploymentHealthPct: returns 100.0 when no deployments exist")
    void computeDeploymentHealthPct_noDeployments_returns100() throws Exception {
        mockDeployments(List.of());

        double pct = invokeComputeDeploymentHealthPct();
        assert pct == 100.0 : "Expected 100.0, got " + pct;
    }

    @Test
    @DisplayName("computeDeploymentHealthPct: handles null replica counts gracefully")
    void computeDeploymentHealthPct_nullReplicas_treatedAsZero() throws Exception {
        Deployment dep = buildDeployment(null, null);
        mockDeployments(List.of(dep));

        double pct = invokeComputeDeploymentHealthPct();
        assert pct == 100.0 : "Expected 100.0 when total desired is 0";
    }

    @Test
    @DisplayName("computeDeploymentHealthPct: 50% health when half replicas available")
    void computeDeploymentHealthPct_halfAvailable_returns50() throws Exception {
        Deployment dep = buildDeployment(4, 2);
        mockDeployments(List.of(dep));

        double pct = invokeComputeDeploymentHealthPct();
        assert pct == 50.0 : "Expected 50.0, got " + pct;
    }

    // ─── pushStatsBucket ──────────────────────────────────────────────

    @Test
    @DisplayName("pushStatsBucket: calls updateSignalIntensity when bucket is present")
    void pushStatsBucket_bucketFound_updatesIntensity() throws Exception {
        SignalBucket bucket = mock(SignalBucket.class);
        when(bucket.getName()).thenReturn("cpu");
        when(signalManager.getBucketByMetric(ClusterMetric.CPU)).thenReturn(Optional.of(bucket));

        invokePushStatsBucket(ClusterMetric.CPU, 67.5);

        verify(signalManager).updateSignalIntensity("cpu", 67.5);
    }

    @Test
    @DisplayName("pushStatsBucket: skips gracefully when no bucket is registered for the metric")
    void pushStatsBucket_noBucket_skips() throws Exception {
        when(signalManager.getBucketByMetric(ClusterMetric.MEMORY)).thenReturn(Optional.empty());

        invokePushStatsBucket(ClusterMetric.MEMORY, 30.0);

        verify(signalManager, never()).updateSignalIntensity(anyString(), anyDouble());
    }

    // ─── helpers ──────────────────────────────────────────────────────

    private void mockDeployments(List<Deployment> deployments) {
        var appsOp = mock(io.fabric8.kubernetes.client.dsl.AppsAPIGroupDSL.class);
        var depOp  = mock(io.fabric8.kubernetes.client.dsl.MixedOperation.class);
        var inAny  = mock(io.fabric8.kubernetes.client.dsl.NonNamespaceOperation.class);
        DeploymentList depList = mock(DeploymentList.class);

        when(client.apps()).thenReturn(appsOp);
        doReturn(depOp).when(appsOp).deployments();
        doReturn(inAny).when(depOp).inAnyNamespace();
        when(inAny.list()).thenReturn(depList);
        when(depList.getItems()).thenReturn(deployments);
    }

    private Deployment buildDeployment(Integer desired, Integer available) {
        Deployment d = new Deployment();
        DeploymentSpec spec = new DeploymentSpec();
        spec.setReplicas(desired);
        d.setSpec(spec);

        DeploymentStatus status = new DeploymentStatus();
        status.setAvailableReplicas(available);
        d.setStatus(status);
        return d;
    }

    private double invokeComputeDeploymentHealthPct() throws Exception {
        var m = K8sClient.class.getDeclaredMethod("computeDeploymentHealthPct");
        m.setAccessible(true);
        return (double) m.invoke(k8sClient);
    }

    private void invokePushStatsBucket(ClusterMetric metric, double rawValue) throws Exception {
        var m = K8sClient.class.getDeclaredMethod("pushStatsBucket", ClusterMetric.class, double.class);
        m.setAccessible(true);
        m.invoke(k8sClient, metric, rawValue);
    }
}
