import java.nio.file.Path;
import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import org.xml.sax.SAXParseException;

/** Run with Java 11+: java viewer/ValidateProcessXml.java schema.xsd model.process.xml ... */
public class ValidateProcessXml {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: java ValidateProcessXml.java schema.xsd model.process.xml ...");
            System.exit(2);
        }
        var factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        var schema = factory.newSchema(Path.of(args[0]).toFile());
        boolean failed = false;
        for (int i = 1; i < args.length; i++) {
            var validator = schema.newValidator();
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            try {
                validator.validate(new StreamSource(Path.of(args[i]).toFile()));
                System.out.println("PASS " + args[i]);
            } catch (SAXParseException ex) {
                failed = true;
                System.err.println("FAIL " + args[i] + ":" + ex.getLineNumber() + ":" + ex.getColumnNumber() + " " + ex.getMessage());
            } catch (Exception ex) {
                failed = true;
                System.err.println("FAIL " + args[i] + " " + ex.getMessage());
            }
        }
        if (failed) System.exit(1);
    }
}
