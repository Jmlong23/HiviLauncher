package com.hivi.launcher.wifi.model;

import com.hivi.launcher.wifi.presenter.WifiPresenter;
import com.hivi.launcher.wifi.ui.WifiView;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class WifiPresenterNetworkActionsTest {
    private RecordingWifiView mView;
    private WifiPresenter mPresenter;

    @Before
    public void setUp() {
        mView = new RecordingWifiView();
        mPresenter = new WifiPresenter(mView);
    }

    @After
    public void tearDown() {
        mPresenter.destroy();
    }

    @Test
    public void selectingConnectedNetworkShowsActionsInsteadOfConnecting() {
        WifiNetwork network = network(true, false, true, true, false);

        mPresenter.onWifiNetworkSelected(network);

        assertSame(network, mView.mActionsNetwork);
        assertNull(mView.mPasswordNetwork);
    }

    @Test
    public void selectingConnectedNetworkWithoutSavedFlagStillShowsActions() {
        WifiNetwork network = network(false, false, false, true, false);

        mPresenter.onWifiNetworkSelected(network);

        assertSame(network, mView.mActionsNetwork);
        assertNull(mView.mPasswordNetwork);
    }

    @Test
    public void selectingConnectedNetworkWhileConnectionIsFinishingShowsActions() {
        WifiNetwork network = network(true, false, true, true, true);

        mPresenter.onWifiNetworkSelected(network);

        assertSame(network, mView.mActionsNetwork);
        assertNull(mView.mPasswordNetwork);
    }

    @Test
    public void selectingSavedDisconnectedNetworkShowsActions() {
        WifiNetwork network = network(true, false, true, false, false);

        mPresenter.onWifiNetworkSelected(network);

        assertSame(network, mView.mActionsNetwork);
        assertNull(mView.mPasswordNetwork);
    }

    @Test
    public void selectingSavedEnterpriseNetworkAllowsNetworkManagement() {
        WifiNetwork network = network(true, true, true, false, false);

        mPresenter.onWifiNetworkSelected(network);

        assertSame(network, mView.mActionsNetwork);
        assertNull(mView.mPasswordNetwork);
        assertNull(mView.mToast);
    }

    @Test
    public void selectingUnsavedSecureNetworkStillRequestsPassword() {
        WifiNetwork network = network(true, false, false, false, false);

        mPresenter.onWifiNetworkSelected(network);

        assertSame(network, mView.mPasswordNetwork);
        assertNull(mView.mActionsNetwork);
    }

    @Test
    public void selectingUnsavedOpenNetworkDoesNotShowManagementActions() {
        mPresenter.onWifiNetworkSelected(network(false, false, false, false, false));

        assertNull(mView.mActionsNetwork);
        assertNull(mView.mPasswordNetwork);
    }

    @Test
    public void selectingConnectingNetworkDoesNotShowAnotherDialog() {
        mPresenter.onWifiNetworkSelected(network(true, false, true, false, true));

        assertNull(mView.mActionsNetwork);
        assertNull(mView.mPasswordNetwork);
    }

    @Test
    public void selectingNullNetworkDoesNotShowDialog() {
        mPresenter.onWifiNetworkSelected(null);

        assertNull(mView.mActionsNetwork);
        assertNull(mView.mPasswordNetwork);
    }

    @Test
    public void connectActionDoesNotReopenSavedNetworkManagement() {
        mPresenter.connectNetwork(network(true, false, true, false, false));

        assertNull(mView.mActionsNetwork);
        assertNull(mView.mPasswordNetwork);
    }

    private static WifiNetwork network(boolean secure, boolean enterprise, boolean saved,
            boolean connected, boolean connecting) {
        return new WifiNetwork("Home-5G", "", -50, secure, enterprise, saved, connected,
                connecting);
    }

    private static final class RecordingWifiView implements WifiView {
        private WifiNetwork mActionsNetwork;
        private WifiNetwork mPasswordNetwork;
        private String mToast;

        @Override
        public void renderWifiNetworks(List<WifiNetwork> networks, String connectedSsid) {
        }

        @Override
        public void setWifiRefreshing(boolean refreshing) {
        }

        @Override
        public void showWifiEmptyState(String message) {
        }

        @Override
        public void showWifiPasswordDialog(WifiNetwork network) {
            mPasswordNetwork = network;
        }

        @Override
        public void showWifiNetworkActionsDialog(WifiNetwork network) {
            mActionsNetwork = network;
        }

        @Override
        public void showToast(String message) {
            mToast = message;
        }
    }
}
