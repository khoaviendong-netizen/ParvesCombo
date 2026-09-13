package hev.sockstun;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

public final class NumberStore {
	private static final String FILE = "NumberQueue";
	private static final String KEY = "numbers";

	private NumberStore() { }

	private static SharedPreferences prefs(Context context) {
		return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
	}

	public static synchronized List<String> getAll(Context context) {
		String saved = prefs(context).getString(KEY, "");
		List<String> result = new ArrayList<>();
		if (saved == null || saved.isEmpty()) return result;
		for (String line : saved.split("\\n", -1)) {
			String value = line.trim();
			if (!value.isEmpty()) result.add(value);
		}
		return result;
	}

	public static synchronized int add(Context context, String input) {
		List<String> numbers = getAll(context);
		int before = numbers.size();
		if (input != null) {
			for (String token : input.split("[\\r\\n,;\\t ]+")) {
				String value = token.trim();
				if (!value.isEmpty()) numbers.add(value);
			}
		}
		save(context, numbers);
		return numbers.size() - before;
	}

	public static synchronized String popFirst(Context context) {
		List<String> numbers = getAll(context);
		if (numbers.isEmpty()) return null;
		String value = numbers.remove(0);
		save(context, numbers);
		return value;
	}

	public static synchronized void removeAt(Context context, int index) {
		List<String> numbers = getAll(context);
		if (index >= 0 && index < numbers.size()) {
			numbers.remove(index);
			save(context, numbers);
		}
	}

	public static synchronized int count(Context context) {
		return getAll(context).size();
	}

	public static synchronized void clear(Context context) {
		prefs(context).edit().remove(KEY).apply();
	}

	private static void save(Context context, List<String> numbers) {
		StringBuilder builder = new StringBuilder();
		for (String number : numbers) {
			if (builder.length() > 0) builder.append('\n');
			builder.append(number);
		}
		prefs(context).edit().putString(KEY, builder.toString()).commit();
	}
}
