package hev.sockstun;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity implements View.OnClickListener {
	private static final int REQUEST_VPN = 20;
	private static final int REQUEST_OVERLAY = 21;

	private Preferences prefs;
	private EditText address;
	private EditText port;
	private EditText username;
	private EditText password;
	private EditText dns;
	private Button control;
	private Button profilePrev;
	private Button profileNext;
	private Button profileMenu;
	private TextView profileName;
	private TextView remainingBadge;
	private boolean pendingConnect;

	private final SharedPreferences.OnSharedPreferenceChangeListener prefsListener =
			new SharedPreferences.OnSharedPreferenceChangeListener() {
				@Override
				public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
					if (Preferences.ENABLE.equals(key)) updateControlState();
				}
			};

	@Override
	public void onCreate(Bundle state) {
		super.onCreate(state);
		PremiumUi.setupWindow(this);
		prefs = new Preferences(this);
		setContentView(R.layout.main);
		((LinearLayout) findViewById(R.id.premium_nav_host)).addView(PremiumUi.bottomNav(this, "Proxies"));

		address = findViewById(R.id.socks_addr);
		port = findViewById(R.id.socks_port);
		username = findViewById(R.id.socks_user);
		password = findViewById(R.id.socks_pass);
		dns = findViewById(R.id.dns_ipv4);
		control = findViewById(R.id.control);
		profilePrev = findViewById(R.id.profile_prev);
		profileNext = findViewById(R.id.profile_next);
		profileMenu = findViewById(R.id.profile_menu);
		profileName = findViewById(R.id.profile_name);
		remainingBadge = findViewById(R.id.remaining_badge);
		stylePremiumScreen();

		profilePrev.setOnClickListener(this);
		profileNext.setOnClickListener(this);
		profileMenu.setOnClickListener(this);
		findViewById(R.id.save).setOnClickListener(this);
		control.setOnClickListener(this);
		findViewById(R.id.tab_numbers).setOnClickListener(this);
		findViewById(R.id.tab_apps).setOnClickListener(this);
		findViewById(R.id.tab_quick_copy).setOnClickListener(this);

		updateUi();
		requestInitialPermissions();
	}

	private void stylePremiumScreen() {
		EditText[] inputs = {address, port, dns, username, password};
		String[] icons = {"server", "port", "dns", "users", "lock"};
		for (int i = 0; i < inputs.length; i++) {
			PremiumUi.styleInput(inputs[i]);
			inputs[i].setCompoundDrawablesRelative(PremiumUi.icon(this, icons[i], PremiumUi.MUTED), null, null, null);
		}
		for (Button button : new Button[] {profilePrev, profileNext, profileMenu}) {
			PremiumUi.styleButton(button, null, false, false);
			button.setMinHeight(PremiumUi.dp(this, 48));
			button.setMinimumHeight(PremiumUi.dp(this, 48));
			button.setPadding(PremiumUi.dp(this, 14), 0, PremiumUi.dp(this, 14), 0);
			button.setText("");
			String iconName = button == profilePrev ? "chevron_left" : button == profileNext ? "chevron" : "menu";
			button.setCompoundDrawables(PremiumUi.icon(this, iconName, PremiumUi.MUTED), null, null, null);
			button.setCompoundDrawablePadding(0);
			button.setBackground(new android.graphics.drawable.RippleDrawable(
					android.content.res.ColorStateList.valueOf(0x336FDFF1),
					PremiumUi.surface(this, PremiumUi.CARD, Color.TRANSPARENT, 10), null));
		}
		Button save = findViewById(R.id.save);
		PremiumUi.styleButton(save, "save", false, false);
		save.setTextColor(PremiumUi.CYAN);
		save.setCompoundDrawablesRelative(PremiumUi.icon(this, "save", PremiumUi.CYAN), null, null, null);
		save.setBackground(new android.graphics.drawable.RippleDrawable(
				android.content.res.ColorStateList.valueOf(0x336FDFF1),
				PremiumUi.surface(this, PremiumUi.CARD, PremiumUi.CYAN, 12), null));
	}

	@Override
	protected void onStart() {
		super.onStart();
		prefs.registerOnChange(prefsListener);
	}

	@Override
	protected void onResume() {
		super.onResume();
		updateControlState();
		updateRemaining();
		if (QuickCopyStore.isOverlayEnabled(this)
				&& (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this))) {
			startOverlay();
		}
	}

	@Override
	protected void onStop() {
		prefs.unregisterOnChange(prefsListener);
		super.onStop();
	}

	@Override
	public void onClick(View view) {
		int id = view.getId();
		if (id == R.id.profile_prev) {
			switchProfile(-1);
		} else if (id == R.id.profile_next) {
			switchProfile(1);
		} else if (id == R.id.profile_menu) {
			if (!prefs.getEnable()) showProfileMenu();
		} else if (id == R.id.save) {
			if (saveProxy()) Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show();
		} else if (id == R.id.control) {
			toggleProxy();
		} else if (id == R.id.tab_numbers) {
			startActivity(new Intent(this, NumbersActivity.class));
		} else if (id == R.id.tab_apps) {
			startActivity(new Intent(this, AppListActivity.class));
		} else if (id == R.id.tab_quick_copy) {
			startActivity(new Intent(this, QuickCopyActivity.class));
		}
	}

	private void requestInitialPermissions() {
		Intent vpnIntent = VpnService.prepare(this);
		if (vpnIntent != null) {
			startActivityForResult(vpnIntent, REQUEST_VPN);
		} else {
			requestOverlayPermission();
		}
	}

	private void requestOverlayPermission() {
		if (!QuickCopyStore.isOverlayEnabled(this)) return;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
			Toast.makeText(this, R.string.floating_permission, Toast.LENGTH_LONG).show();
			Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
					Uri.parse("package:" + getPackageName()));
			startActivityForResult(intent, REQUEST_OVERLAY);
		} else {
			startOverlay();
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == REQUEST_VPN) {
			if (resultCode == RESULT_OK && pendingConnect) {
				pendingConnect = false;
				connectProxy();
			}
			requestOverlayPermission();
		} else if (requestCode == REQUEST_OVERLAY) {
			if (QuickCopyStore.isOverlayEnabled(this)
					&& (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this))) {
				startOverlay();
			}
		}
	}

	private void startOverlay() {
		if (!QuickCopyStore.isOverlayEnabled(this)) return;
		Intent intent = new Intent(this, OverlayService.class);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent);
		else startService(intent);
	}

	private void toggleProxy() {
		if (prefs.getEnable()) {
			prefs.setEnable(false);
			startVpnService(TProxyService.ACTION_DISCONNECT);
			updateControlState();
			return;
		}
		if (!saveProxy()) return;
		if (prefs.getApps().isEmpty()) {
			Toast.makeText(this, R.string.select_app_first, Toast.LENGTH_LONG).show();
			startActivity(new Intent(this, AppListActivity.class));
			return;
		}
		Intent prepare = VpnService.prepare(this);
		if (prepare != null) {
			pendingConnect = true;
			startActivityForResult(prepare, REQUEST_VPN);
			return;
		}
		connectProxy();
	}

	private void connectProxy() {
		prefs.setEnable(true);
		startVpnService(TProxyService.ACTION_CONNECT);
		updateControlState();
	}

	private void startVpnService(String action) {
		Intent intent = new Intent(this, TProxyService.class).setAction(action);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && TProxyService.ACTION_CONNECT.equals(action)) {
			startForegroundService(intent);
		} else {
			startService(intent);
		}
	}

	private boolean saveProxy() {
		String host = address.getText().toString().trim();
		int parsedPort;
		try {
			parsedPort = Integer.parseInt(port.getText().toString().trim());
		} catch (NumberFormatException exception) {
			parsedPort = -1;
		}
		if (host.isEmpty() || parsedPort < 1 || parsedPort > 65535) {
			Toast.makeText(this, R.string.enter_proxy, Toast.LENGTH_LONG).show();
			return false;
		}
		prefs.setSocksAddress(host);
		prefs.setSocksPort(parsedPort);
		prefs.setSocksUsername(username.getText().toString());
		prefs.setSocksPassword(password.getText().toString());
		prefs.setDnsIpv4(dns.getText().toString().trim());
		prefs.setRemoteDns(true);
		prefs.setIpv4(true);
		prefs.setIpv6(false);
		prefs.setGlobal(false);
		prefs.setUdpInTcp(false);
		return true;
	}

	private void updateUi() {
		address.setText(prefs.getSocksAddress());
		port.setText(String.valueOf(prefs.getSocksPort()));
		username.setText(prefs.getSocksUsername());
		password.setText(prefs.getSocksPassword());
		dns.setText(prefs.getDnsIpv4());
		profileName.setText(prefs.getProfileName());
		updateControlState();
		updateRemaining();
	}

	private void updateRemaining() {
		remainingBadge.setText(getString(R.string.remaining, NumberStore.count(this)));
	}

	private void updateControlState() {
		boolean connected = prefs.getEnable();
		control.setText(connected ? "Stop Proxy" : "Enable Proxy");
		PremiumUi.styleButton(control, "power", true, connected);
		TextView status = findViewById(R.id.premium_proxy_state);
		status.setText(connected ? "●  ON" : "●  OFF");
		status.setTextColor(connected ? PremiumUi.CYAN : PremiumUi.MUTED);
		status.setContentDescription(connected ? "Proxy enabled. Connection is checked in the floating control." : "Proxy disabled");
		boolean editable = !connected;
		address.setEnabled(editable);
		port.setEnabled(editable);
		username.setEnabled(editable);
		password.setEnabled(editable);
		dns.setEnabled(editable);
		profilePrev.setEnabled(editable);
		profileNext.setEnabled(editable);
		profileMenu.setEnabled(editable);
		profilePrev.setAlpha(editable ? 1f : .45f);
		profileNext.setAlpha(editable ? 1f : .45f);
		profileMenu.setAlpha(editable ? 1f : .45f);
	}

	private void switchProfile(int direction) {
		if (prefs.getEnable()) return;
		int count = prefs.getProfileCount();
		prefs.setSelected((prefs.getSelected() + direction + count) % count);
		updateUi();
	}

	private void showProfileMenu() {
		String[] items = {getString(R.string.profile_rename), getString(R.string.profile_add),
				getString(R.string.profile_delete)};
		new AlertDialog.Builder(this)
				.setTitle(prefs.getProfileName())
				.setItems(items, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						if (which == 0) showProfileNameDialog(false);
						else if (which == 1) showProfileNameDialog(true);
						else deleteProfile();
					}
				}).show();
	}

	private void showProfileNameDialog(final boolean add) {
		if (add && prefs.getProfileCount() >= Preferences.MAX_PROFILES) {
			Toast.makeText(this, R.string.profile_limit, Toast.LENGTH_SHORT).show();
			return;
		}
		final EditText input = new EditText(this);
		PremiumUi.styleInput(input);
		input.setText(add ? "" : prefs.getProfileName());
		input.setHint(R.string.profile_name);
		input.setSingleLine(true);
		FrameLayout container = new FrameLayout(this);
		int padding = (int) (18 * getResources().getDisplayMetrics().density);
		container.setPadding(padding, 0, padding, 0);
		container.addView(input);
		new AlertDialog.Builder(this)
				.setTitle(add ? R.string.profile_add : R.string.profile_rename)
				.setView(container)
				.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						String name = input.getText().toString().trim();
						if (name.isEmpty()) return;
						if (add) prefs.addProfile(name); else prefs.setProfileName(name);
						updateUi();
					}
				})
				.setNegativeButton(android.R.string.cancel, null)
				.show();
	}

	private void deleteProfile() {
		if (prefs.getProfileCount() <= 1) {
			Toast.makeText(this, R.string.profile_last, Toast.LENGTH_SHORT).show();
			return;
		}
		new AlertDialog.Builder(this)
				.setMessage(getString(R.string.profile_delete_confirm, prefs.getProfileName()))
				.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						prefs.deleteProfile();
						updateUi();
					}
				})
				.setNegativeButton(android.R.string.cancel, null)
				.show();
	}
}
