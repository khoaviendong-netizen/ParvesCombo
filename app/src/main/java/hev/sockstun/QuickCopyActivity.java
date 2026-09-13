package hev.sockstun;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** Full Quick Copy manager retained inside the combined app. */
public class QuickCopyActivity extends Activity {
	private TextView status;

	private int dp(int value) {
		return Math.round(value * getResources().getDisplayMetrics().density);
	}

	@Override
	protected void onCreate(Bundle state) {
		super.onCreate(state);
		PremiumUi.setupWindow(this);
		QuickCopyStore.migrateLegacyNamesIfNeeded(this);

		LinearLayout screen = new LinearLayout(this);
		screen.setOrientation(LinearLayout.VERTICAL);
		screen.setBackgroundColor(PremiumUi.BG);

		LinearLayout header = new LinearLayout(this);
		header.setGravity(Gravity.CENTER_VERTICAL);
		header.setPadding(dp(12), dp(8), dp(18), dp(8));
		Button headerBack = new Button(this);
		PremiumUi.styleButton(headerBack, "back", false, false);
		headerBack.setText("");
		headerBack.setContentDescription("Back to proxy");
		headerBack.setPadding(dp(14), 0, dp(14), 0);
		headerBack.setBackgroundColor(PremiumUi.BG);
		header.addView(headerBack, new LinearLayout.LayoutParams(dp(48), dp(48)));
		TextView title = new TextView(this);
		title.setText(R.string.quick_copy);
		title.setTextSize(23);
		title.setTextColor(PremiumUi.TEXT);
		title.setTypeface(null, android.graphics.Typeface.BOLD);
		LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
				0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
		titleParams.setMargins(dp(8), 0, 0, 0);
		header.addView(title, titleParams);
		screen.addView(header);

		LinearLayout root = new LinearLayout(this);
		root.setOrientation(LinearLayout.VERTICAL);
		root.setPadding(dp(16), dp(6), dp(16), dp(18));
		root.setBackgroundColor(PremiumUi.BG);

		LinearLayout summary = new LinearLayout(this);
		summary.setOrientation(LinearLayout.HORIZONTAL);
		summary.setGravity(Gravity.CENTER_VERTICAL);
		summary.setPadding(dp(16), dp(14), dp(16), dp(14));
		summary.setMinimumHeight(dp(96));
		summary.setBackground(PremiumUi.surface(this, PremiumUi.CARD, PremiumUi.BORDER, 14));
		ImageView people = new ImageView(this);
		people.setImageDrawable(PremiumUi.icon(this, "users", PremiumUi.CYAN));
		people.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
		summary.addView(people, new LinearLayout.LayoutParams(dp(32), dp(32)));

		status = new TextView(this);
		status.setTextColor(PremiumUi.TEXT);
		status.setTextSize(15);
		status.setLineSpacing(dp(3), 1f);
		LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
				0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
		statusParams.setMargins(dp(14), 0, 0, 0);
		summary.addView(status, statusParams);
		LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
		summaryParams.setMargins(0, 0, 0, dp(12));
		root.addView(summary, summaryParams);

		Button names = addButton(root, "Manage Names / Bulk Paste", "users", PremiumUi.TEXT);
		Button password = addButton(root, "Save / Change Password", "lock", PremiumUi.TEXT);
		Button twoFactor = addButton(root, "Save / Change 2FA Secret", "key", PremiumUi.TEXT);
		Button reset = addButton(root, "Reset Names To Start", "reset", PremiumUi.TEXT);
		Button enable = addButton(root, "Enable Floating Button", "overlay", PremiumUi.CYAN);
		Button stop = addButton(root, "Stop Floating Button", "stop", PremiumUi.RED);
		Button back = addButton(root, "Back To Proxy", "back", PremiumUi.TEXT);

		ScrollView scroll = new ScrollView(this);
		scroll.setFillViewport(true);
		scroll.setClipToPadding(false);
		scroll.addView(root);
		screen.addView(scroll, new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
		screen.addView(PremiumUi.bottomNav(this, "Quick Copy"));
		setContentView(screen);
		headerBack.setOnClickListener(new View.OnClickListener() {
			@Override public void onClick(View view) { finish(); }
		});

		names.setOnClickListener(new View.OnClickListener() {
			@Override public void onClick(View view) { showNames(); }
		});
		password.setOnClickListener(new View.OnClickListener() {
			@Override public void onClick(View view) { showSecret(false); }
		});
		twoFactor.setOnClickListener(new View.OnClickListener() {
			@Override public void onClick(View view) { showSecret(true); }
		});
		reset.setOnClickListener(new View.OnClickListener() {
			@Override public void onClick(View view) { resetQueueToStart(); }
		});
		enable.setOnClickListener(new View.OnClickListener() {
			@Override public void onClick(View view) { enableOverlay(); }
		});
		stop.setOnClickListener(new View.OnClickListener() {
			@Override public void onClick(View view) {
				QuickCopyStore.setOverlayEnabled(QuickCopyActivity.this, false);
				stopService(new Intent(QuickCopyActivity.this, OverlayService.class));
				updateStatus();
			}
		});
		back.setOnClickListener(new View.OnClickListener() {
			@Override public void onClick(View view) { finish(); }
		});
		updateStatus();
	}

	private Button addButton(LinearLayout root, String text, String iconName, int color) {
		Button button = new Button(this);
		button.setText(text);
		PremiumUi.styleButton(button, iconName, false, color == PremiumUi.RED);
		button.setTextColor(color);
		button.setTextSize(15);
		button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
		button.setPadding(dp(16), dp(14), dp(16), dp(14));
		button.setCompoundDrawablesRelative(
				PremiumUi.icon(this, iconName, color), null,
				PremiumUi.icon(this, "chevron", color == PremiumUi.TEXT ? PremiumUi.MUTED : color), null);
		button.setCompoundDrawablePadding(dp(14));
		button.setMinHeight(dp(60));
		LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
		params.setMargins(0, 0, 0, dp(8));
		root.addView(button, params);
		return button;
	}

	@Override
	protected void onResume() {
		super.onResume();
		QuickCopyStore.migrateLegacyNamesIfNeeded(this);
		if (QuickCopyStore.isOverlayEnabled(this)
				&& (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this))) {
			startOverlayNow();
		}
		updateStatus();
	}

	private void updateStatus() {
		List<String> master = QuickCopyStore.getMaster(this);
		List<String> queue = QuickCopyStore.ensureQueue(this);
		String current = queue.isEmpty() ? "None" : queue.get(0);
		status.setText("Saved names: " + master.size()
				+ " • Remaining this round: " + queue.size()
				+ "\nCurrent: " + current
				+ "\nFloating: "
				+ (QuickCopyStore.isOverlayEnabled(this) ? "ON" : "OFF"));
	}

	private void resetQueueToStart() {
		int size = QuickCopyStore.resetQueue(this);
		updateStatus();
		Toast.makeText(this, size == 0 ? "No saved names" : "Names reset to start",
				Toast.LENGTH_SHORT).show();
	}

	private void enableOverlay() {
		QuickCopyStore.setOverlayEnabled(this, true);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
			startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
					Uri.parse("package:" + getPackageName())));
			updateStatus();
			return;
		}
		startOverlayNow();
		updateStatus();
	}

	private void startOverlayNow() {
		Intent intent = new Intent(this, OverlayService.class);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent);
		else startService(intent);
	}

	private void showNames() {
		LinearLayout box = new LinearLayout(this);
		box.setOrientation(LinearLayout.VERTICAL);
		box.setPadding(dp(18), dp(8), dp(18), 0);

		final EditText input = new EditText(this);
		input.setHint("Paste all names here — one full name per line");
		PremiumUi.styleInput(input);
		input.setMinLines(6);
		input.setMaxLines(10);
		input.setGravity(Gravity.TOP);
		input.setInputType(InputType.TYPE_CLASS_TEXT
				| InputType.TYPE_TEXT_FLAG_MULTI_LINE
				| InputType.TYPE_TEXT_FLAG_CAP_WORDS);
		input.setText(QuickCopyStore.getMasterText(this));

		final TextView info = new TextView(this);
		info.setTextColor(PremiumUi.MUTED);
		info.setTextSize(14);
		info.setPadding(0, dp(8), 0, 0);
		box.addView(input);
		box.addView(info);
		refreshNameInfo(info);
		ScrollView form = new ScrollView(this);
		form.addView(box);

		final AlertDialog dialog = new AlertDialog.Builder(this)
				.setTitle("Bulk Names")
				.setView(form)
				.setNegativeButton("Close", null)
				.setNeutralButton("Clear All", null)
				.setPositiveButton("SAVE ALL", null)
				.create();

		dialog.setOnShowListener(new DialogInterface.OnShowListener() {
			@Override public void onShow(DialogInterface ignored) {
				dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(
						new View.OnClickListener() {
							@Override public void onClick(View view) {
								List<String> names = QuickCopyStore.parseNames(input.getText().toString());
								QuickCopyStore.saveMasterAndResetQueue(QuickCopyActivity.this, names);
								input.setText(QuickCopyStore.getMasterText(QuickCopyActivity.this));
								input.setSelection(input.getText().length());
								refreshNameInfo(info);
								updateStatus();
								Toast.makeText(QuickCopyActivity.this,
										names.size() + " names saved", Toast.LENGTH_SHORT).show();
							}
						});
				dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(
						new View.OnClickListener() {
							@Override public void onClick(View view) {
								QuickCopyStore.saveMasterAndResetQueue(QuickCopyActivity.this,
										new ArrayList<String>());
								input.setText("");
								refreshNameInfo(info);
								updateStatus();
							}
						});
			}
		});
		dialog.show();
	}

	private void refreshNameInfo(TextView info) {
		info.setText("Master: " + QuickCopyStore.getMaster(this).size()
				+ " • Remaining: " + QuickCopyStore.getQueue(this).size()
				+ "\nWhen the round finishes, the master list starts again automatically.");
	}

	private void showSecret(final boolean isTotp) {
		final EditText input = new EditText(this);
		PremiumUi.styleInput(input);
		input.setHint(isTotp ? "2FA Base32 Secret" : "Password");
		input.setSingleLine(true);
		input.setInputType(InputType.TYPE_CLASS_TEXT
				| (isTotp ? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
				: InputType.TYPE_TEXT_VARIATION_PASSWORD));
		LinearLayout form = new LinearLayout(this);
		form.setPadding(dp(18), dp(8), dp(18), dp(8));
		form.addView(input, new LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

		new AlertDialog.Builder(this)
				.setTitle(isTotp ? "2FA Base32 Secret" : "Password")
				.setView(form)
				.setNegativeButton("Cancel", null)
				.setNeutralButton("Delete Saved", new DialogInterface.OnClickListener() {
					@Override public void onClick(DialogInterface dialog, int which) {
						if (isTotp) QuickCopyStore.deleteTotpSecret(QuickCopyActivity.this);
						else QuickCopyStore.deletePassword(QuickCopyActivity.this);
					}
				})
				.setPositiveButton("Save", new DialogInterface.OnClickListener() {
					@Override public void onClick(DialogInterface dialog, int which) {
						try {
							String value = input.getText().toString().trim();
							if (value.isEmpty()) return;
							if (isTotp) {
								value = QuickCopyStore.normalizeTotpSecret(value);
								QuickCopyStore.currentTotp(value);
								QuickCopyStore.saveTotpSecret(QuickCopyActivity.this, value);
							} else {
								QuickCopyStore.savePassword(QuickCopyActivity.this, value);
							}
							Toast.makeText(QuickCopyActivity.this,
									"Saved securely", Toast.LENGTH_SHORT).show();
						} catch (Exception exception) {
							Toast.makeText(QuickCopyActivity.this,
									isTotp ? "Invalid Base32 secret" : "Could not save",
									Toast.LENGTH_LONG).show();
						}
					}
				})
				.show();
	}
}
