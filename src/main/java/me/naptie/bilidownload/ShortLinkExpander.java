package me.naptie.bilidownload;

import org.apache.commons.lang.StringUtils;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

/**
 * 短链接解析器
 */
public class ShortLinkExpander {

    public static void main(String[] args) {
        try {
            // https://v.douyin.com/ijwjqPpK
            // https://b23.tv/2CLIMFe
            String shortLink = "https://v.douyin.com/ijwjqPpK"; // 这里替换为你的短链接
            String realUrl = getRealUrl(shortLink);
            System.out.println("短链接 '" + shortLink + "' 的真实地址是: " + realUrl);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 获取短链接的真实URL地址。
     * 
     * @param shortLink 短链接地址。
     * @return 真实的URL地址，获取失败则返回null。
     * @throws IOException 如果发生网络I/O错误。
     */
    public static String getRealUrl(String shortLink) throws IOException {
        URI uri = URI.create(shortLink);

        // 提取有效的URL部分，获取方案（scheme）和主机（host）以构造所需字符串
        String TEMP_URL_PREFIX = uri.getScheme() + "://" + uri.getHost();

        HttpURLConnection connection = null;
        try {
            // 创建URL对象
            URL url = new URL(shortLink);
            
            // 打开连接
            connection = (HttpURLConnection) url.openConnection();
            
            // 设置跟随重定向
            connection.setInstanceFollowRedirects(false);
            
            // 发送GET请求
            connection.setRequestMethod("GET");
            connection.connect();
            
            // 获取响应码
            int responseCode = connection.getResponseCode();
            
            // 如果响应码是3xx，表示重定向
            if(responseCode >= 300 && responseCode <= 399) {
                // 从响应头中获取Location字段，即重定向的URL
                String newUrl = connection.getHeaderField("Location");
                if (StringUtils.isBlank(newUrl)) {
                    return null;
                }

                if (!newUrl.startsWith("http")) {
                    // 有的Location没有域名，需要加上域名
                    newUrl = TEMP_URL_PREFIX + newUrl;
                }

                // 对新的URL递归调用此方法，直到找到最终的真实地址
                return getRealUrl(newUrl);
            } else {
                // 如果不是重定向，直接返回当前的URL
                return url.toString();
            }
        } finally {
            if(connection != null) {
                connection.disconnect(); // 关闭连接
            }
        }
    }
}
