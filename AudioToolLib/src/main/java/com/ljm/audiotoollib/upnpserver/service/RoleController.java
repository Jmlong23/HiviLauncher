package com.ljm.audiotoollib.upnpserver.service;

import com.ljm.audiotoollib.upnpserver.cling.model.types.ErrorCode;
import com.ljm.audiotoollib.upnpserver.cling.support.renderingcontrol.RenderingControlException;

public final class RoleController {
    public static final int ROLE_DEFAULT_PARTICLE = 1;
    public static final int ROLE_SNOOPY = 2;
    public static final int ROLE_PP = 3;
    public static final int ROLE_SIREN = 4;
    public static final int ROLE_EVELYN = 5;

    public interface OnRoleChangedListener {
        void onRoleChanged(int roleId);
    }

    public interface OnRoleChangeRequestedListener {
        void onRoleChangeRequested(int roleId) throws RenderingControlException;
    }

    private static final RoleController INSTANCE = new RoleController();
    private int currentRoleId = ROLE_DEFAULT_PARTICLE;
    private int notifiedRoleId = ROLE_DEFAULT_PARTICLE;
    private long roleChangeVersion;
    private boolean awaitingUpload;
    private OnRoleChangedListener roleChangedListener;
    private volatile OnRoleChangeRequestedListener roleChangeRequestedListener;

    public static RoleController getInstance() {
        return INSTANCE;
    }

    public synchronized int getRoleId() {
        return currentRoleId;
    }

    public synchronized void reportRoleId(int roleId) {
        if (!isValidRoleId(roleId)) {
            throw new IllegalArgumentException("RoleId must be between 1 and 5");
        }
        if (currentRoleId == roleId) {
            return;
        }
        currentRoleId = roleId;
        roleChangeVersion++;
        awaitingUpload = false;
        notifyCurrentRole();
    }

    // Keep local queries current while deferring change events until this upload is confirmed.
    public synchronized long stageRoleId(int roleId) {
        if (!isValidRoleId(roleId)) {
            throw new IllegalArgumentException("RoleId must be between 1 and 5");
        }
        currentRoleId = roleId;
        awaitingUpload = true;
        return ++roleChangeVersion;
    }

    public synchronized boolean isPendingRoleChange(long version) {
        return awaitingUpload && roleChangeVersion == version;
    }

    public synchronized boolean confirmRoleUpload(long version) {
        if (!isPendingRoleChange(version)) {
            return false;
        }
        awaitingUpload = false;
        notifyCurrentRole();
        return true;
    }

    private void notifyCurrentRole() {
        if (notifiedRoleId == currentRoleId) {
            return;
        }
        notifiedRoleId = currentRoleId;
        if (roleChangedListener != null) {
            roleChangedListener.onRoleChanged(currentRoleId);
        }
    }

    public synchronized void setOnRoleChangedListener(OnRoleChangedListener listener) {
        roleChangedListener = listener;
    }

    public synchronized void clearOnRoleChangedListener(OnRoleChangedListener listener) {
        if (roleChangedListener == listener) {
            roleChangedListener = null;
        }
    }

    public synchronized void setOnRoleChangeRequestedListener(OnRoleChangeRequestedListener listener) {
        roleChangeRequestedListener = listener;
    }

    public synchronized void clearOnRoleChangeRequestedListener(OnRoleChangeRequestedListener listener) {
        if (roleChangeRequestedListener == listener) {
            roleChangeRequestedListener = null;
        }
    }

    public void requestRoleId(int roleId) throws RenderingControlException {
        if (!isValidRoleId(roleId)) {
            throw new RenderingControlException(ErrorCode.INVALID_ARGS, "RoleId must be between 1 and 5");
        }
        OnRoleChangeRequestedListener listener = roleChangeRequestedListener;
        if (listener == null) {
            throw new RenderingControlException(ErrorCode.ACTION_FAILED, "Role control is not ready");
        }
        listener.onRoleChangeRequested(roleId);
    }

    public static boolean isValidRoleId(int roleId) {
        return roleId >= ROLE_DEFAULT_PARTICLE && roleId <= ROLE_EVELYN;
    }

    public static int roleIdForCharacterSelection(int characterIndex, boolean live2dSelected) {
        if (!live2dSelected) {
            return ROLE_DEFAULT_PARTICLE;
        }
        switch (characterIndex) {
            case 0:
                return ROLE_SNOOPY;
            case 3:
                return ROLE_PP;
            case 2:
                return ROLE_EVELYN;
            case 1:
            default:
                return ROLE_SIREN;
        }
    }

    public static int characterIndexForRoleId(int roleId) {
        switch (roleId) {
            case ROLE_DEFAULT_PARTICLE:
                return -1;
            case ROLE_SNOOPY:
                return 0;
            case ROLE_PP:
                return 3;
            case ROLE_SIREN:
                return 1;
            case ROLE_EVELYN:
                return 2;
            default:
                throw new IllegalArgumentException("RoleId must be between 1 and 5");
        }
    }
}
