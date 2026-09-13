package hev.sockstun;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Shared storage and crypto used by the Quick Copy screen and floating panel. */
public final class QuickCopyStore {
	private static final String PREFS = "quick_copy_prefs";
	private static final String LEGACY_NAMES = "names_text";
	private static final String MASTER_NAMES = "master_names_text";
	private static final String QUEUE_NAMES = "queue_names_text";
	private static final String PASS = "password_enc";
	private static final String TOTP = "totp_enc";
	private static final String OVERLAY_ENABLED = "overlay_enabled";

	private static final String PASS_ALIAS = "QuickCopyPass";
	private static final String TOTP_ALIAS = "QuickCopyTotp";

	private QuickCopyStore() { }

	private static SharedPreferences prefs(Context context) {
		return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
	}

	public static synchronized void migrateLegacyNamesIfNeeded(Context context) {
		SharedPreferences preferences = prefs(context);
		if (preferences.contains(MASTER_NAMES)) return;
		String old = preferences.getString(LEGACY_NAMES, "");
		if (old == null || old.trim().isEmpty()) return;
		String packed = join(parseNames(old));
		preferences.edit()
				.putString(MASTER_NAMES, packed)
				.putString(QUEUE_NAMES, packed)
				.remove(LEGACY_NAMES)
				.apply();
	}

	public static List<String> parseNames(String text) {
		LinkedHashSet<String> unique = new LinkedHashSet<>();
		if (text != null) {
			for (String line : text.split("\\r?\\n")) {
				String clean = line.trim().replaceAll("\\s+", " ");
				if (!clean.isEmpty()) unique.add(clean);
			}
		}
		return new ArrayList<>(unique);
	}

	private static String join(List<String> list) {
		StringBuilder builder = new StringBuilder();
		for (String value : list) {
			String clean = value.trim().replaceAll("\\s+", " ");
			if (clean.isEmpty()) continue;
			if (builder.length() > 0) builder.append('\n');
			builder.append(clean);
		}
		return builder.toString();
	}

	private static List<String> getList(Context context, String key) {
		return parseNames(prefs(context).getString(key, ""));
	}

	public static synchronized List<String> getMaster(Context context) {
		migrateLegacyNamesIfNeeded(context);
		return getList(context, MASTER_NAMES);
	}

	public static synchronized List<String> getQueue(Context context) {
		migrateLegacyNamesIfNeeded(context);
		return getList(context, QUEUE_NAMES);
	}

	public static synchronized String getMasterText(Context context) {
		migrateLegacyNamesIfNeeded(context);
		return prefs(context).getString(MASTER_NAMES, "");
	}

	public static synchronized void saveMasterAndResetQueue(Context context, List<String> names) {
		String packed = join(names);
		prefs(context).edit()
				.putString(MASTER_NAMES, packed)
				.putString(QUEUE_NAMES, packed)
				.remove(LEGACY_NAMES)
				.apply();
	}

	public static synchronized List<String> ensureQueue(Context context) {
		List<String> queue = getQueue(context);
		if (!queue.isEmpty()) return queue;
		List<String> master = getMaster(context);
		if (!master.isEmpty()) {
			saveQueue(context, master);
			return new ArrayList<>(master);
		}
		return queue;
	}

	public static synchronized void saveQueue(Context context, List<String> queue) {
		prefs(context).edit().putString(QUEUE_NAMES, join(queue)).apply();
	}

	public static synchronized void advanceQueue(Context context, List<String> queue) {
		if (!queue.isEmpty()) queue.remove(0);
		if (queue.isEmpty()) saveQueue(context, getMaster(context));
		else saveQueue(context, queue);
	}

	public static synchronized int resetQueue(Context context) {
		List<String> master = getMaster(context);
		saveQueue(context, master);
		return master.size();
	}

	public static String[] splitName(String fullName) {
		String[] parts = fullName.trim().split("\\s+");
		if (parts.length == 1) return new String[]{parts[0], ""};
		StringBuilder last = new StringBuilder();
		for (int i = 1; i < parts.length; i++) {
			if (i > 1) last.append(' ');
			last.append(parts[i]);
		}
		return new String[]{parts[0], last.toString()};
	}

	public static boolean isOverlayEnabled(Context context) {
		return prefs(context).getBoolean(OVERLAY_ENABLED, true);
	}

	public static void setOverlayEnabled(Context context, boolean enabled) {
		prefs(context).edit().putBoolean(OVERLAY_ENABLED, enabled).apply();
	}

	public static void savePassword(Context context, String password) throws Exception {
		saveEncrypted(context, PASS, PASS_ALIAS, password);
	}

	public static String readPassword(Context context) throws Exception {
		return readEncrypted(context, PASS, PASS_ALIAS);
	}

	public static void deletePassword(Context context) {
		prefs(context).edit().remove(PASS).apply();
	}

	public static void saveTotpSecret(Context context, String secret) throws Exception {
		saveEncrypted(context, TOTP, TOTP_ALIAS, normalizeTotpSecret(secret));
	}

	public static String readTotpSecret(Context context) throws Exception {
		return readEncrypted(context, TOTP, TOTP_ALIAS);
	}

	public static void deleteTotpSecret(Context context) {
		prefs(context).edit().remove(TOTP).apply();
	}

	public static String normalizeTotpSecret(String secret) {
		return secret == null ? "" : secret.trim().replace(" ", "").replace("-", "");
	}

	private static SecretKey getOrCreateKey(String alias) throws Exception {
		KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
		keyStore.load(null);
		if (keyStore.containsAlias(alias)) {
			return ((KeyStore.SecretKeyEntry) keyStore.getEntry(alias, null)).getSecretKey();
		}
		KeyGenerator generator = KeyGenerator.getInstance(
				KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
		generator.init(new KeyGenParameterSpec.Builder(alias,
				KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
				.setBlockModes(KeyProperties.BLOCK_MODE_GCM)
				.setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
				.build());
		return generator.generateKey();
	}

	private static void saveEncrypted(Context context, String pref, String alias, String value)
			throws Exception {
		SecretKey key = getOrCreateKey(alias);
		Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
		cipher.init(Cipher.ENCRYPT_MODE, key);
		String packed = Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP)
				+ ":"
				+ Base64.encodeToString(
						cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP);
		prefs(context).edit().putString(pref, packed).apply();
	}

	private static String readEncrypted(Context context, String pref, String alias) throws Exception {
		String packed = prefs(context).getString(pref, null);
		if (packed == null) return null;
		String[] parts = packed.split(":", 2);
		if (parts.length != 2) return null;

		KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
		keyStore.load(null);
		KeyStore.SecretKeyEntry entry =
				(KeyStore.SecretKeyEntry) keyStore.getEntry(alias, null);
		if (entry == null) return null;

		Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
		cipher.init(Cipher.DECRYPT_MODE, entry.getSecretKey(),
				new GCMParameterSpec(128, Base64.decode(parts[0], Base64.DEFAULT)));
		return new String(cipher.doFinal(Base64.decode(parts[1], Base64.DEFAULT)),
				StandardCharsets.UTF_8);
	}

	public static String currentTotp(String base32Secret) throws Exception {
		byte[] key = base32(normalizeTotpSecret(base32Secret));
		if (key.length == 0) throw new IllegalArgumentException("Empty key");
		long counter = System.currentTimeMillis() / 1000 / 30;
		byte[] data = ByteBuffer.allocate(8).putLong(counter).array();

		Mac mac = Mac.getInstance("HmacSHA1");
		mac.init(new SecretKeySpec(key, "HmacSHA1"));
		byte[] hash = mac.doFinal(data);
		int offset = hash[hash.length - 1] & 15;
		int number = ((hash[offset] & 127) << 24)
				| ((hash[offset + 1] & 255) << 16)
				| ((hash[offset + 2] & 255) << 8)
				| (hash[offset + 3] & 255);
		return String.format("%06d", number % 1_000_000);
	}

	private static byte[] base32(String input) {
		String clean = input.toUpperCase().replace(" ", "").replace("-", "").replace("=", "");
		String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
		List<Byte> output = new ArrayList<>();
		int buffer = 0;
		int bits = 0;
		for (char ch : clean.toCharArray()) {
			int value = alphabet.indexOf(ch);
			if (value < 0) throw new IllegalArgumentException("Invalid Base32");
			buffer = (buffer << 5) | value;
			bits += 5;
			if (bits >= 8) {
				bits -= 8;
				output.add((byte) ((buffer >> bits) & 255));
			}
		}
		byte[] result = new byte[output.size()];
		for (int i = 0; i < output.size(); i++) result[i] = output.get(i);
		return result;
	}
}
