package loghub.netty.http;

import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.jar.JarEntry;

import javax.activation.MimetypesFileTypeMap;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.stream.ChunkedInput;
import io.netty.handler.stream.ChunkedStream;

public class ResourceFiles extends HttpRequestProcessing {

    private static final MimetypesFileTypeMap mimeTypesMap = new MimetypesFileTypeMap();
    static {
        mimeTypesMap.addMimeTypes("text/css                                        css");
        mimeTypesMap.addMimeTypes("text/javascript                                 js");
        mimeTypesMap.addMimeTypes("application/json                                json");
        mimeTypesMap.addMimeTypes("text/html                                       html htm");
    }
    private static final Path ROOT = Paths.get("/");
    
    private String internalPath;
    private Date internalDate;
    private URL resourceUrl;

    @Override
    public boolean acceptRequest(HttpRequest request) {
        String name = "static/" + ROOT.relativize(
                Paths.get(request.uri())
                .normalize()
                ).toString();
        resourceUrl = getClass().getClassLoader().getResource(name);
        if (resourceUrl == null) {
            return false;
        } else {
            return true;
        }
    }

    @Override
    protected boolean processRequest(FullHttpRequest request, ChannelHandlerContext ctx) throws HttpRequestFailure {
        if ("jar".equals(resourceUrl.getProtocol())) {
            try {
                JarURLConnection jarConnection = (JarURLConnection)resourceUrl.openConnection();
                JarEntry entry = jarConnection.getJarEntry();
                if (entry.isDirectory()) {
                    throw new HttpRequestFailure(HttpResponseStatus.FORBIDDEN, "Directory listing refused");
                }
                int length = jarConnection.getContentLength();
                internalPath = entry.getName();
                internalDate = new Date(entry.getLastModifiedTime().toMillis());
                ChunkedInput<ByteBuf> content = new ChunkedStream(jarConnection.getInputStream());
                return writeResponse(ctx, request, content, length);
            } catch (IOException e) {
                throw new HttpRequestFailure(HttpResponseStatus.INTERNAL_SERVER_ERROR, e.getMessage());
            }
        } else {
            throw new HttpRequestFailure(HttpResponseStatus.INTERNAL_SERVER_ERROR, request.uri() + " not managed");
        }
    }

    @Override
    protected String getContentType() {
        return mimeTypesMap.getContentType(internalPath);
    }

    @Override
    public Date getContentDate() {
        return internalDate;
    }

}
