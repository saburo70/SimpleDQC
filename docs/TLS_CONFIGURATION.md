# TLS/SSL Configuration

Enabling HTTPS ensures that all communication between the browser and the SimpleDQC service is encrypted. Since SimpleDQC is built on Spring Boot, configuring TLS is straightforward.

---

## 1. Generate a Keystore

To enable TLS, you need a certificate. For production, you should use a certificate from a trusted Certificate Authority (CA). For development or internal use, you can generate a self-signed certificate using `keytool`.

Run the following command in your terminal:

```bash
keytool -genkeypair -alias simpledqc -keyalg RSA -keysize 2048 -storetype PKCS12 -keystore keystore.p12 -validity 3650
```

- **Password**: You will be prompted for a password. Remember it, as you'll need it for the configuration.
- **File**: This creates a `keystore.p12` file in your current directory. Move this file to `src/main/resources/` or a secure location on your server.

---

## 2. Configure application.yaml

Update your `src/main/resources/application.yaml` (or your external configuration file) with the following settings:

```yaml
server:
  port: 8443 # Standard port for HTTPS
  ssl:
    enabled: true
    key-store: classpath:keystore.p12 # or file:/path/to/keystore.p12
    key-store-password: your_keystore_password
    key-store-type: PKCS12
    key-alias: simpledqc
```

---

## 3. (Optional) HTTP to HTTPS Redirection

If you want to automatically redirect users from HTTP (port 8080) to HTTPS (port 8443), you need to add a small configuration class to the project:

```java
@Configuration
public class HttpToHttpsConfig {

    @Bean
    public ServletWebServerFactory servletContainer() {
        TomcatServletWebServerFactory tomcat = new TomcatServletWebServerFactory() {
            @Override
            protected void postProcessContext(Context context) {
                SecurityConstraint securityConstraint = new SecurityConstraint();
                securityConstraint.setUserConstraint("CONFIDENTIAL");
                SecurityCollection collection = new SecurityCollection();
                collection.addPattern("/*");
                securityConstraint.addCollection(collection);
                context.addConstraint(securityConstraint);
            }
        };
        tomcat.addAdditionalTomcatConnectors(redirectConnector());
        return tomcat;
    }

    private Connector redirectConnector() {
        Connector connector = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
        connector.setScheme("http");
        connector.setPort(8080);
        connector.setSecure(false);
        connector.setRedirectPort(8443);
        return connector;
    }
}
```

---

## 4. Verification

After restarting the application, access the dashboard at:
`https://localhost:8443`

*Note: If using a self-signed certificate, your browser will show a security warning. You will need to click "Advanced" and "Proceed" to access the site.*
