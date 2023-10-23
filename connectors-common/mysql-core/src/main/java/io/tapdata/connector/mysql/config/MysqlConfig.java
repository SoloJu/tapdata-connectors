package io.tapdata.connector.mysql.config;

import io.tapdata.common.CommonDbConfig;
import io.tapdata.common.util.FileUtil;
import io.tapdata.kit.EmptyKit;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MysqlConfig extends CommonDbConfig {

    public MysqlConfig() {
        setDbType("mysql");
        setEscapeChar('`');
        setJdbcDriver("com.mysql.cj.jdbc.Driver");
    }

    private static final Map<String, String> DEFAULT_PROPERTIES = new HashMap<String, String>() {{
        put("rewriteBatchedStatements", "true");
        put("useCursorFetch", "true");
        put("useSSL", Boolean.FALSE.toString());
        put("zeroDateTimeBehavior", "convertToNull");
        put("allowPublicKeyRetrieval", "true");
        put("useTimezone", Boolean.FALSE.toString());
        put("tinyInt1isBit", Boolean.FALSE.toString());
        put("autoReconnect", "true");
    }};

    @Override
    public MysqlConfig load(Map<String, Object> map) {
        MysqlConfig config = (MysqlConfig) super.load(map);
        setUser(EmptyKit.isBlank(getUser()) ? (String) map.get("username") : getUser());
        setExtParams(EmptyKit.isBlank(getExtParams()) ? (String) map.get("addtionalString") : getExtParams());
        setSchema(getDatabase());
        return config;
    }

    @Override
    public String getConnectionString() {
        return getHost() + ":" + getPort() + "/" + getDatabase();
    }

    @Override
    public String getDatabaseUrl() {
        String additionalString = getExtParams();
        additionalString = null == additionalString ? "" : additionalString.trim();
        if (additionalString.startsWith("?")) {
            additionalString = additionalString.substring(1);
        }
        StringBuilder sbURL = new StringBuilder("jdbc:").append(getDbType()).append("://").append(getHost()).append(":").append(getPort()).append("/").append(getDatabase());

        if (StringUtils.isNotBlank(additionalString)) {
            String[] additionalStringSplit = additionalString.split("&");
            for (String s : additionalStringSplit) {
                String[] split = s.split("=");
                if (split.length == 2) {
                    properties.put(split[0], split[1]);
                }
            }
        }
        for (String defaultKey : DEFAULT_PROPERTIES.keySet()) {
            if (properties.containsKey(defaultKey)) {
                continue;
            }
            properties.put(defaultKey, DEFAULT_PROPERTIES.get(defaultKey));
        }

        if (StringUtils.isNotBlank(timezone)) {
            timezone = "GMT" + timezone;
            String serverTimezone = timezone.replace("+", "%2B").replace(":00", "");
            properties.put("serverTimezone", serverTimezone);
        }
        StringBuilder propertiesString = new StringBuilder();
        properties.forEach((k, v) -> propertiesString.append("&").append(k).append("=").append(v));

        if (propertiesString.length() > 0) {
            additionalString = StringUtils.removeStart(propertiesString.toString(), "&");
            sbURL.append("?").append(additionalString);
        }

        return sbURL.toString();
    }

    @Override
    public void generateSSlFile() throws IOException, InterruptedException {
        //SSL开启需要的URL属性
        properties.put("useSSL", "true");
        properties.put("requireSSL", "true");
        //每个config都用随机路径
        sslRandomPath = UUID.randomUUID().toString().replace("-", "");
        //如果所有文件都没有上传，表示不验证证书，直接结束
        if (EmptyKit.isBlank(getSslCa()) && EmptyKit.isBlank(getSslCert()) && EmptyKit.isBlank(getSslKey())) {
            properties.put("verifyServerCertificate", "false");
            return;
        }
        properties.put("verifyServerCertificate", "true");
        //如果CA证书有内容，表示需要验证CA证书，导入truststore.jks
        if (EmptyKit.isNotBlank(getSslCa())) {
            String sslCaPath = FileUtil.paths(FileUtil.storeDir(".ssl"), sslRandomPath, "ca.pem");
            FileUtil.save(Base64.getUrlDecoder().decode(getSslCa()), sslCaPath, true);
            Runtime.getRuntime().exec("keytool -import -noprompt -file " + FileUtil.paths(FileUtil.storeDir(".ssl"), sslRandomPath, "ca.pem") +
                    " -keystore " + FileUtil.paths(FileUtil.storeDir(".ssl"), sslRandomPath, "truststore.jks") + " -storepass 123456").waitFor();
            properties.put("trustCertificateKeyStoreUrl", "file:" + FileUtil.paths(FileUtil.storeDir(".ssl"), sslRandomPath, "truststore.jks"));
            properties.put("trustCertificateKeyStorePassword", "123456");
        }
        //如果客户端证书有内容，表示需要验证客户端证书，导入keystore.jks
        if (EmptyKit.isNotBlank(getSslCert()) && EmptyKit.isNotBlank(getSslKey())) {
            String sslCertPath = FileUtil.paths(FileUtil.storeDir(".ssl"), sslRandomPath, "cert.pem");
            FileUtil.save(Base64.getUrlDecoder().decode(getSslCert()), sslCertPath, true);
            String sslKeyPath = FileUtil.paths(FileUtil.storeDir(".ssl"), sslRandomPath, "key.pem");
            FileUtil.save(Base64.getUrlDecoder().decode(getSslKey()), sslKeyPath, true);
            Runtime.getRuntime().exec("openssl pkcs12 -legacy -export -in " + FileUtil.paths(FileUtil.storeDir(".ssl"), sslRandomPath, "cert.pem") +
                    " -inkey " + FileUtil.paths(FileUtil.storeDir(".ssl"), sslRandomPath, "key.pem") +
                    " -name datasource-client -passout pass:123456 -out " + FileUtil.paths(FileUtil.storeDir(".ssl"), sslRandomPath, "client-keystore.p12")).waitFor();
            Runtime.getRuntime().exec("keytool -importkeystore -srckeystore " + FileUtil.paths(FileUtil.storeDir(".ssl"), sslRandomPath, "client-keystore.p12") +
                    " -srcstoretype pkcs12 -srcstorepass 123456 -destkeystore " + FileUtil.paths(FileUtil.storeDir(".ssl"), sslRandomPath, "keystore.jks") + " -deststoretype JKS -deststorepass 123456").waitFor();
            properties.put("clientCertificateKeyStoreUrl", "file:" + FileUtil.paths(FileUtil.storeDir(".ssl"), sslRandomPath, "keystore.jks"));
            properties.put("clientCertificateKeyStorePassword", "123456");
        }
        if (EmptyKit.isNotBlank(getSslKeyPassword())) {
            properties.put("clientKeyPassword", getSslKeyPassword());
        }
    }

    protected String timezone;

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }
}
