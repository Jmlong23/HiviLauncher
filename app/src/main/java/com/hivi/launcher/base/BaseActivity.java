package com.hivi.launcher.base;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.viewbinding.ViewBinding;

public abstract class BaseActivity<B extends ViewBinding, P extends BasePresenter<?>> extends Activity
        implements BaseView {
    protected B binding;
    protected P presenter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        enterFullscreen();
        binding = createBinding();
        presenter = createPresenter();
        setContentView(binding.getRoot());
        initView(savedInstanceState);
        initData();
    }

    protected abstract B createBinding();

    protected abstract P createPresenter();

    protected void initView(@Nullable Bundle savedInstanceState) {
    }

    protected void initData() {
    }

    @Override
    protected void onResume() {
        super.onResume();
        enterFullscreen();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            enterFullscreen();
        }
    }

    @Override
    protected void onDestroy() {
        if (presenter != null) {
            presenter.detach();
            presenter = null;
        }
        binding = null;
        super.onDestroy();
    }

    @Override
    public void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    protected void enterFullscreen() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }
}
