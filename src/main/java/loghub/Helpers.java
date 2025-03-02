package loghub;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.activation.MimetypesFileTypeMap;

import org.apache.logging.log4j.Logger;

import io.netty.util.NetUtil;

public final class Helpers {

    private Helpers() {

    }

    @FunctionalInterface
    public interface TriFunction<S, T, U, R> {
        R apply(S s, T t, U u);
    }

    @FunctionalInterface
    public interface TriConsumer<S, T, U> {
        void accept(S s, T t, U u);
    }

    @FunctionalInterface
    public interface Actor {
        void act();
    }

    @FunctionalInterface
    public interface ThrowingPredicate<T> extends Predicate<T> {

        @Override
        default boolean test(final T elem) {
            try {
                return testThrows(elem);
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        }

        boolean testThrows(T elem) throws Exception;

    }

    @FunctionalInterface
    public interface ThrowingConsumer<T> extends Consumer<T> {

        @Override
        default void accept(final T elem) {
            try {
                acceptThrows(elem);
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        }

        void acceptThrows(T elem) throws Exception;

    }

    @FunctionalInterface
    public interface ThrowingFunction<T, R> extends Function<T, R> {

        @Override
        default R apply(final T elem) {
            try {
                return applyThrows(elem);
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        }

        R applyThrows(T elem) throws Exception;

    }

    @FunctionalInterface
    public interface ThrowingBiFunction<T, U, R> extends BiFunction<T, U, R> {

        @Override
        default R apply(final T elem1, final U elem2) {
            try {
                return applyThrows(elem1, elem2);
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        }

        R applyThrows(final T elem1, final U elem2) throws Exception;
    }

    public static <E> Iterable<E> enumIterable(Enumeration<E> e){
        return new Iterable<E>() {
            @Override
            public Iterator<E> iterator() {
                return new Iterator<E>() {
                    @Override public boolean hasNext() {
                        return e.hasMoreElements();
                    }
                    @Override public E next() {
                        return e.nextElement();
                    }
                };
            }
        };
    }

    public static <T> Stream<T> iteratorToStream(Iterator<T> iterator) {
        Iterable<T> iterable = () -> iterator;
        return StreamSupport.stream(iterable.spliterator(), false);
    }

    public static void readRessources(ClassLoader loader, String lookingfor, Consumer<InputStream> reader) throws IOException, URISyntaxException {
        List<URL> patternsUrls = Collections.list(loader.getResources(lookingfor));
        for(URL url: patternsUrls) {
            URLConnection cnx = url.openConnection();
            if (cnx instanceof JarURLConnection) {
                JarURLConnection jarcnx = (JarURLConnection) cnx;
                JarFile jarfile = jarcnx.getJarFile();

                Helpers.ThrowingFunction<JarEntry, InputStream> openner = i-> jarfile.getInputStream(i);

                StreamSupport.stream(Helpers.enumIterable(jarfile.entries()).spliterator(), false)
                .filter( i -> i.getName().startsWith(lookingfor))
                .map(openner)
                .forEach(reader);
            }
            else if ("file".equals(url.getProtocol())) {
                Path p = Paths.get(url.toURI());
                if ( p.toFile().isDirectory()) {
                    try (DirectoryStream<Path> ds = Files.newDirectoryStream(p)) {
                        for (Path entry: ds) {
                            InputStream is = new FileInputStream(entry.toFile());
                            reader.accept(is);
                        }
                    }
                }
            } else {
                throw new RuntimeException("cant load ressource at " + url);
            }
        }
    }

    /**
     * Taken from http://stackoverflow.com/questions/1247772/is-there-an-equivalent-of-java-util-regex-for-glob-type-patterns
     * 
     * Converts a standard POSIX Shell globbing pattern into a regular expression
     * pattern. The result can be used with the standard {@link java.util.regex} API to
     * recognize strings which match the glob pattern.
     * <p>
     * See also, the POSIX Shell language:
     * http://pubs.opengroup.org/onlinepubs/009695399/utilities/xcu_chap02.html#tag_02_13_01
     * 
     * @param pattern A glob pattern.
     * @return A regex pattern to recognize the given glob pattern.
     */
    public static final Pattern convertGlobToRegex(String pattern) {
        StringBuilder sb = new StringBuilder(pattern.length());
        int inGroup = 0;
        int inClass = 0;
        int firstIndexInClass = -1;
        char[] arr = pattern.toCharArray();
        for (int i = 0; i < arr.length; i++) {
            char ch = arr[i];
            switch (ch) {
            case '\\':
                if (++i >= arr.length) {
                    sb.append('\\');
                } else {
                    char next = arr[i];
                    switch (next) {
                    case ',':
                        // escape not needed
                        break;
                    case 'Q':
                    case 'E':
                        // extra escape needed
                        sb.append('\\');
                    default:
                        sb.append('\\');
                    }
                    sb.append(next);
                }
                break;
            case '*':
                if (inClass == 0)
                    sb.append(".*");
                else
                    sb.append('*');
                break;
            case '?':
                if (inClass == 0)
                    sb.append('.');
                else
                    sb.append('?');
                break;
            case '[':
                inClass++;
                firstIndexInClass = i+1;
                sb.append('[');
                break;
            case ']':
                inClass--;
                sb.append(']');
                break;
            case '.':
            case '(':
            case ')':
            case '+':
            case '|':
            case '^':
            case '$':
            case '@':
            case '%':
                if (inClass == 0 || (firstIndexInClass == i && ch == '^'))
                    sb.append('\\');
                sb.append(ch);
                break;
            case '!':
                if (firstIndexInClass == i)
                    sb.append('^');
                else
                    sb.append('!');
                break;
            case '{':
                inGroup++;
                sb.append('(');
                break;
            case '}':
                inGroup--;
                sb.append(')');
                break;
            case ',':
                if (inGroup > 0)
                    sb.append('|');
                else
                    sb.append(',');
                break;
            default:
                sb.append(ch);
            }
        }
        return Pattern.compile(sb.toString());
    }

    public static InetAddress parseIpAddres(String ipstring) throws UnknownHostException{
        byte[] parts = null;
        if(NetUtil.isValidIpV4Address(ipstring) || NetUtil.isValidIpV6Address(ipstring)) {
            parts = NetUtil.createByteArrayFromIpAddressString(ipstring);
            return InetAddress.getByAddress(parts);
        } else {
            return null;
        }
    }

    /**
     * Check if an Throwable is fatal hence should never be catched.
     * Thanks to superbaloo for the tips
     * @param err
     * @return true if the exception is fatal to the JVM and should not be catched in a plugin
     */
    public static boolean isFatal(Throwable err) {
        return (
                        // StackOverflowError is a VirtualMachineError but not critical if found in a plugin
                        ! (err instanceof StackOverflowError) && 
                        // VirtualMachineError includes OutOfMemoryError and other fatal errors
                        (err instanceof VirtualMachineError || err instanceof InterruptedException || err instanceof ThreadDeath) ) 
                        ;
    }

    public static Object putNotEmpty(Map<String, Object>i, String j, Object k){
        if (k != null) {
            return i.put(j, k);
        } else {
            return null;
        }
    }

    public static String getFirstInitClass() {
        StackTraceElement[] elements = new Throwable().getStackTrace();
        String last ="";
        for (int i = 1; i < elements.length ; i++) {
            if (!"<init>".equals(elements[i].getMethodName())) {
                break;
            }
            last = elements[i].getClassName();
        }
        return last;
    }

    private static final MimetypesFileTypeMap mimeTypesMap = new MimetypesFileTypeMap();
    public static String getMimeType(String file) {
        return mimeTypesMap.getContentType(file);
    }

    /**
     * It tries to extract a meaningfull message for any exception
     * @param t
     * @return
     */
    public static String resolveThrowableException(Throwable t) {
        StringBuilder builder = new StringBuilder();
        while(t.getCause() != null) {
            String message = t.getMessage();
            if (message == null) {
                message = t.getClass().getSimpleName();
            }
            builder.append(message).append(": ");
            t = t.getCause();
        }
        String message = t.getMessage();
        if (t instanceof NoSuchMethodException) {
            message = "No such method: " + t.getMessage();
        } else if (t instanceof ArrayIndexOutOfBoundsException) {
            message = "Array out of bounds: " + message;
        } else if (message == null) {
            message = t.getClass().getSimpleName();
        }
        builder.append(message);
        return builder.toString();
    }

    public static URL[] stringsToUrl(String[] destinations, int port, String protocol, Logger logger) {
        // Uses URI parsing to read destination given by the user.
        URL[] endPoints = new URL[destinations.length];
        for (int i = 0 ; i < destinations.length ; i++) {
            String temp = destinations[i];
            if ( !temp.contains("//")) {
                temp = protocol + "://" + temp;
            }
            try {
                URL newEndPoint = new URL(temp);
                int localport = port;
                endPoints[i] = new URL(
                                       (newEndPoint.getProtocol() != null ? newEndPoint.getProtocol() : protocol),
                                       (newEndPoint.getHost() != null ? newEndPoint.getHost() : "localhost"),
                                       (newEndPoint.getPort() > 0 ? newEndPoint.getPort() : localport),
                                       (newEndPoint.getPath() != null ? newEndPoint.getPath() : "")
                                );
            } catch (MalformedURLException e) {
                logger.error("invalid destination {}: {}", destinations[i], e.getMessage());
            }
        }
        return endPoints;
    }

    @SuppressWarnings("rawtypes")
    private static final Iterator EMTPYITERATOR = new Iterator() {
        @Override
        public boolean hasNext() {
            return false;
        }

        @Override
        public Event next() {
            throw new NoSuchElementException();
        }
    };

    @SuppressWarnings("unchecked")
    public static <T> Iterator<T> getEmptyIterator() {
        return (Iterator<T>) EMTPYITERATOR;
    }

    // Implementing Durstenfeld shuffle (see https://en.wikipedia.org/wiki/Fisher–Yates_shuffle#The_modern_algorithm)
    public static <T extends Object> void shuffleArray(T[] ar) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int i = ar.length - 1; i > 0; i--) {
            int index = rnd.nextInt(i + 1);
            T a = ar[index];
            ar[index] = ar[i];
            ar[i] = a;
        }
    }

    public static void waitAllThreads(Stream<Thread> threads) {
        threads.forEach(i -> {
            while ( ! i.isAlive()) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
    }

    private static final Pattern regexContent = Pattern.compile("\\\\(\r|\n){1,2}?");
    public static void cleanPattern(RouteLexer p) {
        String newText = regexContent.matcher(p.getText()).replaceAll("$1");
        p.setText(newText);
    }

}
