package hev.sockstun;

import android.os.Handler;
import android.os.Looper;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

public final class ProxyIpChecker {
	public enum Status { OK, AUTH_FAIL, TIMEOUT, NO_DATA }

	public interface Callback {
		void onResult(Status status, String ip);
	}

	private static final String IP_HOST = "api.ipify.org";
	private static final int IP_PORT = 443;
	private static final int TIMEOUT_MS = 12000;

	private ProxyIpChecker() { }

	public static void check(final String proxyHost, final int proxyPort,
			final String username, final String password, final Callback callback) {
		new Thread(new Runnable() {
			@Override
			public void run() {
				Status status;
				String ip = null;
				try {
					ip = requestIp(proxyHost, proxyPort, username, password);
					status = isIp(ip) ? Status.OK : Status.NO_DATA;
				} catch (AuthException exception) {
					status = Status.AUTH_FAIL;
				} catch (SocketTimeoutException exception) {
					status = Status.TIMEOUT;
				} catch (Exception exception) {
					status = Status.NO_DATA;
				}
				final Status resultStatus = status;
				final String resultIp = ip;
				new Handler(Looper.getMainLooper()).post(new Runnable() {
					@Override
					public void run() {
						callback.onResult(resultStatus, resultIp);
					}
				});
			}
		}, "proxy-ip-check").start();
	}

	private static String requestIp(String proxyHost, int proxyPort,
			String username, String password) throws Exception {
		Socket proxySocket = new Socket();
		try {
			proxySocket.connect(new InetSocketAddress(proxyHost, proxyPort), TIMEOUT_MS);
			proxySocket.setSoTimeout(TIMEOUT_MS);
			DataInputStream input = new DataInputStream(proxySocket.getInputStream());
			OutputStream output = proxySocket.getOutputStream();

			boolean hasAuth = username != null && !username.isEmpty();
			if (hasAuth) output.write(new byte[] {5, 2, 0, 2});
			else output.write(new byte[] {5, 1, 0});
			output.flush();

			int version = input.readUnsignedByte();
			int method = input.readUnsignedByte();
			if (version != 5 || method == 0xFF) throw new AuthException();
			if (method == 2) authenticate(input, output, username, password);
			else if (method != 0) throw new AuthException();

			byte[] domain = IP_HOST.getBytes(StandardCharsets.US_ASCII);
			ByteArrayOutputStream request = new ByteArrayOutputStream();
			request.write(5);
			request.write(1);
			request.write(0);
			request.write(3);
			request.write(domain.length);
			request.write(domain);
			request.write((IP_PORT >> 8) & 0xFF);
			request.write(IP_PORT & 0xFF);
			output.write(request.toByteArray());
			output.flush();

			if (input.readUnsignedByte() != 5) throw new IOException("Bad SOCKS version");
			int reply = input.readUnsignedByte();
			input.readUnsignedByte();
			int type = input.readUnsignedByte();
			if (reply != 0) throw new IOException("SOCKS connect failed");
			skipAddress(input, type);
			input.readUnsignedShort();

			SSLSocketFactory sslFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
			SSLSocket ssl = (SSLSocket) sslFactory.createSocket(proxySocket, IP_HOST, IP_PORT, true);
			SSLParameters parameters = ssl.getSSLParameters();
			parameters.setEndpointIdentificationAlgorithm("HTTPS");
			ssl.setSSLParameters(parameters);
			ssl.setSoTimeout(TIMEOUT_MS);
			ssl.startHandshake();
			OutputStream secureOutput = ssl.getOutputStream();
			secureOutput.write(("GET / HTTP/1.0\r\nHost: " + IP_HOST
					+ "\r\nConnection: close\r\nUser-Agent: ParvesProxy/2\r\n\r\n")
					.getBytes(StandardCharsets.US_ASCII));
			secureOutput.flush();
			String response = readLimited(ssl.getInputStream(), 16384);
			int separator = response.indexOf("\r\n\r\n");
			if (!response.startsWith("HTTP/1.1 200") && !response.startsWith("HTTP/1.0 200"))
				throw new IOException("IP service error");
			if (separator < 0) throw new IOException("Invalid response");
			return response.substring(separator + 4).trim();
		} finally {
			try { proxySocket.close(); } catch (IOException ignored) { }
		}
	}

	private static void authenticate(DataInputStream input, OutputStream output,
			String username, String password) throws Exception {
		byte[] user = safeUtf8(username);
		byte[] pass = safeUtf8(password);
		if (user.length == 0 || user.length > 255 || pass.length > 255) throw new AuthException();
		ByteArrayOutputStream auth = new ByteArrayOutputStream();
		auth.write(1);
		auth.write(user.length);
		auth.write(user);
		auth.write(pass.length);
		auth.write(pass);
		output.write(auth.toByteArray());
		output.flush();
		input.readUnsignedByte();
		if (input.readUnsignedByte() != 0) throw new AuthException();
	}

	private static byte[] safeUtf8(String value) {
		return (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
	}

	private static void skipAddress(DataInputStream input, int type) throws IOException {
		int length;
		if (type == 1) length = 4;
		else if (type == 4) length = 16;
		else if (type == 3) length = input.readUnsignedByte();
		else throw new IOException("Unknown address type");
		byte[] ignored = new byte[length];
		input.readFully(ignored);
	}

	private static String readLimited(InputStream input, int limit) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		byte[] buffer = new byte[1024];
		while (bytes.size() < limit) {
			int count = input.read(buffer, 0, Math.min(buffer.length, limit - bytes.size()));
			if (count < 0) break;
			bytes.write(buffer, 0, count);
		}
		return new String(bytes.toByteArray(), StandardCharsets.US_ASCII);
	}

	private static boolean isIp(String value) {
		if (value == null || value.length() < 3 || value.length() > 45) return false;
		if (value.indexOf(':') >= 0) return value.matches("[0-9a-fA-F:]+");
		String[] parts = value.split("\\.", -1);
		if (parts.length != 4) return false;
		for (String part : parts) {
			try {
				int number = Integer.parseInt(part);
				if (number < 0 || number > 255) return false;
			} catch (NumberFormatException exception) {
				return false;
			}
		}
		return true;
	}

	private static final class AuthException extends Exception {
		private static final long serialVersionUID = 1L;
	}
}
