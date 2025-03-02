package loghub.encoders;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;

import loghub.BuilderClass;
import loghub.Event;
import lombok.Setter;

@BuilderClass(Gelf.Builder.class)
public class Gelf extends Encoder {

    public static class Builder extends Encoder.Builder<Gelf> {
        private boolean compressed = false;
        private boolean stream = false;
        @Setter
        private String shortmessagefield = "shortmessage";
        @Setter
        private String fullmessagefield = null;
        public Builder setCompressed(Boolean compressed) {
            this.compressed = compressed;
            this.stream = compressed ? false : this.stream;
            return this;
        }
        public Builder setStream(Boolean stream) {
            this.stream = stream;
            this.compressed = stream ? false: this.compressed;
            return this;
        }
        @Override
        public Gelf build() {
            return new Gelf(this);
        }
    };
    public static Builder getBuilder() {
        return new Builder();
    }

    private static final Pattern fieldpattner = Pattern.compile("^[\\w\\.\\-]*$");
    private static final Predicate<String> fieldpredicate = fieldpattner.asPredicate();
    private static final String hostname;
    static {
        try {
            hostname = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

    private static final JsonFactory factory = new JsonFactory();
    private static final ThreadLocal<ObjectMapper> json = ThreadLocal.withInitial(() ->new ObjectMapper(factory));

    private final boolean compressed;
    private final boolean stream;
    private final String shortmessagefield;
    private final String fullmessagefield;

    private Gelf(Builder builder) {
        super(builder);
        this.compressed = builder.compressed;
        this.stream = builder.stream;
        this.shortmessagefield = builder.shortmessagefield;
        this.fullmessagefield = builder.fullmessagefield;
    }

    @Override
    public byte[] encode(Event event) {
        try {
            Map<String, Object> gelfcontent = new HashMap<>(event.size() + 5);
            gelfcontent.put("version", "1.1");
            gelfcontent.put("host", hostname);
            if (event.containsKey(shortmessagefield)) {
                gelfcontent.put("short_message", event.remove(shortmessagefield));
            }
            if (fullmessagefield != null && event.containsKey(fullmessagefield)) {
                gelfcontent.put("full_message", event.remove(fullmessagefield));
            }
            gelfcontent.put("timestamp", event.getTimestamp().getTime() / 1000.0);
            event.entrySet().stream()
            .filter( i ->  ! "id".equals(i.getKey()))
            .filter( i -> fieldpredicate.test(i.getKey()))
            .forEach( i -> gelfcontent.put( "_" + i.getKey(), i.getValue()));
            byte[] buffer1 = json.get().writeValueAsBytes(gelfcontent);
            byte[] buffer2;
            if (compressed) {
                try (final ByteArrayOutputStream bos = new ByteArrayOutputStream();
                                final GZIPOutputStream stream = new GZIPOutputStream(bos)) {

                    stream.write(buffer1);
                    stream.finish();
                    buffer2 = bos.toByteArray();
                }
            } else if (stream) {
                buffer2 = Arrays.copyOf(buffer1, buffer1.length+1);
            } else {
                buffer2 = buffer1;
            }
            return buffer2;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
