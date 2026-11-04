package com.hivi.launcher.line.presenter;

import com.hivi.launcher.base.BasePresenter;
import com.hivi.launcher.line.model.LineModel;
import com.hivi.launcher.line.ui.LineView;

public final class LinePresenter extends BasePresenter<LineView> {
    private final LineModel mModel = new LineModel();

    public LinePresenter(LineView view) {
        super(view);
    }

    public void init() {
        mModel.start(fftData -> runOnUiThread(() -> {
            LineView view = getView();
            if (view != null) {
                view.renderLineFftData(fftData);
            }
        }));
    }

    @Override
    public void detach() {
        mModel.stop();
        super.detach();
    }
}
