package top.eiyooooo.easycontrol.app;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

import java.util.UUID;

import android.view.animation.LinearInterpolator;

import top.eiyooooo.easycontrol.app.client.Client;
import top.eiyooooo.easycontrol.app.databinding.ActivityMainBinding;
import top.eiyooooo.easycontrol.app.databinding.ItemRequestPermissionBinding;
import top.eiyooooo.easycontrol.app.entity.AppData;
import top.eiyooooo.easycontrol.app.entity.Device;
import top.eiyooooo.easycontrol.app.helper.DeviceListAdapter;
import top.eiyooooo.easycontrol.app.helper.PublicTools;
import top.eiyooooo.easycontrol.app.helper.ConnectHelper;

public class MainActivity extends Activity {
  // 设备列表
  private DeviceListAdapter deviceListAdapter;
  private ConnectHelper connectHelper;

  // 创建界面
  private ActivityMainBinding mainActivity;

  @SuppressLint("SourceLockedOrientationActivity")
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    AppData.init(this);
    PublicTools.setStatusAndNavBar(this);
    PublicTools.setLocale(this);
    mainActivity = ActivityMainBinding.inflate(this.getLayoutInflater());
    setContentView(mainActivity.getRoot());
    // 检测权限
    if (AppData.setting.getAlwaysFullMode() || haveOverlayPermission()) startApp();
    else createAlert();
  }

  private void startApp() {
    // 设置设备列表适配器、广播接收器
    deviceListAdapter = new DeviceListAdapter(this, mainActivity.devicesList);
    mainActivity.devicesList.setAdapter(deviceListAdapter);
    AppData.myBroadcastReceiver.setDeviceListAdapter(deviceListAdapter);
    connectHelper = new ConnectHelper(this);
    AppData.myBroadcastReceiver.setConnectHelper(connectHelper);
    ConnectHelper.status = true;
    // 设置按钮监听
    setButtonListener();
    // 首次使用显示使用说明
    if (!AppData.setting.getShowUsage()) {
      AppData.setting.setShowUsage(true);
      AppData.uiHandler.postDelayed(() -> PublicTools.openWebViewActivity(this, "file:///android_asset/usage.html"), 1500);
    }
    if (!Client.allClient.isEmpty()) {
      for (Client client : Client.allClient) {
        if (client.clientView.viewMode == 3) {
          client.clientView.changeToFull();
          break;
        }
      }
    }
  }

  @Override
  protected void onDestroy() {
    AppData.uiHandler.removeCallbacks(connectHelper.showStartDefaultUSB);
    AppData.myBroadcastReceiver.setDeviceListAdapter(null);
    AppData.myBroadcastReceiver.setConnectHelper(null);
    ConnectHelper.status = false;
    super.onDestroy();
  }

  @Override
  protected void onPause() {
    AppData.uiHandler.removeCallbacks(connectHelper.showStartDefaultUSB);
    ConnectHelper.status = false;
    super.onPause();
  }

  @Override
  protected void onResume() {
    ConnectHelper.status = true;
    if (!AppData.setting.getAlwaysFullMode() && !haveOverlayPermission()) createAlert();
    else {
      if (!Client.allClient.isEmpty()) {
        for (Client client : Client.allClient) {
          if (client.clientView.viewMode == 3) {
            client.clientView.changeToFull();
            break;
          }
        }
      }
    }
    super.onResume();
  }

  // 检查权限
  private boolean haveOverlayPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) return Settings.canDrawOverlays(this);
    else return PublicTools.checkOpNoThrow(this, "OP_SYSTEM_ALERT_WINDOW", 24);
  }

  // 创建无权限提示
  private void createAlert() {
    ItemRequestPermissionBinding requestPermissionView = ItemRequestPermissionBinding.inflate(LayoutInflater.from(this));
    requestPermissionView.buttonGoToSet.setOnClickListener(v -> startActivity(PublicTools.getOverlayPermissionIntent(this)));
    requestPermissionView.buttonAlwaysFullMode.setOnClickListener(v -> AppData.setting.setAlwaysFullMode(true));
    Dialog dialog = PublicTools.createDialog(this, false, requestPermissionView.getRoot());
    dialog.setOnCancelListener(dialog1 -> {
      if (!AppData.setting.getAlwaysFullMode() && !haveOverlayPermission()) dialog.show();
    });
    dialog.show();
    checkPermissionDelay(dialog);
  }

  // 定时检查
  private void checkPermissionDelay(Dialog dialog) {
    // 因为某些设备可能会无法进入设置或其他问题，导致不会有返回结果，为了减少不确定性，使用定时检测的方法
    AppData.uiHandler.postDelayed(() -> {
      if (AppData.setting.getAlwaysFullMode() || haveOverlayPermission()) {
        dialog.cancel();
        startApp();
      } else checkPermissionDelay(dialog);
    }, 1000);
  }

  // 设置按钮监听
  private void setButtonListener() {
    mainActivity.buttonRefresh.setOnClickListener(v -> {
      mainActivity.buttonRefresh.setClickable(false);
      deviceListAdapter.update();

      ObjectAnimator rotation = ObjectAnimator.ofFloat(mainActivity.buttonRefresh, "rotation", 0f, 360f);
      rotation.setDuration(800);
      rotation.setInterpolator(new LinearInterpolator());
      rotation.addListener(new AnimatorListenerAdapter() {
        @Override
        public void onAnimationEnd(Animator animation) {
          super.onAnimationEnd(animation);
          if (deviceListAdapter.checkConnectionExecutor != null) rotation.start();
          else mainActivity.buttonRefresh.setClickable(true);
        }
      });
      rotation.start();
    });
    mainActivity.buttonPair.setOnClickListener(v -> startActivity(new Intent(this, PairActivity.class)));
    mainActivity.buttonAdd.setOnClickListener(v -> PublicTools.createAddDeviceView(this, Device.getDefaultDevice(UUID.randomUUID().toString(), Device.TYPE_NORMAL), deviceListAdapter).show());
    mainActivity.buttonSet.setOnClickListener(v -> startActivity(new Intent(this, SetActivity.class)));
    // 多选按钮
    mainActivity.buttonMultiSelect.setOnClickListener(v -> enterMultiSelectMode());
    mainActivity.buttonSelectAll.setOnClickListener(v -> {
      if (deviceListAdapter.isAllSelected()) deviceListAdapter.deselectAll();
      else deviceListAdapter.selectAll();
      updateMultiSelectTitle();
    });
    mainActivity.buttonDeleteSelected.setOnClickListener(v -> {
      int count = deviceListAdapter.getSelectedCount();
      if (count == 0) {
        Toast.makeText(this, getString(R.string.multi_select_empty), Toast.LENGTH_SHORT).show();
        return;
      }
      android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
      builder.setMessage(getString(R.string.multi_select_confirm_delete, count));
      builder.setPositiveButton(android.R.string.ok, (d, w) -> {
        deviceListAdapter.deleteSelected();
        exitMultiSelectMode();
      });
      builder.setNegativeButton(android.R.string.cancel, null);
      builder.show();
    });
    mainActivity.buttonMultiSelectDone.setOnClickListener(v -> exitMultiSelectMode());
  }

  private void enterMultiSelectMode() {
    deviceListAdapter.onSelectionChanged = this::updateMultiSelectTitle;
    deviceListAdapter.enterMultiSelectMode();
    mainActivity.multiSelectBar.setVisibility(View.VISIBLE);
    mainActivity.buttonRefresh.setVisibility(View.GONE);
    mainActivity.buttonPair.setVisibility(View.GONE);
    mainActivity.buttonAdd.setVisibility(View.GONE);
    mainActivity.buttonSet.setVisibility(View.GONE);
    mainActivity.buttonMultiSelect.setVisibility(View.GONE);
    mainActivity.mainSubtitle.setVisibility(View.GONE);
    updateMultiSelectTitle();
  }

  private void exitMultiSelectMode() {
    deviceListAdapter.onSelectionChanged = null;
    deviceListAdapter.exitMultiSelectMode();
    mainActivity.multiSelectBar.setVisibility(View.GONE);
    mainActivity.buttonRefresh.setVisibility(View.VISIBLE);
    mainActivity.buttonPair.setVisibility(View.VISIBLE);
    mainActivity.buttonAdd.setVisibility(View.VISIBLE);
    mainActivity.buttonSet.setVisibility(View.VISIBLE);
    mainActivity.buttonMultiSelect.setVisibility(View.VISIBLE);
    mainActivity.mainSubtitle.setVisibility(View.VISIBLE);
    mainActivity.mainTitle.setText(R.string.app_name);
  }

  private void updateMultiSelectTitle() {
    int count = deviceListAdapter.getSelectedCount();
    mainActivity.mainTitle.setText(getString(R.string.multi_select_title, count));
    if (deviceListAdapter.isAllSelected())
      mainActivity.buttonSelectAll.setText(R.string.multi_select_deselect_all);
    else
      mainActivity.buttonSelectAll.setText(R.string.multi_select_select_all);
  }
}