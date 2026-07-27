package com.hivi.launcher.hdmi.presenter;

import com.hivi.launcher.base.BasePresenter;
import com.hivi.launcher.hdmi.model.HdmiModel;
import com.hivi.launcher.hdmi.ui.HdmiView;

public final class HdmiPresenter extends BasePresenter<HdmiView> {
    private final HdmiModel mModel = new HdmiModel();

    public HdmiPresenter(HdmiView view) {
        super(view);
    }
}
