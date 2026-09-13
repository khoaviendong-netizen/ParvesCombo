package hev.sockstun;

import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.VpnService;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/** One movable overlay containing every Parves Proxy and Quick Copy action. */
public class OverlayService extends Service {
	private static final String CHANNEL = "parves_floating";
	private static final int PANEL_WIDTH_DP = 136;
	private static final int PANEL_ITEM_HEIGHT_DP = 48;
	private static final int PANEL_PADDING_DP = 5;
	private static final int PANEL_ITEM_MARGIN_DP = 2;

	private WindowManager windowManager;
	private WindowManager.LayoutParams params;
	private WindowManager.LayoutParams ipParams;
	private WindowManager.LayoutParams panelParams;
	private TextView button;
	private TextView ipLabel;
	private LinearLayout panel;
	private ScrollView panelScroll;
	private TextView numberAction;
	private TextView proxyAction;
	private Preferences prefs;
	private final Handler handler = new Handler(Looper.getMainLooper());
	private boolean longPressed;
	private boolean moved;
	private float downRawX;
	private float downRawY;
	private int downX;
	private int downY;
	private int ipState;
	private int checkGeneration;
	private long lastTapTime;

	private final Runnable longPressAction = new Runnable() {
		@Override
		public void run() {
			if (!moved) {
				longPressed = true;
				copyAndRemoveNextNumber();
			}
		}
	};

	private final Runnable singleTapAction = new Runnable() {
		@Override
		public void run() {
			lastTapTime = 0L;
			toggleProxy();
		}
	};

	private final SharedPreferences.OnSharedPreferenceChangeListener listener =
			new SharedPreferences.OnSharedPreferenceChangeListener() {
				@Override
				public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
					if (Preferences.ENABLE.equals(key)) {
						if (prefs.getEnable()) checkProxyIp();
						else showIp("OFF", 0);
						refreshPanel();
					}
				}
			};

	@Override
	public void onCreate() {
		super.onCreate();
		if (!QuickCopyStore.isOverlayEnabled(this)) {
			stopSelf();
			return;
		}
		prefs = new Preferences(this);
		prefs.registerOnChange(listener);
		QuickCopyStore.migrateLegacyNamesIfNeeded(this);
		QuickCopyStore.ensureQueue(this);
		startAsForeground();
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
			stopSelf();
			return;
		}
		createFloatingButton();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (!QuickCopyStore.isOverlayEnabled(this)) {
			stopSelf();
			return START_NOT_STICKY;
		}
		if (button == null) return START_NOT_STICKY;
		refreshButton();
		refreshPanel();
		if (prefs.getEnable() && ipState == 0) checkProxyIp();
		return START_STICKY;
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	private void startAsForeground() {
		NotificationManager manager =
				(NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			NotificationChannel channel = new NotificationChannel(CHANNEL,
					getString(R.string.app_name), NotificationManager.IMPORTANCE_LOW);
			channel.setSound(null, null);
			channel.enableVibration(false);
			manager.createNotificationChannel(channel);
		}
		Intent open = new Intent(this, MainActivity.class);
		PendingIntent pending = PendingIntent.getActivity(this, 3, open,
				PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
		Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
				? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
		Notification notification = builder
				.setContentTitle(getString(R.string.app_name))
				.setContentText(getString(R.string.floating_help))
				.setSmallIcon(android.R.drawable.ic_menu_share)
				.setContentIntent(pending)
				.setOngoing(true)
				.setShowWhen(false)
				.build();
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
			startForeground(2, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
		} else {
			startForeground(2, notification);
		}
	}

	private void createFloatingButton() {
		windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
		button = new TextView(this);
		button.setTextColor(Color.WHITE);
		button.setTextSize(18);
		button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
		button.setIncludeFontPadding(false);
		button.setGravity(Gravity.CENTER);
		button.setElevation(dp(6));

		ipLabel = new TextView(this);
		ipLabel.setTextColor(Color.WHITE);
		ipLabel.setTextSize(12);
		ipLabel.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
		ipLabel.setIncludeFontPadding(false);
		ipLabel.setGravity(Gravity.CENTER);
		ipLabel.setSingleLine(true);
		ipLabel.setBackgroundColor(Color.TRANSPARENT);
		ipLabel.setShadowLayer(3f, 0f, 1f, Color.BLACK);

		int size = dp(52);
		int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
				? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
				: WindowManager.LayoutParams.TYPE_PHONE;
		params = new WindowManager.LayoutParams(size, size, type,
				WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
						| WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
				PixelFormat.TRANSLUCENT);
		params.gravity = Gravity.TOP | Gravity.START;
		SharedPreferences position = getSharedPreferences("OverlayPosition", MODE_PRIVATE);
		params.x = position.getInt("x", dp(18));
		params.y = position.getInt("y", dp(180));
		clampButtonPosition();

		ipParams = new WindowManager.LayoutParams(dp(170), dp(24), type,
				WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
						| WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
						| WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
				PixelFormat.TRANSLUCENT);
		ipParams.gravity = Gravity.TOP | Gravity.START;
		updateIpPosition();

		button.setOnTouchListener(new View.OnTouchListener() {
			@Override
			public boolean onTouch(View view, MotionEvent event) {
				switch (event.getActionMasked()) {
					case MotionEvent.ACTION_DOWN:
						downRawX = event.getRawX();
						downRawY = event.getRawY();
						downX = params.x;
						downY = params.y;
						moved = false;
						longPressed = false;
						handler.postDelayed(longPressAction,
								ViewConfiguration.getLongPressTimeout());
						return true;
					case MotionEvent.ACTION_MOVE:
						float dx = event.getRawX() - downRawX;
						float dy = event.getRawY() - downRawY;
						if (!moved && Math.hypot(dx, dy) >
								ViewConfiguration.get(OverlayService.this).getScaledTouchSlop()) {
							moved = true;
							handler.removeCallbacks(longPressAction);
						}
						if (moved) {
							params.x = downX + Math.round(dx);
							params.y = downY + Math.round(dy);
							clampButtonPosition();
							windowManager.updateViewLayout(button, params);
							updateIpPosition();
							windowManager.updateViewLayout(ipLabel, ipParams);
						}
						return true;
					case MotionEvent.ACTION_UP:
						handler.removeCallbacks(longPressAction);
						if (moved) savePosition();
						else if (!longPressed) handleTap();
						return true;
					case MotionEvent.ACTION_CANCEL:
						handler.removeCallbacks(longPressAction);
						return true;
					default:
						return false;
				}
			}
		});

		windowManager.addView(button, params);
		windowManager.addView(ipLabel, ipParams);
		createPanel(type);
		showIp(prefs.getEnable() ? "CHECK…" : "OFF", prefs.getEnable() ? 1 : 0);
		refreshButton();
		if (prefs.getEnable()) checkProxyIp();
	}

	private void handleTap() {
		long now = android.os.SystemClock.uptimeMillis();
		if (lastTapTime > 0L
				&& now - lastTapTime <= ViewConfiguration.getDoubleTapTimeout()) {
			handler.removeCallbacks(singleTapAction);
			lastTapTime = 0L;
			openPanel();
		} else {
			lastTapTime = now;
			handler.postDelayed(singleTapAction, ViewConfiguration.getDoubleTapTimeout());
		}
	}

	private void createPanel(int type) {
		panel = new LinearLayout(this);
		panel.setOrientation(LinearLayout.VERTICAL);
		panel.setPadding(dp(PANEL_PADDING_DP), dp(PANEL_PADDING_DP),
				dp(PANEL_PADDING_DP), dp(PANEL_PADDING_DP));
		panelScroll = new ScrollView(this);
		panelScroll.setBackground(PremiumUi.surface(this, PremiumUi.BG, PremiumUi.BORDER, 16));
		panelScroll.setFillViewport(false);
		panelScroll.setVisibility(View.GONE);
		panelScroll.addView(panel, new ScrollView.LayoutParams(
				ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

		TextView close = compactItem("×", null);
		close.setTextSize(22);
		close.setContentDescription("Close floating controls");
		TextView first = compactItem("First", "users");
		TextView last = compactItem("Last", "users");
		TextView password = compactItem("Pass", "lock");
		TextView twoFactor = compactItem("2FA", "key");
		numberAction = compactItem("Number", "phone");
		proxyAction = compactItem("Proxy", "proxy");
		TextView reset = compactItem("Reset", "reset");

		panel.addView(close);
		panel.addView(first);
		panel.addView(last);
		panel.addView(password);
		panel.addView(twoFactor);
		panel.addView(numberAction);
		panel.addView(proxyAction);
		panel.addView(reset);

		panelParams = new WindowManager.LayoutParams(dp(PANEL_WIDTH_DP),
				WindowManager.LayoutParams.WRAP_CONTENT, type,
				WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
						| WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
				PixelFormat.TRANSLUCENT);
		panelParams.gravity = Gravity.TOP | Gravity.START;
		panelParams.x = dp(4);
		panelParams.y = dp(80);
		windowManager.addView(panelScroll, panelParams);

		close.setOnClickListener(new View.OnClickListener() {
			@Override public void onClick(View view) { closePanel(); }
		});
		first.setOnClickListener(new View.OnClickListener() {
			@Override public void onClick(View view) { copyName(true); }
		});
		last.setOnClickListener(new View.OnClickListener() {
			@Override public void onClick(View view) { copyName(false); }
		});
		password.setOnClickListener(new View.OnClickListener() {
			@Override public void onClick(View view) { copyPassword(); }
		});
		twoFactor.setOnClickListener(new View.OnClickListener() {
			@Override public void onClick(View view) { copyTwoFactorOrSetup(); }
		});
		twoFactor.setOnLongClickListener(new View.OnLongClickListener() {
			@Override public boolean onLongClick(View view) {
				show2FASetup();
				return true;
			}
		});
		numberAction.setOnClickListener(new View.OnClickListener() {
			@Override public void onClick(View view) { copyAndRemoveNextNumber(); }
		});
		proxyAction.setOnClickListener(new View.OnClickListener() {
			@Override public void onClick(View view) {
				toggleProxy();
				refreshPanel();
			}
		});
		reset.setOnClickListener(new View.OnClickListener() {
			@Override public void onClick(View view) {
				int count = QuickCopyStore.resetQueue(OverlayService.this);
				Toast.makeText(OverlayService.this,
						count == 0 ? "No saved names" : "Names reset to start",
						Toast.LENGTH_SHORT).show();
			}
		});
		refreshPanel();
	}

	private TextView compactItem(String text, String iconName) {
		TextView item = new TextView(this);
		item.setText(text);
		item.setTextColor(PremiumUi.TEXT);
		item.setTextSize(12);
		item.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
		item.setIncludeFontPadding(false);
		item.setGravity(iconName == null ? Gravity.CENTER : Gravity.CENTER_VERTICAL | Gravity.START);
		item.setSingleLine(false);
		item.setHorizontallyScrolling(false);
		item.setMinHeight(dp(PANEL_ITEM_HEIGHT_DP));
		item.setPadding(dp(8), dp(10), dp(8), dp(10));
		item.setBackground(PremiumUi.surface(this, PremiumUi.CARD, PremiumUi.BORDER, 10));
		if (iconName != null) {
			Drawable icon = PremiumUi.icon(this, iconName, PremiumUi.TEXT);
			icon.setBounds(0, 0, dp(16), dp(16));
			item.setCompoundDrawablesRelative(icon, null, null, null);
			item.setCompoundDrawablePadding(dp(6));
		}
		LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
		layout.setMargins(0, dp(PANEL_ITEM_MARGIN_DP), 0, dp(PANEL_ITEM_MARGIN_DP));
		item.setLayoutParams(layout);
		return item;
	}

	private void openPanel() {
		QuickCopyStore.ensureQueue(this);
		refreshPanel();
		positionPanelNearButton();
		panelScroll.scrollTo(0, 0);
		button.setVisibility(View.GONE);
		ipLabel.setVisibility(View.GONE);
		panelScroll.setVisibility(View.VISIBLE);
	}

	private void closePanel() {
		panelScroll.setVisibility(View.GONE);
		button.setVisibility(View.VISIBLE);
		ipLabel.setVisibility(View.VISIBLE);
	}

	private void positionPanelNearButton() {
		int screenWidth = getResources().getDisplayMetrics().widthPixels;
		int screenHeight = getResources().getDisplayMetrics().heightPixels;
		int margin = dp(4);
		int contentWidth = 0;
		for (int index = 0; index < panel.getChildCount(); index++) {
			TextView item = (TextView) panel.getChildAt(index);
			// Text paint reflects the user's font scale, including accessibility sizes.
			int itemWidth = (int) Math.ceil(item.getPaint().measureText(item.getText().toString()))
					+ item.getCompoundPaddingLeft() + item.getCompoundPaddingRight();
			contentWidth = Math.max(contentWidth, itemWidth);
		}
		int panelWidth = Math.min(Math.max(1, screenWidth - margin * 2),
				Math.max(dp(PANEL_WIDTH_DP), contentWidth
						+ panel.getPaddingLeft() + panel.getPaddingRight() + dp(6)));
		panel.measure(View.MeasureSpec.makeMeasureSpec(panelWidth, View.MeasureSpec.EXACTLY),
				View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
		int topMargin = dp(26);
		int bottomMargin = dp(28);
		int availableHeight = Math.max(1, screenHeight - topMargin - bottomMargin);
		int panelHeight = Math.min(panel.getMeasuredHeight(), availableHeight);
		panelParams.width = panelWidth;
		panelParams.height = panelHeight;
		int center = params.x + params.width / 2;
		panelParams.x = center < screenWidth / 2
				? margin : Math.max(margin, screenWidth - panelWidth - margin);
		panelParams.y = Math.max(topMargin,
				Math.min(params.y, screenHeight - panelHeight - bottomMargin));
		try {
			windowManager.updateViewLayout(panelScroll, panelParams);
		} catch (Exception ignored) { }
	}

	private void refreshPanel() {
		if (numberAction != null) {
			int count = NumberStore.count(this);
			numberAction.setText(count > 99 ? "Number 99+" : "Number " + count);
		}
		if (proxyAction != null && prefs != null) {
			proxyAction.setText(prefs.getEnable() ? "Proxy ON" : "Proxy OFF");
			proxyAction.setTextColor(prefs.getEnable() ? PremiumUi.CYAN : PremiumUi.TEXT);
			proxyAction.setBackground(PremiumUi.surface(this, PremiumUi.CARD,
					prefs.getEnable() ? PremiumUi.CYAN : PremiumUi.BORDER, 10));
		}
		if (panelScroll != null && panelParams != null
				&& panelScroll.getVisibility() == View.VISIBLE) {
			positionPanelNearButton();
		}
	}

	private void clampButtonPosition() {
		if (params == null) return;
		int maxX = Math.max(0,
				getResources().getDisplayMetrics().widthPixels - params.width);
		int maxY = Math.max(0,
				getResources().getDisplayMetrics().heightPixels - params.height);
		params.x = Math.max(0, Math.min(maxX, params.x));
		params.y = Math.max(0, Math.min(maxY, params.y));
	}

	private void updateIpPosition() {
		if (ipParams == null || params == null) return;
		int screenWidth = getResources().getDisplayMetrics().widthPixels;
		int screenHeight = getResources().getDisplayMetrics().heightPixels;
		int centeredX = params.x - (ipParams.width - params.width) / 2;
		ipParams.x = Math.max(0,
				Math.min(Math.max(0, screenWidth - ipParams.width), centeredX));
		int below = params.y + params.height + dp(2);
		if (below + ipParams.height <= screenHeight) ipParams.y = below;
		else ipParams.y = Math.max(0, params.y - ipParams.height - dp(2));
	}

	private void savePosition() {
		getSharedPreferences("OverlayPosition", MODE_PRIVATE).edit()
				.putInt("x", params.x).putInt("y", params.y).apply();
	}

	private void refreshButton() {
		if (button == null) return;
		int count = NumberStore.count(this);
		button.setText(count > 999 ? "999+" : String.valueOf(count));
		button.setTextSize(count > 999 ? 14 : 18);
		button.setContentDescription(count + " numbers remaining. Tap to toggle proxy, "
				+ "double tap for controls, hold to copy the next number.");
		GradientDrawable background = new GradientDrawable();
		background.setShape(GradientDrawable.OVAL);
		int ringColor;
		if (!prefs.getEnable()) ringColor = Color.parseColor("#43879B");
		else if (ipState == 2) ringColor = PremiumUi.CYAN;
		else if (ipState == 1) ringColor = Color.parseColor("#379FDC");
		else ringColor = PremiumUi.RED;
		background.setColor(PremiumUi.BG);
		background.setStroke(dp(3), ringColor);
		button.setBackground(background);
	}

	private void copyAndRemoveNextNumber() {
		String number = NumberStore.popFirst(this);
		if (number == null) {
			Toast.makeText(this, R.string.no_numbers, Toast.LENGTH_SHORT).show();
			refreshButton();
			refreshPanel();
			return;
		}
		copyToClipboard("number", number);
		int left = NumberStore.count(this);
		Toast.makeText(this, getString(R.string.number_copied, number, left),
				Toast.LENGTH_SHORT).show();
		refreshButton();
		refreshPanel();
	}

	private void copyName(boolean firstName) {
		List<String> queue = QuickCopyStore.ensureQueue(this);
		if (queue.isEmpty()) {
			Toast.makeText(this, "No saved names", Toast.LENGTH_SHORT).show();
			return;
		}
		String[] parts = QuickCopyStore.splitName(queue.get(0));
		if (firstName) {
			copy("First name", parts[0]);
			if (parts[1].isEmpty()) QuickCopyStore.advanceQueue(this, queue);
		} else {
			if (parts[1].isEmpty()) {
				Toast.makeText(this, "This name has no last name", Toast.LENGTH_SHORT).show();
				return;
			}
			copy("Last name", parts[1]);
			QuickCopyStore.advanceQueue(this, queue);
		}
	}

	private void copyPassword() {
		try {
			String password = QuickCopyStore.readPassword(this);
			if (password == null) {
				Toast.makeText(this, "Save password in Quick Copy first",
						Toast.LENGTH_SHORT).show();
			} else {
				copy("Password", password);
			}
		} catch (Exception exception) {
			Toast.makeText(this, "Could not read saved password",
					Toast.LENGTH_SHORT).show();
		}
	}

	private void copyTwoFactorOrSetup() {
		try {
			String secret = QuickCopyStore.readTotpSecret(this);
			if (secret == null || secret.trim().isEmpty()) {
				show2FASetup();
				return;
			}
			copy("2FA code", QuickCopyStore.currentTotp(secret));
		} catch (Exception exception) {
			show2FASetup();
		}
	}

	private void show2FASetup() {
		LinearLayout box = new LinearLayout(this);
		box.setOrientation(LinearLayout.VERTICAL);
		box.setPadding(dp(20), dp(12), dp(20), dp(8));

		final EditText input = new EditText(this);
		input.setHint("Paste Base32 2FA secret");
		input.setSingleLine(true);
		input.setTextColor(PremiumUi.TEXT);
		input.setHintTextColor(PremiumUi.MUTED);
		input.setTextSize(14);
		input.setPadding(dp(12), dp(12), dp(12), dp(12));
		input.setBackground(PremiumUi.surface(this, PremiumUi.CARD, PremiumUi.BORDER, 10));
		input.setInputType(InputType.TYPE_CLASS_TEXT
				| InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
		box.addView(input);

		final AlertDialog dialog = new AlertDialog.Builder(this,
				android.R.style.Theme_Material_Dialog_Alert)
				.setTitle("2FA Setup")
				.setView(box)
				.setNegativeButton("Cancel", null)
				.setNeutralButton("Use Saved", null)
				.setPositiveButton("OK • Save & Copy", null)
				.create();

		if (dialog.getWindow() != null) {
			dialog.getWindow().setType(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
					? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
					: WindowManager.LayoutParams.TYPE_PHONE);
		}
		dialog.setOnShowListener(new DialogInterface.OnShowListener() {
			@Override public void onShow(DialogInterface ignored) {
				if (dialog.getWindow() != null) {
					dialog.getWindow().setBackgroundDrawable(
							PremiumUi.surface(OverlayService.this,
									PremiumUi.BG, PremiumUi.BORDER, 16));
				}
				dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(PremiumUi.CYAN);
				dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(PremiumUi.CYAN);
				dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(PremiumUi.MUTED);
				dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(
						new View.OnClickListener() {
							@Override public void onClick(View view) {
								try {
									String secret = QuickCopyStore.normalizeTotpSecret(
											input.getText().toString());
									if (secret.isEmpty()) {
										input.setError("Paste a Base32 secret");
										return;
									}
									String code = QuickCopyStore.currentTotp(secret);
									QuickCopyStore.saveTotpSecret(OverlayService.this, secret);
									copy("2FA code", code);
									dialog.dismiss();
								} catch (Exception exception) {
									input.setError("Invalid Base32 secret");
								}
							}
						});
				dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(
						new View.OnClickListener() {
							@Override public void onClick(View view) {
								try {
									String secret = QuickCopyStore.readTotpSecret(OverlayService.this);
									if (secret == null || secret.trim().isEmpty()) {
										input.setError("No saved secret");
										return;
									}
									copy("2FA code", QuickCopyStore.currentTotp(secret));
									dialog.dismiss();
								} catch (Exception exception) {
									input.setError("Could not use saved secret");
								}
							}
						});
			}
		});
		dialog.show();
	}

	private void copy(String label, String value) {
		copyToClipboard(label, value);
		Toast.makeText(this, label + " copied", Toast.LENGTH_SHORT).show();
	}

	private void copyToClipboard(String label, String value) {
		ClipboardManager clipboard =
				(ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
		clipboard.setPrimaryClip(ClipData.newPlainText(label, value));
	}

	private void toggleProxy() {
		if (prefs.getEnable()) {
			checkGeneration++;
			prefs.setEnable(false);
			startService(new Intent(this, TProxyService.class)
					.setAction(TProxyService.ACTION_DISCONNECT));
			Toast.makeText(this, R.string.proxy_disconnected, Toast.LENGTH_SHORT).show();
			showIp("OFF", 0);
			return;
		}
		if (prefs.getSocksAddress().trim().isEmpty() || prefs.getSocksPort() < 1
				|| prefs.getSocksPort() > 65535) {
			Toast.makeText(this, R.string.enter_proxy, Toast.LENGTH_SHORT).show();
			openMain();
			return;
		}
		if (prefs.getApps().isEmpty()) {
			Toast.makeText(this, R.string.select_app_first, Toast.LENGTH_SHORT).show();
			openMain();
			return;
		}
		if (VpnService.prepare(this) != null) {
			Toast.makeText(this, R.string.open_app_permission, Toast.LENGTH_LONG).show();
			openMain();
			return;
		}
		prefs.setEnable(true);
		Intent intent = new Intent(this, TProxyService.class)
				.setAction(TProxyService.ACTION_CONNECT);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent);
		else startService(intent);
		Toast.makeText(this, R.string.proxy_connected, Toast.LENGTH_SHORT).show();
		checkProxyIp();
	}

	private void checkProxyIp() {
		if (ipLabel == null || !prefs.getEnable()) return;
		final int generation = ++checkGeneration;
		showIp("CHECK…", 1);
		ProxyIpChecker.check(prefs.getSocksAddress(), prefs.getSocksPort(),
				prefs.getSocksUsername(), prefs.getSocksPassword(),
				new ProxyIpChecker.Callback() {
					@Override
					public void onResult(ProxyIpChecker.Status status, String ip) {
						if (generation != checkGeneration || !prefs.getEnable()) return;
						if (status == ProxyIpChecker.Status.OK) showIp(ip, 2);
						else if (status == ProxyIpChecker.Status.AUTH_FAIL)
							showIp("AUTH FAIL", 3);
						else if (status == ProxyIpChecker.Status.TIMEOUT)
							showIp("TIMEOUT", 3);
						else showIp("NO DATA", 3);
					}
				});
	}

	private void showIp(String text, int state) {
		ipState = state;
		if (ipLabel != null) ipLabel.setText(text);
		refreshButton();
		refreshPanel();
	}

	private void openMain() {
		Intent intent = new Intent(this, MainActivity.class)
				.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		startActivity(intent);
	}

	private int dp(int value) {
		return Math.round(value * getResources().getDisplayMetrics().density);
	}

	@Override
	public void onDestroy() {
		handler.removeCallbacksAndMessages(null);
		if (prefs != null) prefs.unregisterOnChange(listener);
		if (windowManager != null && button != null) {
			try { windowManager.removeView(button); } catch (Exception ignored) { }
		}
		if (windowManager != null && ipLabel != null) {
			try { windowManager.removeView(ipLabel); } catch (Exception ignored) { }
		}
		if (windowManager != null && panelScroll != null) {
			try { windowManager.removeView(panelScroll); } catch (Exception ignored) { }
		}
		super.onDestroy();
	}
}
