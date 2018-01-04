package x7.core.util;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.Map;

import org.apache.http.HttpEntity;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import x7.core.util.JsonX;
import x7.core.util.StringUtil;

public class HttpClientUtil {

	public static String post(String url, Object param) {

		System.out.println("HttpClientUtil post, url: " + url);
		// 创建默认的httpClient实例.
		CloseableHttpClient httpclient = HttpClients.createDefault();
		// 创建httppost
		HttpPost httpPost = new HttpPost(url);
		// 20161025 modify by cl
		// String json=JSON.toJSONString(param);
		String json = "";
		if (param != null) {
			json = JsonX.toJson(param);
		}
		HttpEntity entity = null;
		String result = null;
		try {
			entity = new ByteArrayEntity(json.getBytes("UTF-8"));
			httpPost.setHeader("Content-type", "application/json;charset=UTF-8");
			httpPost.setEntity(entity);
			System.out.println("executing request " + httpPost.getURI());
			CloseableHttpResponse response = httpclient.execute(httpPost);
			try {
				entity = response.getEntity();
				if (entity != null) {
					result = EntityUtils.toString(entity, "UTF-8");
					System.out.println("--------------------------------------");
					System.out.println("Response content: " + result);
					System.out.println("--------------------------------------");
				}
			} finally {
				response.close();
			}
		} catch (ClientProtocolException e) {
			e.printStackTrace();
		} catch (UnsupportedEncodingException e1) {
			e1.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			// 关闭连接,释放资源
			try {
				httpclient.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		return result;
	}

	public static String getUrl(String urlString) {
		StringBuffer sb = new StringBuffer();
		try {
			URL url = new URL(urlString);

			URLConnection conn = url.openConnection();

			conn.setReadTimeout(15000);
			BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));

			for (String line = null; (line = reader.readLine()) != null;) {
				sb.append(line + "\n");
			}
			reader.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		String result = "";
		try {
			result = URLDecoder.decode(sb.toString(), "UTF-8");
			System.out.println("getUrl: " + result);
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}

		return result;
	}

	public final static String EQ = "=";
	public final static String AND = "&";

	public static String getUrl(String url, Map<String, String> map) {

		if (StringUtil.isNullOrEmpty(url))
			return null;

		StringBuilder sb = new StringBuilder();
		sb.append(url);
		sb.append("?");

		int size = map.size();
		int i = 0;
		for (String key : map.keySet()) {
			i++;
			String value = map.get(key);
			sb.append(key).append(EQ).append(value);
			if (i < size) {
				sb.append(AND);
			}
		}

		String requestStr = sb.toString();

		System.out.println("request getUrl: " + requestStr);

		String result = getUrl(requestStr);

		return result;
	}

	public static void upload(String url, String filepath) {

	}

	public static FileWrapper getFile(String url) {

		FileWrapper file = new FileWrapper();
		InputStream is = null;
		ByteArrayOutputStream baos = null;
		try {
			URL urlGet = new URL(url);
			HttpURLConnection http = (HttpURLConnection) urlGet.openConnection();
			http.setRequestMethod("GET"); // 必须是get方式请求
			http.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
			http.setDoOutput(true);
			http.setDoInput(true);
			System.setProperty("sun.net.client.defaultConnectTimeout", "30000");// 连接超时30�?
			System.setProperty("sun.net.client.defaultReadTimeout", "30000"); // 读取超时30�?
			http.connect();

			String extName = getFileExpandedName(http.getHeaderField("Content-Type"));
			file.setExtName(extName);
			// 获取文件转化为byte�?
			is = http.getInputStream();

			byte[] data = new byte[10240];
			int len = 0;

			baos = new ByteArrayOutputStream();

			while ((len = is.read(data)) != -1) {
				baos.write(data, 0, len);
			}

			file.setBytes(baos.toByteArray());

		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (is != null) {
				try {
					is.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			if (baos != null) {
				try {
					baos.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

		return file;

	}

	private static String getFileExpandedName(String contentType) {
		String fileEndWitsh = "";
		if ("image/jpeg".equals(contentType))
			fileEndWitsh = ".jpg";
		if ("image/png".equals(contentType))
			fileEndWitsh = ".png";
		else if ("audio/mpeg".equals(contentType))
			fileEndWitsh = ".mp3";
		else if ("audio/amr".equals(contentType))
			fileEndWitsh = ".amr";
		else if ("video/mp4".equals(contentType))
			fileEndWitsh = ".mp4";
		else if ("video/mpeg4".equals(contentType))
			fileEndWitsh = ".mp4";
		return fileEndWitsh;
	}

	public static class FileWrapper {
		private String extName;
		private byte[] bytes;

		public String getExtName() {
			return extName;
		}

		public void setExtName(String extName) {
			this.extName = extName;
		}

		public byte[] getBytes() {
			return bytes;
		}

		public void setBytes(byte[] bytes) {
			this.bytes = bytes;
		}

		@Override
		public String toString() {
			return "FileWrapper [extName=" + extName + ", bytes=" + Arrays.toString(bytes) + "]";
		}
	}
}
