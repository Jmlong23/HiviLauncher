package com.hivi.launcher.base;

import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.hivi.launcher.R;

public abstract class BaseFragment<P extends BasePresenter<?>> extends Fragment implements BaseView {
    private P mPresenter;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(getLayoutResId(), container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        TextView pageTitle = view.findViewById(R.id.page_title);
        if (pageTitle != null) {
            pageTitle.setText(getPageTitleResId());
        }
        mPresenter = createPresenter();
    }

    @Override
    public void onDestroyView() {
        if (mPresenter != null) {
            mPresenter.detach();
            mPresenter = null;
        }
        super.onDestroyView();
    }

    @Override
    public void showToast(String message) {
        if (isAdded()) {
            Toast.makeText(getActivity(), message, Toast.LENGTH_SHORT).show();
        }
    }

    protected abstract P createPresenter();

    @Nullable
    protected final P getPresenter() {
        return mPresenter;
    }

    protected abstract int getLayoutResId();

    protected abstract int getPageTitleResId();
}
