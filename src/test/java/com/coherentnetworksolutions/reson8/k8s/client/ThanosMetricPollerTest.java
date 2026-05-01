package com.coherentnetworksolutions.reson8.k8s.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.freedesktop.gstreamer.Gst;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.coherentnetworksolutions.reson8.k8s.client.ThanosMetricPoller.ThanosData;
import com.coherentnetworksolutions.reson8.k8s.client.ThanosMetricPoller.ThanosResponse;
import com.coherentnetworksolutions.reson8.k8s.client.ThanosMetricPoller.ThanosResult;
import com.coherentnetworksolutions.reson8.signal.K8sSyncState;
import com.coherentnetworksolutions.reson8.signal.SignalManager;

import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(MockitoExtension.class)
class ThanosMetricPollerTest {

    @Mock
    ThanosRestClient restClient;

    @Mock
    SignalManager signalManager;

    @Mock
    K8sSyncState k8sSyncState;

    @Mock
    K8sAuthTokenProvider authTokenProvider;

    @InjectMocks
    ThanosMetricPoller poller;

    @BeforeEach
    void assertNotNativeGst() {
        assertFalse(Gst.isInitialized(),
                "Toolkit abstraction leak detected in ThanosMetricPollerTest.");
    }

    @BeforeEach
    void injectRestClient() throws Exception {
        // ThanosMetricPoller builds restClient lazily in thanosStartup(); bypass that
        // for unit tests by injecting a mock directly via reflection.
        Field f = ThanosMetricPoller.class.getDeclaredField("restClient");
        f.setAccessible(true);
        f.set(poller, restClient);

        // Mark as initialized so pollMetrics() proceeds
        Field init = ThanosMetricPoller.class.getDeclaredField("initialized");
        init.setAccessible(true);
        ((AtomicBoolean) init.get(poller)).set(true);

        lenient().when(k8sSyncState.isEnabled()).thenReturn(true);
    }

    // ─── parseResponse ────────────────────────────────────────────────

    @Test
    @DisplayName("parseResponse: returns 0.0 when result list is empty")
    void parseResponse_emptyResult_returnsZero() throws Exception {
        ThanosResponse response = new ThanosResponse("success",
                new ThanosData("vector", List.of()));

        double val = invokeParseResponse(response);
        assert val == 0.0;
    }

    @Test
    @DisplayName("parseResponse: returns 0.0 when value list is null")
    void parseResponse_nullValueList_returnsZero() throws Exception {
        ThanosResult result = new ThanosResult(java.util.Map.of(), null);
        ThanosResponse response = new ThanosResponse("success",
                new ThanosData("vector", List.of(result)));

        double val = invokeParseResponse(response);
        assert val == 0.0;
    }

    @Test
    @DisplayName("parseResponse: returns 0.0 when value list has fewer than 2 elements")
    void parseResponse_shortValueList_returnsZero() throws Exception {
        ThanosResult result = new ThanosResult(java.util.Map.of(), List.of("1234"));
        ThanosResponse response = new ThanosResponse("success",
                new ThanosData("vector", List.of(result)));

        double val = invokeParseResponse(response);
        assert val == 0.0;
    }

    @Test
    @DisplayName("parseResponse: parses value from [timestamp, value] format")
    void parseResponse_validResult_returnsValue() throws Exception {
        ThanosResult result = new ThanosResult(java.util.Map.of(), List.of("1700000000", "42.5"));
        ThanosResponse response = new ThanosResponse("success",
                new ThanosData("vector", List.of(result)));

        double val = invokeParseResponse(response);
        assert val == 42.5;
    }

    @Test
    @DisplayName("parseResponse: handles multiple results by using the first")
    void parseResponse_multiResult_usesFirstValue() throws Exception {
        ThanosResult r1 = new ThanosResult(java.util.Map.of(), List.of("1700000000", "10.0"));
        ThanosResult r2 = new ThanosResult(java.util.Map.of(), List.of("1700000001", "99.0"));
        ThanosResponse response = new ThanosResponse("success",
                new ThanosData("vector", List.of(r1, r2)));

        double val = invokeParseResponse(response);
        assert val == 10.0;
    }

    // ─── pollMetrics ──────────────────────────────────────────────────

    @Test
    @DisplayName("pollMetrics: does nothing when not initialized")
    void pollMetrics_notInitialized_skips() throws Exception {
        Field init = ThanosMetricPoller.class.getDeclaredField("initialized");
        init.setAccessible(true);
        ((AtomicBoolean) init.get(poller)).set(false);

        poller.pollMetrics();

        verify(restClient, never()).query(anyString(), anyString());
    }

    @Test
    @DisplayName("pollMetrics: does nothing when k8s sync is disabled")
    void pollMetrics_syncDisabled_skips() throws Exception {
        when(k8sSyncState.isEnabled()).thenReturn(false);

        poller.pollMetrics();

        verify(restClient, never()).query(anyString(), anyString());
    }

    @Test
    @DisplayName("pollMetrics: queries Thanos and updates SignalManager for each active query")
    void pollMetrics_happyPath_updatesSignalManager() throws Exception {
        registerActiveQuery("cpu-load", "rate(cpu_usage[1m])");

        ThanosResult r = new ThanosResult(java.util.Map.of(), List.of("1700000000", "77.3"));
        ThanosResponse response = new ThanosResponse("success", new ThanosData("vector", List.of(r)));
        when(restClient.query(eq("rate(cpu_usage[1m])"), anyString())).thenReturn(response);

        // Inject auth token
        Field authField = ThanosMetricPoller.class.getDeclaredField("authToken");
        authField.setAccessible(true);
        authField.set(poller, "Bearer test-token");

        poller.pollMetrics();

        verify(signalManager).updateSignalIntensity(eq("cpu-load"), eq(77.3));
    }

    // ─── helpers ──────────────────────────────────────────────────────

    private double invokeParseResponse(ThanosResponse response) throws Exception {
        Method m = ThanosMetricPoller.class.getDeclaredMethod("parseResponse", ThanosResponse.class);
        m.setAccessible(true);
        return (double) m.invoke(poller, response);
    }

    @SuppressWarnings("unchecked")
    private void registerActiveQuery(String signalId, String promql) throws Exception {
        Field f = ThanosMetricPoller.class.getDeclaredField("activeQueries");
        f.setAccessible(true);
        ((java.util.Map<String, String>) f.get(poller)).put(signalId, promql);
    }
}
