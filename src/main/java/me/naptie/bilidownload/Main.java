package me.naptie.bilidownload;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import cz.mallat.uasparser.OnlineUpdater;
import cz.mallat.uasparser.UASparser;
import me.naptie.bilidownload.objects.Downloader;
import me.naptie.bilidownload.utils.ConfigManager;
import me.naptie.bilidownload.utils.HttpManager;
import me.naptie.bilidownload.utils.LoginManager;
import me.naptie.bilidownload.utils.SignUtil;
import org.apache.commons.codec.digest.DigestUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;

@SuppressWarnings("ResultOfMethodCallIgnored")
public class Main {

	// 定义全局静态布尔变量debug，用于控制程序的调试状态
	public static boolean debug;

	// 定义全局静态布尔变量hint，用于控制是否显示提示信息
	public static boolean hint;

	// 定义全局静态布尔变量isFileInput，用于标记程序是否从文件中输入数据
	public static boolean isFileInput;

	// 定义全局静态Scanner对象，用于程序的输入操作
	private static Scanner scanner;

	// 定义全局静态File对象，用于程序配置文件的存储和访问
	private static File config;

	// 定义全局静态长整型变量beginTime，用于记录程序的开始时间，用于性能分析
	private static long beginTime;


	/**
	 * 程序的入口点。
	 * 根据传入的命令行参数决定程序的执行路径：直接下载或调试模式。
	 * 使用配置文件来设置程序的行为。
	 * java -jar bili-download-1.3.6-jar-with-dependencies.jar
	 * direct "http://upos-sz-mirrorkodo.bilivideo.com/upgcxcode/90/37/315703790/315703790-1-30336.m4s?e=ig8euxZM2rNcNbdlhoNvNC8BqJIzNbfqXBvEuENvNC8aNEVEtEvE9IMvXBvE2ENvNCImNEVEIj0Y2J_aug859r1qXg8gNEVE5XREto8z5JZC2X2gkX5L5F1eTX1jkXlsTXHeux_f2o859IB_&ua=tvproj&uipk=5&nbs=1&deadline=1622289611&gen=playurlv2&os=kodobv&oi=2078815810&trid=b7708dc7ef174e5bbe4fba32f5418517t&upsig=29cbb17759b52b6499638195bf0861aa&uparams=e,ua,uipk,nbs,deadline,gen,os,oi,trid&mid=474403243&bvc=vod&orderid=0,1&logo=80000000"
	 * "D:\BiliDownload\快住手！这根本不是 Kawaii Bass！_ 恋のうた Remix 工程演示.mp4"
	 *
	 * @param args 命令行参数，包括直接下载的URL和路径，或者调试模式的标志。
	 * @throws IOException 如果读取配置文件发生错误。
	 * @throws InterruptedException 如果线程被中断。
	 */
	public static void main(String[] args) throws IOException, InterruptedException {
		// 记录程序开始时间
		beginTime = System.currentTimeMillis();
		// 初始化配置文件
		config = new File("config.yml");

		// 检查是否直接从URL下载
		if (args.length > 2 && args[0].equalsIgnoreCase("direct")) {
			String url = args[1];
			String path = args[2];
			// 从URL下载文件
			downloadFromUrl(url, path);
			// 输出程序运行时间并退出
			System.out.println("\n程序运行结束；总运行时间：" + getFormattedTime(System.currentTimeMillis() - beginTime));
			System.exit(0);
		}

		// 设置调试模式
		debug = args.length > 0 && args[0].equalsIgnoreCase("debug");
		// 初始化输入设备
		setScanner();
		// 获取视频ID
		String id = getNumber();
		// 登录视频网站
		String[] login = login();
		// 获取视频信息
		JSONObject info = getVideoInfo(id, login[0], !login[1].isEmpty());
		// 指定下载的视频内容
		Object[] specified = specify(info);
		// 获取视频的分辨率信息
		Object[] details = getResolutions(info, login, !login[1].isEmpty(), (int) specified[0]);
		// 设置下载路径
		String[] path = getPath((String) specified[1]);
		// 为路径添加视频分辨率信息
		path[1] += " [" + details[4] + "]";
		// 开始下载视频
		download(details, path);
		// 输出程序运行时间并退出
		System.out.println("\n程序运行结束；总运行时间：" + getFormattedTime(System.currentTimeMillis() - beginTime));
		System.exit(0);
	}


	private static void setScanner() throws FileNotFoundException {
		File input = new File("Input.txt");
		if (input.exists() && input.length() > 0) {
			scanner = new Scanner(input);
			isFileInput = true;
			hint = debug;
			System.out.println("检测到 Input.txt，已切换输入源\n");
		} else {
			scanner = new Scanner(System.in);
			isFileInput = false;
			hint = true;
		}
	}

	private static int inputInt() {
		String input = input();
		return Integer.parseInt(input);
	}

	private static String input() {
		String input = scanner.nextLine();
		if (input.equalsIgnoreCase("*exit")) {
			System.out.println("\n程序运行结束；总运行时间：" + getFormattedTime(System.currentTimeMillis() - beginTime));
			System.exit(0);
		}
		if (debug && hint && isFileInput) System.out.println(input);
		return input;
	}

	private static String getNumber() {
		if (hint) System.out.println("请输入一个 AV 号或 BV 号：");
		return input();
	}

	private static String[] login() throws IOException {
		boolean webSuccess = false, tvSuccess = false;
		String sessData = "#", accessToken = "", cookie = "#";
		if (config.exists()) {
			ConfigManager.init(config);
			Map<String, Object> map = ConfigManager.get();
			if (map == null)
				map = new LinkedHashMap<>();
			if (map.containsKey("sess-data")) {
				sessData = (String) map.get("sess-data");
				cookie = "SESSDATA=" + sessData + "; Path=/; Domain=bilibili.com;";
				JSONObject login = HttpManager.readJsonFromUrl("https://api.bilibili.com/x/web-interface/nav", cookie, false);
				if (login.getIntValue("code") == 0)
					if (login.getJSONObject("data").getBoolean("isLogin")) {
						System.out.println("成功使用保存的 SESSDATA 登录\nID：" + login.getJSONObject("data").getString("uname") + "\nUID：" + login.getJSONObject("data").getIntValue("mid"));
						webSuccess = true;
					}
			}
			if (map.containsKey("access-token")) {
				accessToken = (String) map.get("access-token");
				String params = "access_key=" + accessToken + "&appkey=4409e2ce8ffd12b8&ts=" + System.currentTimeMillis();
				JSONObject login = HttpManager.readJsonFromUrl("https://app.bilibili.com/x/v2/account/myinfo?" + params + "&sign=" + SignUtil.generate(params), "#", true);
				if (login.getIntValue("code") == 0) {
					System.out.println("成功使用保存的 TOKEN 登录\nID：" + login.getJSONObject("data").getString("name") + "\nUID：" + login.getJSONObject("data").getIntValue("mid"));
					tvSuccess = true;
				}
			}
		}
		boolean loginSuccess = webSuccess && tvSuccess;
		while (!loginSuccess) {
			System.out.println("\n登录方式：\n  1. WEB 端二维码登录\n  2. TV 端二维码登录\n  3. 输入 SESSDATA 登录\n  4. 跳过登录\n请选择登录方式（输入 1~4 之间的整数）：");
			int method = inputInt();
			if (method < 1) {
				System.out.println("输入的数字“" + method + "”太小，已为您选择 WEB 端二维码登录");
				method = 1;
			}
			if (method > 4) {
				System.out.println("输入的数字“" + method + "”太大，已为您选择跳过登录");
				method = 4;
			}
			if (method == 1) {
				LoginManager.showQRCodeFromWeb();
				sessData = LoginManager.sessData;
				if (sessData.isEmpty()) {
					System.out.println("登录失败");
					System.out.println("本次 WEB 端二维码登录所用 UA 的浏览器为 " + (new UASparser(OnlineUpdater.getVendoredInputStream()).parse(LoginManager.userAgent)).getUaFamily());
					continue;
				}
			} else if (method == 2) {
				LoginManager.showQRCodeFromTV();
				accessToken = LoginManager.accessToken;
				if (accessToken.isEmpty()) {
					System.out.println("登录失败");
					continue;
				}
			} else if (method == 3) {
				if (hint) System.out.println("\n请输入 Cookie 中 SESSDATA 的值：");
				sessData = input();
			} else {
				break;
			}
			if (method != 2) {
				if (sessData.equals("#")) {
					cookie = "#";
					break;
				}
				cookie = "SESSDATA=" + sessData + "; Path=/; Domain=bilibili.com;";
				JSONObject login = HttpManager.readJsonFromUrl("https://api.bilibili.com/x/web-interface/nav", cookie, false);
				if (login.getIntValue("code") == 0)
					if (login.getJSONObject("data").getBoolean("isLogin")) {
						webSuccess = loginSuccess = true;
						System.out.println("登录成功" + (debug ? "\nID：" + login.getJSONObject("data").getString("uname") + "\nUID：" + login.getJSONObject("data").getIntValue("mid") : ""));
						System.out.println("本次 WEB 端二维码登录所用 UA 的浏览器为 " + (new UASparser(OnlineUpdater.getVendoredInputStream()).parse(LoginManager.userAgent)).getUaFamily() + "，请于登录操作通知中核实对照");
						if (hint) System.out.println("请决定是否保存该 SESSDATA（输入“Y”代表是，输入“N”代表否）：");
						if (input().equalsIgnoreCase("Y")) {
							if (!config.exists()) config.createNewFile();
							ConfigManager.init(config);
							Map<String, Object> map = ConfigManager.get();
							if (map == null)
								map = new LinkedHashMap<>();
							map.put("sess-data", sessData);
							ConfigManager.dump(map);
							if (hint) System.out.println("已保存 SESSDATA");
						}
						if (!tvSuccess) {
							if (hint) System.out.println("请决定是否继续登录（输入“Y”代表是，输入“N”代表否）：");
							if (input().equalsIgnoreCase("Y"))
								loginSuccess = false;
						}
					} else {
						System.out.println("登录失败");
					}
				else {
					System.out.println("登录失败");
				}
			} else {
				String params = "access_key=" + accessToken + "&appkey=4409e2ce8ffd12b8&ts=" + System.currentTimeMillis();
				JSONObject login = HttpManager.readJsonFromUrl("https://app.bilibili.com/x/v2/account/myinfo?" + params + "&sign=" + SignUtil.generate(params), "#", true);
				if (login.getIntValue("code") == 0) {
					tvSuccess = loginSuccess = true;
					System.out.println("登录成功" + (debug ? "\nID：" + login.getJSONObject("data").getString("name") + "\nUID：" + login.getJSONObject("data").getIntValue("mid") : ""));
					if (hint) System.out.println("请决定是否保存该 TOKEN（输入“Y”代表是，输入“N”代表否）：");
					if (input().equalsIgnoreCase("Y")) {
						if (!config.exists()) config.createNewFile();
						ConfigManager.init(config);
						Map<String, Object> map = ConfigManager.get();
						if (map == null)
							map = new LinkedHashMap<>();
						map.put("access-token", accessToken);
						ConfigManager.dump(map);
						if (hint) System.out.println("已保存 TOKEN");
					}
					if (!webSuccess) {
						if (hint) System.out.println("请决定是否继续登录（输入“Y”代表是，输入“N”代表否）：");
						if (input().equalsIgnoreCase("Y"))
							loginSuccess = false;
					}
				} else {
					System.out.println("登录失败");
				}
			}
		}
		return new String[]{cookie, accessToken};
	}

	/**
	 * 根据视频ID和Cookie获取视频信息。
	 * @param id 视频的AV号或BV号。
	 * @param cookie 用于身份验证的Cookie。
	 * @param tv 是否为TV版接口。
	 * @return 包含视频信息的JSONObject。
	 * @throws IOException 如果发生I/O错误。
	 */
	private static JSONObject getVideoInfo(String id, String cookie, boolean tv) throws IOException {
	    // 输出提示信息，说明正在获取视频信息
	    System.out.println((hint ? "\n" : "") + "正在获取稿件信息······");

	    // 根据ID的类型（AV号或BV号）构造API请求URL，并发送请求获取视频信息
	    JSONObject info = HttpManager.readJsonFromUrl("https://api.bilibili.com/x/web-interface/view?" + (id.toLowerCase().startsWith("av") ? "aid=" + id.substring(2) : "bvid=" + id),
				tv ? "#" : cookie, tv);

	    // 如果返回码不为0，表示请求失败，输出错误信息并退出程序
	    if (info.getIntValue("code") != 0) {
	        System.out.println(info.getString("message"));
	        System.out.println("\n程序运行结束，错误代码：" + info.getIntValue("code") + "；总运行时间：" + getFormattedTime(System.currentTimeMillis() - beginTime));
	        System.exit(info.getIntValue("code"));
	    } else {
	        // 如果请求成功，进一步获取视频的具体信息
	        info = info.getJSONObject("data");
	    }

	    // 输出视频的标题、UP主、时长、播放量、弹幕量、获赞数、投币数和收藏数
	    System.out.println("\n标题：" + info.getString("title"));
	    System.out.println("UP主：" + info.getJSONObject("owner").getString("name"));
	    System.out.println("时长：" + getFormattedTime(info.getIntValue("duration"), info.getIntValue("duration") > 3600));
	    System.out.println("播放：" + String.format("%,d", info.getJSONObject("stat").getIntValue("view")));
	    System.out.println("弹幕：" + String.format("%,d", info.getJSONObject("stat").getIntValue("danmaku")));
	    System.out.println("获赞：" + String.format("%,d", info.getJSONObject("stat").getIntValue("like")));
	    System.out.println("投币：" + String.format("%,d", info.getJSONObject("stat").getIntValue("coin")));
	    System.out.println("收藏：" + String.format("%,d", info.getJSONObject("stat").getIntValue("favorite")));

	    // 返回包含视频信息的JSONObject
	    return info;
	}

	/**
	 * 根据提供的JSONObject，指定并返回视频的CID和名称。
	 * 如果视频有多个分P，则允许用户选择具体的分P。
	 *
	 * @param info 包含视频信息的JSONObject，必须包含"title"、"videos"、"pages"等字段。
	 * @return 包含CID和名称的对象数组，名称中会包含所选分P的信息。
	 */
	private static Object[] specify(JSONObject info) {
	    int cid;
	    // 获取视频标题
	    String name = info.getString("title");

	    // 判断视频是否有多个分P
	    if (info.getIntValue("videos") > 1) {
	        // 获取分P信息
	        JSONArray pages = info.getJSONArray("pages");
	        // 打印分P列表
	        System.out.println("\n分P：");
	        for (int i = 0; i < pages.size(); i++) {
	            System.out.println(String.format("%3d", (i + 1)) + ". P" + String.format("%-5d", pages.getJSONObject(i).getIntValue("page")) + "CID：" + pages.getJSONObject(i).getIntValue("cid") + "  时长：" + getFormattedTime(pages.getJSONObject(i).getIntValue("duration"), pages.getJSONObject(i).getIntValue("duration") >= 3600) + "  标题：" + pages.getJSONObject(i).getString("part"));
	        }
	        // 提示用户选择分P
	        if (hint) System.out.println("请选择分P（输入 1~" + pages.size() + " 之间的整数）：");
	        int part = inputInt();
	        // 对用户输入的分P编号进行验证和修正
	        if (part > pages.size()) {
	            System.out.println("输入的数字“" + part + "”太大，已为您选择末尾的分P " + pages.getJSONObject(pages.size() - 1).getString("part"));
	            part = pages.size();
	        }
	        if (part < 1) {
	            System.out.println("输入的数字“" + part + "”太小，已为您选择开头的分P " + pages.getJSONObject(0).getString("part"));
	            part = 1;
	        }
	        // 根据用户选择，获取CID和分P名称
	        cid = pages.getJSONObject(part - 1).getIntValue("cid");
	        name += " [P" + part + "] " + pages.getJSONObject(part - 1).getString("part");
	    } else {
	        // 如果视频只有一个分P，则直接获取CID
	        cid = info.getIntValue("cid");
	    }
	    // 返回CID和名称
	    return new Object[]{cid, name};
	}

	private static Object[] getResolutions(JSONObject info, String[] auth, boolean tv, int cid) throws IOException {
		System.out.println("\n正在获取清晰度信息······");
		String videoUrlTV = "https://api.snm0516.aisee.tv/x/tv/ugc/playurl?avid=" + info.getIntValue("aid") + "&mobi_app=android_tv_yst&fnval=80&qn=120&cid=" + cid + (tv ? "&access_key=" + auth[1] : "") + "&fourk=1&platform=android&device=android&build=103800&fnver=0";
		String videoUrlWeb = "https://api.bilibili.com/x/player/playurl?avid=" + info.getIntValue("aid") + "&cid=" + cid + "&fnval=80&fourk=1";
//		String videoUrlWebPGC = "https://api.bilibili.com/pgc/player/web/playurl?cid=" + cid + "&qn=120&fourk=1&fnver=0&fnval=80";
		JSONObject videoTV = HttpManager.readJsonFromUrl(videoUrlTV, "#", true);
		JSONObject videoWeb = HttpManager.readJsonFromUrl(videoUrlWeb, auth[0], false).getJSONObject("data");
		JSONArray qualitiesTV = videoTV.getJSONArray("accept_description");
		JSONArray qualitiesWeb = videoWeb.getJSONArray("accept_description");
		JSONArray qualities = summarize(qualitiesTV, qualitiesWeb, videoTV);
		System.out.println("\n清晰度：");
		for (int i = 1; i < qualities.size(); i++) {
			System.out.println(String.format("%3d", i) + ". " + qualities.getString(i));
		}
		if (hint) System.out.println("请选择清晰度（输入 1~" + (qualities.size() - 1) + " 之间的整数）：");
		int quality = inputInt();
		String videoDownloadUrl;
		if (qualities.getIntValue(0) == 1) {
			if (quality > qualities.size() - 1) {
				System.out.println("输入的数字“" + quality + "”太大，已为您选择最差清晰度 " + qualities.getString(qualities.size() - 1).replaceAll(" +", " "));
				quality = qualities.size() - 1;
				videoDownloadUrl = getVideoDownload(videoWeb, qualitiesWeb.size() - 1);
			} else if (quality > qualitiesTV.size())
				videoDownloadUrl = getVideoDownload(videoWeb, quality - qualitiesTV.size() - 1);
			else if (quality > 0)
				videoDownloadUrl = getVideoDownload(videoTV, quality - 1);
			else {
				System.out.println("输入的数字“" + quality + "”太小，已为您选择最佳清晰度 " + qualities.getString(1).replaceAll(" +", " "));
				quality = 1;
				videoDownloadUrl = getVideoDownload(videoTV, 0);
			}
		} else {
			if (quality > qualities.size() - 1) {
				System.out.println("输入的数字“" + quality + "”太大，已为您选择最差清晰度 " + qualities.getString(qualities.size() - 1).replaceAll(" +", " "));
				quality = qualities.size() - 1;
				videoDownloadUrl = getVideoDownload(videoWeb, qualitiesWeb.size() - 1);
			} else if (quality > 0)
				videoDownloadUrl = getVideoDownload(videoWeb, quality - 1);
			else {
				System.out.println("输入的数字“" + quality + "”太小，已为您选择最佳清晰度 " + qualities.getString(1).replaceAll(" +", " "));
				quality = 1;
				videoDownloadUrl = getVideoDownload(videoWeb, 0);
			}
		}
		return new Object[]{videoDownloadUrl, qualities, quality, videoWeb, qualities.getString(quality).replaceAll(" +", " ")};
	}

	private static String[] getPath(String name) throws IOException {
		boolean pathSuccess = false;
		String savePath = "";
		if (config.exists()) {
			ConfigManager.init(config);
			Map<String, Object> map = ConfigManager.get();
			if (map == null)
				map = new LinkedHashMap<>();
			if (map.containsKey("save-path")) {
				File file = new File((String) map.get("save-path"));
				if (file.isDirectory()) {
					pathSuccess = true;
					savePath = file.getAbsolutePath();
					if (debug) System.out.println("\n成功获取保存路径：" + savePath);
				}
			}
		}
		while (!pathSuccess) {
			if (hint) System.out.println("\n请输入保存路径：");
			savePath = input();
			File file;
			if (savePath.startsWith("~")) {
				String userHomeDir = Paths.get(System.getProperty("user.home")).toAbsolutePath().toString();
				if (debug) System.out.println("检测到路径以“~”开头，已找到用户主目录：" + userHomeDir);
				file = new File(userHomeDir, savePath.substring(1));
				savePath = file.getAbsolutePath();
			} else {
				file = new File(savePath);
			}
			if (!file.exists()) {
				if (hint) System.out.println("该目录不存在，请决定是否创建该目录（输入“Y”代表是，输入“N”代表否）：");
				if (input().equalsIgnoreCase("Y")) {
					pathSuccess = file.mkdirs();
					if (!pathSuccess) System.out.println("创建目录失败");
				}
			} else {
				pathSuccess = true;
			}
			if (pathSuccess) {
				if (hint) System.out.println("请决定是否保存该保存路径（输入“Y”代表是，输入“N”代表否）：");
				if (input().equalsIgnoreCase("Y")) {
					if (!config.exists()) config.createNewFile();
					ConfigManager.init(config);
					Map<String, Object> map = ConfigManager.get();
					if (map == null)
						map = new LinkedHashMap<>();
					map.put("save-path", savePath);
					ConfigManager.dump(map);
					if (hint) System.out.println("已保存该保存路径");
				}
			}
		}
		return new String[]{savePath, name.replaceAll("[/\\\\:*?<>|]", "_")};
	}

	private static void download(Object[] details, String[] path) throws IOException, InterruptedException {
		String videoDownloadUrl = (String) details[0];
		JSONArray qualities = (JSONArray) details[1];
		int quality = (int) details[2];
		JSONObject videoWeb = (JSONObject) details[3];
		if (hint) System.out.println("\n下载选项：\n  1. 视频+音频（合并需要 FFmpeg）\n  2. 仅视频\n  3. 仅音频\n请选择下载选项（输入 1~3 之间的整数）：");
		int choice = inputInt();
		if (choice > 3) {
			System.out.println("输入的数字“" + choice + "”太大，已为您选择最后一个选项 仅音频");
			choice = 3;
		}
		if (choice < 1) {
			System.out.println("输入的数字“" + choice + "”太小，已为您选择第一个选项 视频+音频（合并需要 FFmpeg）");
			choice = 1;
		}
		switch (choice) {
			case 1: {
				int ffmpegSuccess = 0;
				File ffmpeg = new File(System.getProperty("user.dir"), "null");
				if (config.exists()) {
					ConfigManager.init(config);
					Map<String, Object> map = ConfigManager.get();
					if (map == null)
						map = new LinkedHashMap<>();
					if (map.containsKey("ffmpeg-path")) {
						String ffmpegPath = (String) map.get("ffmpeg-path");
						ffmpeg = ffmpegPath.endsWith("ffmpeg.exe") ? new File(ffmpegPath) : new File(ffmpegPath, "ffmpeg.exe");
						ffmpegSuccess = ffmpeg.exists() ? 1 : 0;
						if (ffmpegSuccess == 1 && debug)
							System.out.println("\n成功获取 FFmpeg 路径：" + ffmpeg.getAbsolutePath());
					}
				}
				while (ffmpegSuccess == 0) {

					if (hint) System.out.println("\n请输入 ffmpeg(.exe) 的绝对路径（跳过合并请填“#”）：");
					String ffmpegPath = input();
					if (ffmpegPath.equals("#")) {
						ffmpegSuccess = -1;
						break;
					}
					ffmpeg = (ffmpegPath.endsWith("ffmpeg.exe") || (!System.getProperty("os.name").toLowerCase().contains("windows") && ffmpegPath.endsWith("ffmpeg"))) ? new File(ffmpegPath) : new File(ffmpegPath, System.getProperty("os.name").toLowerCase().contains("windows") ? "ffmpeg.exe" : "ffmpeg");
					ffmpegSuccess = ffmpeg.exists() && !ffmpeg.isDirectory() ? 1 : 0;
					if (ffmpegSuccess == 1) {
						if (hint) System.out.println("请决定是否保存 FFmpeg 路径（输入“Y”代表是，输入“N”代表否）：");
						if (input().equalsIgnoreCase("Y")) {
							if (!config.exists()) config.createNewFile();
							ConfigManager.init(config);
							Map<String, Object> map = ConfigManager.get();
							if (map == null)
								map = new LinkedHashMap<>();
							map.put("ffmpeg-path", ffmpeg.getAbsolutePath());
							ConfigManager.dump(map);
							if (hint) System.out.println("已保存 FFmpeg 路径");
						}
					}
					if (ffmpegSuccess == 0) {
						System.out.println("无法找到 ffmpeg(.exe)");
					}
				}
				boolean videoSuccess, audioSuccess;
				videoDownloadUrl = validateVideoUrl(videoDownloadUrl, qualities, quality, videoWeb);
				String audioDownloadUrl = getAudioDownload(videoWeb);
				System.out.println("\n成功获取音频下载地址：" + audioDownloadUrl);
				String md5 = DigestUtils.md5Hex(path[1] + System.currentTimeMillis());
				File video = ffmpegSuccess == -1 ? new File(path[0], path[1] + ".mp4") : new File(path[0], "tmpVid_" + md5 + ".mp4");
				File audio = ffmpegSuccess == -1 ? new File(path[0], path[1] + ".aac") : new File(path[0], "tmpAud_" + md5 + ".aac");
				System.out.println("\n正在下载视频至 " + video.getAbsolutePath());
				long lenVid = downloadFromUrl(videoDownloadUrl, video.getAbsolutePath());
				videoSuccess = video.length() == lenVid;
//				System.out.println(video.length() + " == " + lenVid); // debug
				System.out.println(videoSuccess ? "\n视频下载完毕" : "\n视频下载失败");
				System.out.println("\n正在下载音频至 " + audio.getAbsolutePath());
				long lenAud = downloadFromUrl(audioDownloadUrl, audio.getAbsolutePath());
				audioSuccess = audio.length() == lenAud;
				System.out.println(audioSuccess ? "\n音频下载完毕" : "\n音频下载失败");
				if (videoSuccess && audioSuccess && ffmpegSuccess == 1 && !ffmpeg.getName().equals("null")) {
					System.out.println("\n正在合并至 " + new File(path[0], path[1] + ".mp4").getAbsolutePath());
					File file = merge(ffmpeg, video, audio, new File(path[0], path[1] + ".mp4"));
					if (file != null) {
						System.out.println("合并完毕");
						video.deleteOnExit();
						audio.deleteOnExit();
					} else {
						System.out.println("合并失败");
					}
				} else {
					System.out.println("\n合并失败");
				}
				break;
			}
			case 2: {
				boolean videoSuccess;
				videoDownloadUrl = validateVideoUrl(videoDownloadUrl, qualities, quality, videoWeb);
				File video = new File(path[0], path[1] + ".mp4");
				System.out.println("\n正在下载至 " + video.getAbsolutePath());
				long len = downloadFromUrl(videoDownloadUrl, video.getAbsolutePath());
				videoSuccess = video.length() == len;
				System.out.println(videoSuccess ? "\n下载完毕" : "\n下载失败");
				break;
			}
			case 3: {
				boolean audioSuccess;
				String audioDownloadUrl = getAudioDownload(videoWeb);
				System.out.println("\n成功获取音频下载地址：" + audioDownloadUrl);
				File audio = new File(path[0], path[1] + ".aac");
				System.out.println("\n正在下载至 " + audio.getAbsolutePath());
				long len = downloadFromUrl(audioDownloadUrl, audio.getAbsolutePath());
				audioSuccess = audio.length() == len;
				System.out.println(audioSuccess ? "\n下载完毕" : "\n下载失败");
				break;
			}
		}
	}

	private static String validateVideoUrl(String videoDownloadUrl, JSONArray qualities, int quality, JSONObject videoWeb) {
		if (videoDownloadUrl == null) {
			System.out.print("\n无法获取 " + qualities.getString(quality).replaceAll(" +", " ") + " 的视频下载地址，已为您选择目前可用的最佳清晰度 " + getQualityDescription(videoWeb, videoWeb.getJSONObject("dash").getJSONArray("video").getJSONObject(0).getIntValue("id")));
			videoDownloadUrl = videoWeb.getJSONObject("dash").getJSONArray("video").getJSONObject(0).getString("base_url");
			System.out.println("；下载地址：" + videoDownloadUrl);
		} else {
			System.out.println("\n成功获取 " + qualities.getString(quality).replaceAll(" +", " ") + " 的视频下载地址：" + videoDownloadUrl);
		}
		return videoDownloadUrl;
	}

	private static String getFormattedTime(int time, boolean hour) {
		String result = "";
		if (hour) {
			result += time / 3600 + ":";
		}
		String min = (time % 3600) / 60 + "";
		result += (min.length() < 2 ? "0" + min : min) + ":";
		String sec = time % 60 + "";
		result += sec.length() < 2 ? "0" + sec : sec;
		return result;
	}

	private static String getFormattedTime(long time) {
		SimpleDateFormat ft = new SimpleDateFormat("mm:ss.SS");
		return ft.format(time);
	}

	private static File merge(File ffmpegExecutable, File video, File audio, File output) throws IOException, InterruptedException {
		for (File f : Arrays.asList(ffmpegExecutable, video, audio)) {
			if (!f.exists()) {
				System.out.println("指定的文件“" + f.getAbsolutePath() + "”不存在");
				return null;
			}
		}
		File disk = Paths.get(output.getAbsolutePath()).getRoot().toFile().getAbsoluteFile();
		if (disk.getUsableSpace() < video.length() + audio.length()) {
			System.out.println("磁盘空间不足");
			return null;
		}
		if (output.exists())
			//noinspection ResultOfMethodCallIgnored
			output.delete();
		ProcessBuilder builder = new ProcessBuilder(
				ffmpegExecutable.getAbsolutePath(),
				"-i", video.getAbsolutePath(),
				"-i", audio.getAbsolutePath(),
				"-vcodec", "copy",
				"-acodec", "copy",
				output.getAbsolutePath()
		);
		builder.redirectErrorStream(true);
		Process process = builder.start();
		process.waitFor();
		if (!output.exists()) {
			return null;
		}
		return output;
	}

	private static JSONArray summarize(JSONArray qualitiesTV, JSONArray qualitiesWeb, JSONObject videoTV) {
		boolean watermark = true;
		if (videoTV.getIntValue("code") == 0) {
			JSONArray watermarks = videoTV.getJSONArray("accept_watermark");
			for (int i = 0; i < watermarks.size(); i++) {
				if (!watermarks.getBoolean(i)) {
					watermark = false;
					break;
				}
			}
		}
		JSONArray qualities = new JSONArray();
		if (!watermark) {
			qualities.add(1);
			for (int i = 0; i < qualitiesTV.size(); i++)
				if (!videoTV.getJSONArray("accept_watermark").getBoolean(i))
					qualities.add(String.format("%-11s", qualitiesTV.getString(i)) + watermark(videoTV.getJSONArray("accept_watermark").getBoolean(i)));
				else
					qualities.add(qualitiesTV.getString(i));
		} else {
			qualities.add(0);
		}
		for (int i = 0; i < qualitiesWeb.size(); i++)
			qualities.add(qualitiesWeb.getString(i));
		return qualities;
	}

	private static String getQualityDescription(JSONObject video, int qNum) {
		JSONArray descriptions = video.getJSONArray("accept_description");
		JSONArray qualityNums = video.getJSONArray("accept_quality");
		for (int i = 0; i < qualityNums.size(); i++) {
			if (qualityNums.getIntValue(i) == qNum) {
				return descriptions.getString(i);
			}
		}
		return null;
	}

	private static String getVideoDownload(JSONObject video, int quality) {
		int qualityNum = video.getJSONArray("accept_quality").getIntValue(quality);
		JSONArray videos = video.getJSONObject("dash").getJSONArray("video");
		for (int i = 0; i < videos.size(); i++) {
			if (videos.getJSONObject(i).getIntValue("id") == qualityNum) {
				return videos.getJSONObject(i).getString("base_url");
			}
		}
		return null;
	}

	private static String getAudioDownload(JSONObject video) {
		JSONArray audios = video.getJSONObject("dash").getJSONArray("audio");
		return audios.getJSONObject(0).getString("base_url");
	}

	private static String watermark(boolean availability) {
		if (availability) return "";
		else return "无水印";
	}

	private static long downloadFromUrl(String address, String path, List<Map.Entry<Long, Long>> status, int tries) throws IOException {
		Downloader downloader = new Downloader(address, path, status);
		long remainingSizeLen = calcRemainingSize(status);
		int threadAmount = status.size();
		double remainingSize = remainingSizeLen / 1024.0 / 1024.0;
		System.out.println("\n剩余文件大小：" + String.format("%,.3f", remainingSize) + (debug ? "MB（" + remainingSizeLen + "B）" : "MB"));
		System.out.println("下载所用线程数：" + threadAmount);
		System.out.println("本次是第" + tries + "次重试" + (tries > 3 ? "，若数次下载失败请考虑强制退出程序" : ""));
		long beginTime = System.currentTimeMillis();
		short result = downloader.download();
		if (result == -1) {
			System.out.println("磁盘空间不足");
			return -1;
		}
		if (result == -2) {
			System.out.println("线程详情为空");
			return -2;
		}
		StringBuilder progress = new StringBuilder();
		long downloadedLen = downloader.getDownloaded(), time;
		Deque<Long> prevLensInTenSec = new LinkedList<>(), prevLensInHalfSec = new LinkedList<>();
		double downloaded, speed, averageSpeed, instantaneousSpeed;

		while (downloadedLen != remainingSizeLen) {
			time = System.currentTimeMillis() - beginTime;
			downloadedLen = downloader.getDownloaded();
			if (debug && downloadedLen < Math.min(remainingSizeLen / 500, 32)) {
				continue;
			}
			for (int i = 0; i < threadAmount; i++) {
				if (downloader.isInterrupted(downloader.getThreads().get(i))) {
					return -1;
				}
			}
			downloaded = downloadedLen / 1024.0 / 1024.0;
			prevLensInTenSec.addLast(downloadedLen);
			prevLensInHalfSec.addLast(downloadedLen);
			speed = (time / 1000 == 0) ? 0 : (time > 10000 ? downloaded - (prevLensInTenSec.getFirst() / 1024.0 / 1024.0) : downloaded) / (time > 10000 ? 10 : time / 1000.0);
			instantaneousSpeed = (time / 100 == 0) ? 0 : (time > 500 ? downloaded - (prevLensInHalfSec.getFirst() / 1024.0 / 1024.0) : downloaded) / (time > 500 ? 0.5 : time / 1000.0);
			averageSpeed = (time / 1000 == 0) ? 0 : downloaded / (time / 1000.0);
			if (time > 10000) {
				prevLensInTenSec.removeFirst();
				if (speed == 0) {
					System.out.println("\n下载中断，已下载 " + String.format("%,.3f", downloaded) + (debug ? "MB（" + downloadedLen + "B）" : "MB") + "；正在尝试继续下载");
//					System.out.println(downloadedLen + " != " + remainingSizeLen); // debug
					return downloadedLen + downloadFromUrl(address, path, downloader.cancel(), tries + 1);
				}
			}
			if (time > 500 && prevLensInHalfSec.size() > 0) {
				prevLensInHalfSec.removeFirst();
			}

			int lastLength = progress.length();
			int lastByteLength = progress.toString().getBytes().length;
			if (progress.length() > 0) {
				for (int i = 0; i < lastByteLength; i++)
					System.out.print("\b");
			}

			progress = new StringBuilder("进度：" + String.format("%.2f", (downloadedLen * 100.0 / remainingSizeLen)) + "%（" + String.format("%,.3f", downloaded) + "MB / " + String.format("%,.3f", remainingSize) + "MB）；瞬时速度：" + String.format("%,.3f", instantaneousSpeed) + "MB/s；平均速度：" + String.format("%,.3f", averageSpeed) + "MB/s；剩余时间：" + String.format("%,.3f", (remainingSize - downloaded) / averageSpeed).replace("Infinity", "∞") + "s");
			for (int i = 0; i <= lastLength - progress.length(); i++)
				progress.append(" ");
			System.out.print(progress);
		}

		System.out.print("\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b");
		String timeSpent = "用时：" + String.format("%,.3f", (System.currentTimeMillis() - beginTime) / 1000.0) + "s";
		System.out.print(timeSpent);
		for (int i = 0; i < 13 - timeSpent.length(); i++)
			System.out.print(" ");
		return remainingSizeLen;
	}

	private static long downloadFromUrl(String address, String path) throws IOException {
		URLConnection request = (new URL(address)).openConnection();
		request.setRequestProperty("Referer", "https://www.bilibili.com");
		long totalLen = request.getContentLengthLong();
		int threadAmount;
		if (totalLen < 8388608L) {
			threadAmount = (int) totalLen / 1024 / 1024;
			if (threadAmount < 1) {
				threadAmount = 1;
			}
		} else {
			threadAmount = 8;
			boolean threadAmountSuccess = false;
			if (config.exists()) {
				ConfigManager.init(config);
				Map<String, Object> map = ConfigManager.get();
				if (map == null)
					map = new LinkedHashMap<>();
				if (map.containsKey("thread-amount")) {
					threadAmount = (Integer) map.get("thread-amount");
					if (threadAmount >= 1) {
						threadAmountSuccess = true;
						if (debug) System.out.println("\n成功获取下载所用线程数：" + threadAmount);
					}
				}
			}
			if (!threadAmountSuccess) {
				if (hint) System.out.println("\n请决定下载所用线程数（输入 1~N 之间的整数，N 不定，且过大可能导致416错误）：");
				threadAmount = inputInt();
				if (threadAmount < 1) {
					System.out.println("输入的数字“" + threadAmount + "”太小，已为您决定使用单线程下载");
					threadAmount = 1;
				}
				if (hint) System.out.println("请决定是否保存下载所用线程数（输入“Y”代表是，输入“N”代表否）：");
				if (input().equalsIgnoreCase("Y")) {
					if (!config.exists()) config.createNewFile();
					ConfigManager.init(config);
					Map<String, Object> map = ConfigManager.get();
					if (map == null)
						map = new LinkedHashMap<>();
					map.put("thread-amount", threadAmount);
					ConfigManager.dump(map);
					if (hint) System.out.println("已保存下载所用线程数");
				}
			}
		}
		Downloader downloader = new Downloader(address, path, threadAmount);
		double total = totalLen / 1024.0 / 1024.0;
		System.out.println("文件大小：" + String.format("%,.3f", total) + (debug ? "MB（" + totalLen + "B）" : "MB"));
		System.out.println("下载所用线程数：" + threadAmount);
		long beginTime = System.currentTimeMillis();
		long result = downloader.download(totalLen);
		if (result == -1) {
			System.out.println("磁盘空间不足");
			return -1;
		}
		StringBuilder progress = new StringBuilder();
		long downloadedLen = downloader.getDownloaded(), time;
		Deque<Long> prevLensInTenSec = new LinkedList<>(), prevLensInHalfSec = new LinkedList<>();
		double downloaded, speed, averageSpeed, instantaneousSpeed;

		while (downloadedLen != totalLen) {
			time = System.currentTimeMillis() - beginTime;
			downloadedLen = downloader.getDownloaded();
			if (debug && downloadedLen < Math.min(totalLen / 500, 32)) {
				continue;
			}
			for (int i = 0; i < threadAmount; i++) {
				if (downloader.isInterrupted(downloader.getThreads().get(i))) {
					return -1;
				}
			}
			downloaded = downloadedLen / 1024.0 / 1024.0;
			prevLensInTenSec.addLast(downloadedLen);
			prevLensInHalfSec.addLast(downloadedLen);
			speed = (time / 1000 == 0) ? 0 : (time > 10000 ? downloaded - (prevLensInTenSec.getFirst() / 1024.0 / 1024.0) : downloaded) / (time > 10000 ? 10 : time / 1000.0);
//			System.out.println("prevLensInHalfSec: " + prevLensInHalfSec.size() + ", first = " + prevLensInHalfSec.getFirst() + ", last = " + prevLensInHalfSec.getLast());
//			System.out.print("Speed = " + (time > 10000 ? downloaded - (prevLensInTenSec.getFirst() / 1024.0 / 1024.0) : downloaded) + " / " + (time > 10000 ? 10 : time / 1000.0) + " = " + speed);
			instantaneousSpeed = (time / 100 == 0) ? 0 : (time > 500 ? downloaded - (prevLensInHalfSec.getFirst() / 1024.0 / 1024.0) : downloaded) / (time > 500 ? 0.5 : time / 1000.0);
//			System.out.println(" Instantaneous Speed = (" + downloaded + " - " + (prevLensInHalfSec.getFirst() / 1024.0 / 1024.0) + ") / " + (time > 500 ? 0.5 : time / 1000.0) + " = " + instantaneousSpeed);
			averageSpeed = (time / 1000 == 0) ? 0 : downloaded / (time / 1000.0);
//			System.out.println(" Average Speed = " + downloaded + " / " + time / 1000.0);
			if (time > 10000) {
				prevLensInTenSec.removeFirst();
				if (speed == 0) {
					System.out.println("\n下载中断，已下载 " + String.format("%,.3f", downloaded) + (debug ? "MB（" + downloadedLen + "B）" : "MB") + "；正在尝试继续下载");
					return downloadedLen + downloadFromUrl(address, path, downloader.cancel(), 1);
				}
			}
			if (time > 500 && prevLensInHalfSec.size() > 0) {
				prevLensInHalfSec.removeFirst();
			}

			int lastLength = progress.length();
			int lastByteLength = progress.toString().getBytes().length;
			if (progress.length() > 0) {
				for (int i = 0; i < lastByteLength; i++)
					System.out.print("\b");
			}

			progress = new StringBuilder("进度：" + String.format("%.2f", (downloadedLen * 100.0 / totalLen)) + "%（" + String.format("%,.3f", downloaded) + "MB / " + String.format("%,.3f", total) + "MB）；瞬时速度：" + String.format("%,.3f", instantaneousSpeed) + "MB/s；平均速度：" + String.format("%,.3f", averageSpeed) + "MB/s；剩余时间：" + String.format("%,.3f", (total - downloaded) / averageSpeed).replace("Infinity", "∞") + "s");
			for (int i = 0; i <= lastLength - progress.length(); i++)
				progress.append(" ");
			System.out.print(progress);
		}

		System.out.print("\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b\b");
		String timeSpent = "用时：" + String.format("%,.3f", (System.currentTimeMillis() - beginTime) / 1000.0) + "s";
		System.out.print(timeSpent);
		for (int i = 0; i < 13 - timeSpent.length(); i++)
			System.out.print(" ");
		return totalLen;
	}

	private static long calcRemainingSize(List<Map.Entry<Long, Long>> status) {
		long toDownload = 0L;
		for (Map.Entry<Long, Long> entry : status) {
			toDownload += entry.getValue() - entry.getKey();
		}
//		System.out.println("Calculated remainingSize: " + toDownload); // debug
		return toDownload;
	}

}