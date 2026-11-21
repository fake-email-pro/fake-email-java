package pro.fakeemail;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** Reads the SDK version that the build wrote into {@code version.properties} from pom.xml. */
final class Version {
    private Version() {}

    static String read() {
        try (InputStream in = Version.class.getResourceAsStream("version.properties")) {
            if (in != null) {
                Properties props = new Properties();
                props.load(in);
                String version = props.getProperty("version");
                if (version != null && !version.isEmpty() && !version.startsWith("${")) {
                    return version;
                }
            }
        } catch (IOException e) {
            // Fall through: a missing version must not break the client.
        }
        return "unknown";
    }
}
