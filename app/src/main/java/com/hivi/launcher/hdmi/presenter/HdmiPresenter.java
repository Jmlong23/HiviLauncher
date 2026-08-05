package com.hivi.launcher.hdmi.presenter;

import com.hivi.launcher.base.BasePresenter;
import com.hivi.launcher.hdmi.model.HdmiModel;
import com.hivi.launcher.hdmi.ui.HdmiView;

public final class HdmiPresenter extends BasePresenter<HdmiView> {
    private final HdmiModel mModel = new HdmiModel();

    public HdmiPresenter(HdmiView view) {
        super(view);
    }

    public void init() {
        mModel.start(fftData -> runOnUiThread(() -> {
            HdmiView view = getView();
            if (view != null) {
                view.renderHdmiFftData(fftData);
            }
        }));
    }

    @Override
    public void detach() {
        mModel.stop();
        super.detach();
    }
}
