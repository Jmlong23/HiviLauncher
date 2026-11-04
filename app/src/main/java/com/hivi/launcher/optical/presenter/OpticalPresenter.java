package com.hivi.launcher.optical.presenter;

import com.hivi.launcher.base.BasePresenter;
import com.hivi.launcher.optical.model.OpticalModel;
import com.hivi.launcher.optical.ui.OpticalView;

public final class OpticalPresenter extends BasePresenter<OpticalView> {
    private final OpticalModel mModel = new OpticalModel();

    public OpticalPresenter(OpticalView view) {
        super(view);
    }

    public void init() {
        mModel.start(fftData -> runOnUiThread(() -> {
            OpticalView view = getView();
            if (view != null) {
                view.renderOpticalFftData(fftData);
            }
        }));
    }

    @Override
    public void detach() {
        mModel.stop();
        super.detach();
    }
}
