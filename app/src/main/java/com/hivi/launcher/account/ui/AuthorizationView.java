package com.hivi.launcher.account.ui;

import com.hivi.launcher.account.model.AuthorizationUiState;
import com.hivi.launcher.base.BaseView;

public interface AuthorizationView extends BaseView {
    void renderAuthorization(AuthorizationUiState state);
}
