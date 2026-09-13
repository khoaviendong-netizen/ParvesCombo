package hev.sockstun;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

public class NumbersActivity extends Activity implements View.OnClickListener {
	private EditText input;
	private TextView remaining;
	private ArrayAdapter<String> adapter;

	@Override
	protected void onCreate(Bundle state) {
		super.onCreate(state);
		PremiumUi.setupWindow(this);
		setContentView(R.layout.numbers);
		input = findViewById(R.id.number_input);
		PremiumUi.styleInput(input);
		remaining = findViewById(R.id.number_remaining);
		PremiumUi.styleButton((Button) findViewById(R.id.add_numbers), "phone", true, false);
		PremiumUi.styleButton((Button) findViewById(R.id.clear_numbers), "close", false, true);
		Button back = findViewById(R.id.back);
		PremiumUi.styleButton(back, "back", false, false);
		back.setText("");
		back.setPadding(PremiumUi.dp(this, 14), 0, PremiumUi.dp(this, 14), 0);
		back.setBackgroundColor(PremiumUi.BG);
		findViewById(R.id.add_numbers).setOnClickListener(this);
		findViewById(R.id.clear_numbers).setOnClickListener(this);
		findViewById(R.id.back).setOnClickListener(this);
		ListView list = findViewById(R.id.number_list);
		list.setItemsCanFocus(true);
		View form = findViewById(R.id.premium_numbers_form);
		((ViewGroup) form.getParent()).removeView(form);
		list.addHeaderView(form, null, false);
		final int headerCount = list.getHeaderViewsCount();
		adapter = new ArrayAdapter<String>(this, R.layout.premium_number_item,
				R.id.premium_number_text, new ArrayList<String>()) {
			@Override
			public View getView(int position, View recycled, ViewGroup parent) {
				View row = super.getView(position, recycled, parent);
				TextView number = row.findViewById(R.id.premium_number_text);
				number.setBackground(PremiumUi.surface(NumbersActivity.this,
						PremiumUi.CARD, PremiumUi.BORDER, 12));
				number.setCompoundDrawablesRelative(
						PremiumUi.icon(NumbersActivity.this, "phone", PremiumUi.MUTED), null, null, null);
				number.setCompoundDrawablePadding(PremiumUi.dp(NumbersActivity.this, 14));
				return row;
			}
		};
		list.setAdapter(adapter);
		list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
			@Override
			public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
				int numberPosition = position - headerCount;
				if (numberPosition < 0) return false;
				NumberStore.removeAt(NumbersActivity.this, numberPosition);
				refresh();
				return true;
			}
		});
		((LinearLayout) findViewById(R.id.premium_numbers_nav))
				.addView(PremiumUi.bottomNav(this, "Numbers"));
		refresh();
	}

	@Override
	public void onClick(View view) {
		if (view.getId() == R.id.add_numbers) {
			int count = NumberStore.add(this, input.getText().toString());
			input.setText("");
			Toast.makeText(this, getString(R.string.numbers_added, count), Toast.LENGTH_SHORT).show();
			refresh();
		} else if (view.getId() == R.id.clear_numbers) {
			new AlertDialog.Builder(this)
					.setMessage(R.string.clear_confirm)
					.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							NumberStore.clear(NumbersActivity.this);
							refresh();
						}
					})
					.setNegativeButton(android.R.string.cancel, null)
					.show();
		} else if (view.getId() == R.id.back) {
			finish();
		}
	}

	private void refresh() {
		adapter.clear();
		adapter.addAll(NumberStore.getAll(this));
		adapter.notifyDataSetChanged();
		remaining.setText(getString(R.string.remaining, adapter.getCount()));
	}
}
