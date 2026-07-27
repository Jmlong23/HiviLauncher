package com.hivi.launcher.optical.presenter;

import com.hivi.launcher.base.BasePresenter;
import com.hivi.launcher.optical.model.OpticalModel;
import com.hivi.launcher.optical.ui.OpticalView;

public final class OpticalPresenter extends BasePresenter<OpticalView> {
    private final OpticalModel mModel = new OpticalModel();

    public OpticalPresenter(OpticalView view) {
        super(view);
    }
}
