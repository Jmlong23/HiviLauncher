package com.hivi.launcher.coax.presenter;

import com.hivi.launcher.base.BasePresenter;
import com.hivi.launcher.coax.model.CoaxModel;
import com.hivi.launcher.coax.ui.CoaxView;

public final class CoaxPresenter extends BasePresenter<CoaxView> {
    private final CoaxModel mModel = new CoaxModel();

    public CoaxPresenter(CoaxView view) {
        super(view);
    }
}
